const test = require('node:test');
const assert = require('node:assert/strict');
const { load } = require('./load.cjs');
const policy = load('lib/updatePolicy.ts');
const current = '1790077410740';
const newer = '1790078410740';

function fixture({ remote = newer, player = null, saved = new Map(), query = '', storageBlocked = false } = {}) {
  const document = new EventTarget();
  document.visibilityState = 'visible';
  let selector = '', interval, cleanup, observedPlayer = player;
  document.querySelector = value => { selector = value; return observedPlayer; };
  const navigations = [];
  const storage = { getItem(key) { if (storageBlocked) throw Error('blocked'); return saved.get(key); }, setItem(key, value) { if (storageBlocked) throw Error('blocked'); saved.set(key, value); } };
  const window = {
    location: { href: `https://web.arvio.tv/${query}`, search: query, replace: value => navigations.push(value) },
    history: { state: null, replaceState() {} }, localStorage: storage,
    setInterval(fn) { interval = fn; return 1; }, clearInterval() {}
  };
  const module = load('components/shell/UpdateWatcher.tsx', {
    react: { useEffect: effect => { cleanup = effect(); } }, '@/lib/updatePolicy': policy
  }, { window, document, process: { env: { NEXT_PUBLIC_BUILD_STAMP: current } }, fetch: async () => ({ ok: true, json: async () => ({ v: remote }) }) });
  module.UpdateWatcher();
  return { saved, navigations, selector: () => selector, closePlayer: () => { observedPlayer = null; }, tick: () => interval(), visible: () => document.dispatchEvent(new Event('visibilitychange')), cleanup: () => cleanup() };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('older or invalid build manifests cannot trigger automatic reloads', async () => {
  for (const remote of ['1790003341540', current, 'bad', {}, Number.MAX_SAFE_INTEGER + 1]) {
    const f = fixture({ remote });
    await flush(); f.tick(); f.visible(); await flush();
    assert.equal(f.navigations.length, 0);
    f.cleanup();
  }
});

test('a newer build waits for paused, buffering and embedded trailer players to close', async () => {
  const f = fixture({ player: { paused: true } });
  await flush();
  assert.equal(f.navigations.length, 0);
  assert.match(f.selector(), /video/);
  assert.match(f.selector(), /youtube-nocookie/);
  f.closePlayer(); f.tick(); await flush();
  assert.equal(f.navigations.length, 1);
  assert.equal(new URL(f.navigations[0]).searchParams.get('_v'), newer);
  f.visible(); f.tick(); await flush();
  assert.equal(f.navigations.length, 1);
});

test('the same stale HTML cannot loop after the four-minute cooldown expires', async () => {
  const first = fixture(); await flush();
  assert.equal(first.navigations.length, 1);
  first.saved.set('arvio.web.lastUpdateReload', String(Date.now() - 20 * 60000));
  const repeated = fixture({ saved: first.saved }); await flush(); repeated.tick(); await flush();
  assert.equal(repeated.navigations.length, 0);
});

test('blocked storage still permits one update and the cache-bust URL prevents a loop', async () => {
  const first = fixture({ storageBlocked: true }); await flush();
  assert.equal(first.navigations.length, 1);
  const repeated = fixture({ storageBlocked: true, query: `?_v=${newer}` });
  await flush(); repeated.tick(); await flush();
  assert.equal(repeated.navigations.length, 0);
});

test('version manifest is static and uses the exact browser compilation stamp', async () => {
  const route = load('app/version.json/route.ts', {}, { process: { env: { NEXT_PUBLIC_BUILD_STAMP: current } } });
  assert.equal(route.dynamic, 'force-static');
  const response = route.GET();
  assert.deepEqual(await response.json(), { v: current });
  assert.equal(response.headers.get('cache-control'), 'no-store');
});
