const test = require('node:test');
const assert = require('node:assert/strict');
const { load, storage } = require('./load.cjs');
const presentation = load('lib/collectionPresentation.ts');
test('built-in and custom groups retain folders without duplicates', () => {
  const rail = { id: 'rail', kind: 'COLLECTION_RAIL', collectionGroup: 'SERVICE', enabled: true };
  const folder = { id: 'netflix', kind: 'COLLECTION', collectionGroup: 'SERVICE', enabled: true };
  const custom = { ...rail, id: 'custom', collectionRailKey: 'my-pack' };
  const all = [rail, folder, custom, { ...folder, id: 'mine', collectionRailKey: 'my-pack' }, { ...folder, id: 'hidden', enabled: false }];
  assert.equal(presentation.collectionHomeCatalogs(all).map(c => c.id).join(), 'rail,custom');
  assert.equal(presentation.collectionFolders(rail, all).map(c => c.id).join(), 'netflix');
  assert.equal(presentation.collectionFolders(custom, all).map(c => c.id).join(), 'mine');
});
test('movie and series tabs understand Android source types and curated lists', () => {
  assert.equal(presentation.collectionMediaTypes({ collectionSources: [{ kind: 'TMDB_GENRE', mediaType: 'series' }] }).join(), 'tv');
  assert.equal(presentation.collectionMediaTypes({ collectionSources: [{ kind: 'TMDB_COLLECTION' }] }).join(), 'movie');
  assert.equal(presentation.collectionMediaTypes({ collectionSources: [{ kind: 'CURATED_IDS', curatedRefs: ['movie:1', 'series:2'] }] }).join(), 'movie,tv');
});
test('first paint requests one matching page and retries without skipping', async () => {
  const calls = []; let fail = true;
  const tmdb = load('lib/tmdb.ts', {
    './config': { config: {} }, './storage': storage(), './metadata/anizip': {}, './metadata/dispatcher': {}, './mediaImages': { tmdbImageUrl: () => '' },
    './http': { proxiedUrl: x => x, apiProxiedUrl: x => x, jsonRequest: async raw => {
      const url = new URL(raw, 'https://web.arvio.tv'); calls.push(url);
      if (url.searchParams.get('page') === '2' && fail) throw new Error('offline');
      return { results: Array.from({ length: 20 }, (_, i) => ({ id: i + 1 + (Number(url.searchParams.get('page')) - 1) * 20, name: 'Show', media_type: 'tv' })), total_pages: 99 };
    } }
  }, { window: { location: { origin: 'https://web.arvio.tv' } } });
  const { createCollectionLoader } = load('lib/collectionLoader.ts', { './collectionPresentation': presentation, './tmdb': tmdb });
  const next = createCollectionLoader({ collectionSources: [{ kind: 'TMDB_DISCOVER', mediaType: 'movie' }, { kind: 'TMDB_DISCOVER', mediaType: 'series', discoverParams: { with_origin_country: 'JP' } }] }, 'tv', 'en-US', [], []);
  assert.equal((await next()).items.length, 20); assert.equal(calls.length, 1);
  assert.ok(calls[0].pathname.endsWith('/discover/tv'));
  assert.equal(calls[0].searchParams.get('with_origin_country'), 'JP');
  const failed = await next(); assert.equal(failed.items.length, 0); assert.equal(failed.errors.length, 1);
  fail = false;
  const retry = await next(); assert.equal(retry.items[0].id, 21); assert.equal(retry.errors.length, 0);
});
test('partial failure preserves successful results and retries only failed source', async () => {
  let fail = true;
  const { createCollectionLoader } = load('lib/collectionLoader.ts', { './collectionPresentation': presentation, './tmdb': {
    loadCollectionSource: async source => { if (source.tmdbListId === 2 && fail) throw new Error('failed'); return [{ id: source.tmdbListId, mediaType: 'movie', title: 'Film' }]; }
  } });
  const next = createCollectionLoader({ collectionSources: [1, 2].map(tmdbListId => ({ kind: 'TMDB_LIST', tmdbListId })) }, 'movie', 'en', [], []);
  assert.equal((await next()).items[0].id, 1); fail = false;
  const retry = await next(); assert.equal(retry.items.length, 1); assert.equal(retry.items[0].id, 2); assert.equal(retry.hasMore, false);
});
