const test = require('node:test');
const assert = require('node:assert/strict');
const { load } = require('./load.cjs');

test('series discovery retains HD alternatives when the first variant fails or lacks the requested episode', async () => {
  const queried = [];
  let active = 0, peak = 0;
  const vod = load('lib/xtreamVod.ts', { './iptv': {
    xtreamInfoFromUrl: () => ({ baseUrl: 'https://provider.example', username: 'fixture', password: 'test-only' }),
    playlistProxyHeaders: () => ({ 'User-Agent': 'Fixture' }),
    buildXtreamPlayerApiUrl: (_base, _user, _pass, action) => `https://provider.example/api?action=${action}`,
    fetchXtreamJson: async url => {
      const parsed = new URL(url), action = parsed.searchParams.get('action');
      if (action === 'get_vod_streams') return [];
      if (action === 'get_series') return [
        { series_id: 1, tmdb: 20, name: '4K Fixture' },
        { series_id: 2, tmdb: 20, name: '1080p Fixture' },
        { series_id: 2, tmdb: 20, name: 'Duplicate' },
        { series_id: 3, tmdb: 20, name: 'Unavailable' },
        { series_id: 4, tmdb: 20, name: '720p Fixture' },
        { series_id: 5, tmdb: 99, name: 'Different show' }
      ];
      const id = Number(parsed.searchParams.get('series_id')); queried.push(id); peak = Math.max(peak, ++active);
      await new Promise(setImmediate); active--;
      if (id === 1) return { episodes: { '2': [{ id: 100, episode_num: 1 }] } };
      if (id === 3) throw new Error('provider unavailable');
      return { episodes: { '2': [{ id: id * 100, episode_num: 3, title: 'Episode three', container_extension: 'mp4' }] } };
    }
  } });
  const sources = await vod.findEpisodeVodSource([{ enabled: true, m3uUrl: 'https://provider.example/get.php' }], { id: 20, title: 'Fixture' }, 2, 3);
  assert.deepEqual(Array.from(sources, s => s.quality), ['1080p', '720p']);
  assert.deepEqual(Array.from(sources, s => new URL(s.url).pathname.split('/').pop()), ['200.mp4', '400.mp4']);
  assert.deepEqual(queried.sort(), [1, 2, 3, 4]);
  assert.ok(peak <= 2, 'provider lookups are bounded');
});

test('IPTV movies and episodes retain the catalogue player identity through browser preparation', async () => {
  const requests = [];
  const headersFor = ua => ({ Accept: '*/*', 'User-Agent': ua || 'Default IPTV player', 'Icy-MetaData': '1' });
  const vod = load('lib/xtreamVod.ts', { './iptv': {
    xtreamInfoFromUrl: () => ({ baseUrl: 'http://provider.example', username: 'fixture', password: 'test-only' }),
    playlistProxyHeaders: headersFor,
    buildXtreamPlayerApiUrl: (_base, _user, _pass, action) => `https://provider.example/api?action=${action}`,
    fetchXtreamJson: async (url, headers) => {
      requests.push(headers);
      const action = new URL(url).searchParams.get('action');
      if (action === 'get_vod_streams') return [{ tmdb: 10, name: 'Fixture movie', stream_id: 100, container_extension: 'mp4' }];
      if (action === 'get_series') return [{ tmdb: 20, name: 'Fixture show', series_id: 200 }];
      return { episodes: { '1': [{ id: 201, episode_num: 2, container_extension: 'mp4' }] } };
    }
  } });
  const prepare = load('lib/prepareBrowserStream.ts', {
    './debrid': { cachedDebridDirectUrl: () => null, parseDebridStream: () => null },
    './streamCompatibility': { playbackPlan: () => ({ route: 'here', method: 'direct' }), canTryRemux: () => false,
      streamTransport: () => 'file', streamContainer: () => 'mp4' },
    './homeServerPlayback': {},
    './resolver': load('lib/resolver.ts', { './config': { config: { resolverUrl: 'https://relay.example' } } })
  });
  for (const ua of [undefined, 'Configured player']) {
    const playlists = [{ enabled: true, m3uUrl: 'https://provider.example/get.php' }];
    const movies = await vod.findMovieVodSources(playlists, { id: 10, title: 'Fixture movie' }, ua);
    const episodes = await vod.findEpisodeVodSource(playlists, { id: 20, title: 'Fixture show' }, 1, 2, ua);
    for (const source of [...movies, ...episodes]) {
      assert.equal(source.behaviorHints.proxyHeaders.request['User-Agent'], headersFor(ua)['User-Agent']);
      const result = await prepare.prepareBrowserStream(source, {});
      assert.equal(result.url, source.url, 'IPTV keeps the subscriber-IP native path first');
      assert.equal(result.remux, false);
      assert.deepEqual(result.behaviorHints.proxyHeaders.request, headersFor(ua));
      const repackaged = await prepare.prepareBrowserStream(source, {}, { forceRemux: true });
      const relay = new URL(repackaged.url);
      assert.equal(relay.origin, 'https://relay.example');
      assert.equal(relay.searchParams.get('url'), source.url);
      assert.deepEqual(JSON.parse(atob(relay.searchParams.get('h'))), headersFor(ua));
      assert.equal(repackaged.originalUrl, source.url);
      assert.equal(repackaged.behaviorHints.proxyHeaders.request, undefined);
    }
  }
  assert.ok(requests.length >= 3);
});
