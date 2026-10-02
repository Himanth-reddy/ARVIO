const test = require('node:test');
const assert = require('node:assert/strict');
const { load, storage } = require('./load.cjs');

function fixture(respond) {
  const calls = [];
  const api = load('lib/tmdb.ts', {
    './config': { config: {} }, './storage': storage(),
    './metadata/anizip': {}, './metadata/dispatcher': {},
    './mediaImages': { tmdbImageUrl: () => '' },
    './http': { proxiedUrl: x => x, apiProxiedUrl: x => x, jsonRequest: async url => {
      calls.push(url);
      return respond(new URL(url));
    } }
  }, { window: { location: { origin: 'https://web.invalid' } } });
  return { api, calls };
}
const catalog = ref => ({ id: 'tmdb-test', name: 'TMDB', sourceType: 'tmdb', sourceRef: ref, enabled: true });

test('Android synced company and network catalogs load scoped TMDB sources', async () => {
  const { api, calls } = fixture(url => ({ results: [{ id: url.pathname.endsWith('/tv') ? 501 : 1, title: 'Title' }], total_pages: 1 }));
  const company = await api.loadCatalog(catalog('tmdb:company:3:'), 'en', []);
  assert.deepEqual(Array.from(company.items, x => x.mediaType), ['movie', 'tv']);
  assert.ok(calls.every(url => url.includes('with_companies=3')));
  calls.length = 0;
  const network = await api.loadCatalog(catalog('tmdb:network:49:'), 'en', []);
  assert.equal(network.items[0].mediaType, 'tv');
  assert.ok(calls.every(url => url.includes('/discover/tv') && url.includes('with_networks=49')));
});

test('person catalogs use combined credits and respect TV scope', async () => {
  const { api, calls } = fixture(() => ({ cast: [
    { id: 1, title: 'Film', media_type: 'movie' }, { id: 501, name: 'Show', media_type: 'tv' }
  ] }));
  const row = await api.loadCatalog(catalog('tmdb:person:287:tv'), 'en', []);
  assert.deepEqual(Array.from(row.items, x => x.id), [501]);
  assert.ok(calls[0].includes('/person/287/combined_credits'));
});

test('scoped lists page past movies and URL fallback survives restore', async () => {
  const { api, calls } = fixture(url => ({ total_pages: 2, items: url.searchParams.get('page') === '1'
    ? [{ id: 1, title: 'Film', media_type: 'movie' }] : [{ id: 501, name: 'Show', media_type: 'tv' }] }));
  const row = await api.loadCatalog({ ...catalog(null), sourceUrl: 'https://www.themoviedb.org/en-US/list/99/tv' }, 'en', []);
  assert.deepEqual(Array.from(row.items, x => x.id), [501]);
  assert.equal(calls.length, 2);
});

test('collection follows release order and invalid IDs do not request anything', async () => {
  const { api, calls } = fixture(() => ({ parts: [
    { id: 2, title: 'Second', release_date: '2002-01-01' }, { id: 1, title: 'First', release_date: '2001-01-01' }
  ] }));
  const row = await api.loadCatalog(catalog('tmdb:collection:1241:'), 'en', []);
  assert.deepEqual(Array.from(row.items, x => x.id), [1, 2]);
  calls.length = 0;
  assert.equal((await api.loadCatalog(catalog('tmdb:list:0:'), 'en', [])).items.length, 0);
  assert.equal(calls.length, 0);
});
