const test = require('node:test');
const assert = require('node:assert/strict');
const { load } = require('./load.cjs');
const info = load('lib/addonStreamInfo.ts');

test('community and donation webpages are never playback source candidates', () => {
  for (const url of ['https://discord.gg/example', 'https://discord.com/invite/example', 'https://www.ko-fi.com/example', 'https://buymeacoffee.com/example', 'https://www.paypal.com/donate?hosted_button_id=example']) {
    assert.equal(info.isInformationalAddonStream({ source: 'HDHub', url }), true, url);
  }
});

test('the observed PenguPlay donation row is informational without excluding other provider URLs', () => {
  assert.equal(info.isInformationalAddonStream({ source: '✨ | support the project!', description: 'PenguPlay - pengu.uk/donate (donating hides this message)', url: 'https://pengu.uk/donate' }), true);
  assert.equal(info.isInformationalAddonStream({ source: 'Support the project', url: 'https://pengu.uk/stream?id=123' }), false);
  assert.equal(info.isInformationalAddonStream({ source: 'Support the project', url: 'https://pengu.uk/donate/movie.mp4' }), false);
});

test('the observed HDHub donation page is informational without excluding the provider media', () => {
  assert.equal(info.isInformationalAddonStream({ source: '🌟 Donation needed.', description: 'HDHub', url: 'https://hdhub.thevolecitor.qzz.io/donation.html' }), true);
  assert.equal(info.isInformationalAddonStream({ source: 'Donation needed.', url: 'https://hdhub.thevolecitor.qzz.io/stream?id=123' }), false);
  assert.equal(info.isInformationalAddonStream({ source: 'Donation needed.', url: 'https://hdhub.thevolecitor.qzz.io/movie.mp4' }), false);
});

test('explicit no-result notices without media or pointing at diagnostic documents are excluded', () => {
  for (const entry of [
    { name: 'No streams found' },
    { source: 'Sports Streams', description: 'Match not found or has ended', url: 'https://example.test/unavailable.html' },
    { source: 'Unavailable', description: 'Match not found or has ended', url: 'https://www.google.com/' },
    { name: '⚠ No streams available.', url: 'about:blank' },
    { source: 'Addon', description: 'No sources found', url: 'https://example.test/error.json' }
  ]) assert.equal(info.isInformationalAddonStream(entry), true);
});

test('real media and torrent results survive informational-looking titles', () => {
  for (const entry of [
    { name: 'Donation', url: 'https://cdn.example.test/movie.mp4' },
    { title: 'No streams found', url: 'https://cdn.example.test/watch?id=123' },
    { source: 'Match not found or has ended', url: 'https://cdn.example.test/live.m3u8' },
    { name: 'No streams found', infoHash: 'a'.repeat(40) },
    { source: 'Discord documentary', url: 'https://cdn.discordapp.com/attachments/123/movie.mp4' },
    { name: 'Watch on provider', externalUrl: 'https://example.test/watch' },
    { name: 'Google homepage', externalUrl: 'https://www.google.com/' },
    { name: 'No streams found', url: 'https://video.google.com/videoplayback?id=123' },
    { name: 'Ko-fi feature', url: 'https://ko-fi.com/media.mp4' }
  ]) assert.equal(info.isInformationalAddonStream(entry), false, JSON.stringify(entry));
});

function addonClient({ resolver = false, raw = [], remote = [] } = {}) {
  return load('lib/addons.ts', {
    './http': { proxiedUrl: url => url, jsonRequest: async url => url.includes('/subtitles/') ? { subtitles: [] } : { streams: raw } },
    './config': { hasResolverConfig: () => resolver },
    './resolver': { getResolverStreamsProgressive: async (_addons, _item, _season, _episode, update) => { update?.(remote, remote); return remote; } },
    './storage': { loadStored: (_key, fallback) => fallback, saveStored() {} },
    './streamCompatibility': { isBrowserPlayableStream: stream => !!stream.url, isIosPlayableStream: stream => !!stream.url },
    './addonStreamInfo': info
  });
}
const addon = { id: 'fixture', name: 'Fixture', manifestUrl: 'https://addon.example.test/manifest.json', catalogs: [], resources: ['stream'], types: ['movie'] };
const item = { id: 1, mediaType: 'movie', title: 'Fixture title' };

test('browser addon parser excludes notices before publishing progressive sources or auto-select candidates', async () => {
  const client = addonClient({ raw: [
    { name: 'Support us', externalUrl: 'https://ko-fi.com/fixture' },
    { name: 'Join Discord', externalUrl: 'https://discord.gg/fixture' },
    { name: 'No streams found' },
    { name: 'Unavailable', title: 'Match not found or has ended', url: 'https://www.google.com/' },
    { name: 'Movie', url: 'https://cdn.example.test/play?id=1' }
  ] });
  const batches = [];
  const sources = await client.getStreamsProgressive([addon], item, undefined, undefined, (streams, batch) => batches.push(...streams, ...batch));
  assert.equal(sources.length, 1);
  assert.equal(sources[0].source, 'Movie');
  assert.ok(batches.length > 0);
  assert.ok(batches.every(stream => stream.source === 'Movie'));
});

test('resolver notices are filtered from every progressive batch and final merged sources', async () => {
  const remote = [
    { source: 'Donation', addonName: 'Fixture', url: 'https://ko-fi.com/fixture' },
    { source: 'Unavailable', addonName: 'Sports Streams', description: 'Match not found or has ended', url: 'https://www.google.com/' },
    { source: 'Movie', addonName: 'Fixture', url: 'https://cdn.example.test/movie.mp4' }
  ];
  const client = addonClient({ resolver: true, remote });
  const batches = [];
  const sources = await client.getStreamsProgressive([], item, undefined, undefined, (streams, batch) => batches.push(...streams, ...batch));
  assert.equal(sources.length, 1);
  assert.equal(sources[0].source, 'Movie');
  assert.ok(batches.length > 0);
  assert.ok(batches.every(stream => stream.source === 'Movie'));
});
