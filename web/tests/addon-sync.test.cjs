const test = require('node:test');
const assert = require('node:assert/strict');
const { load, storage } = require('./load.cjs');
const addon = id => ({ id, manifestUrl: `https://${id}.example/manifest.json` });
const auth = { session: { userId: 'account', accessToken: 'test' }, isNetlifySession: true, accessToken: async () => 'test' };

async function save(root, list, options = {}) {
  let pushed;
  const cloud = load('lib/cloud.ts', {
    './config': { config: { netlifyBackendUrl: 'https://backend.invalid' }, hasNetlifyBackendUrl: () => true },
    './homeserver': {}, './iptv': {}, './mediaImages': {},
    './http': { jsonRequest: async (_url, request) => {
      if (request?.method === 'POST') { pushed = JSON.parse(request.body).payload; return { accepted: true }; }
      return { payload: root };
    } }
  });
  await cloud.saveCloudAddons(auth, list.map(addon), 'p1', options);
  return pushed;
}

test('stale web toggle preserves remote additions and carries no removal records', async () => {
  const pushed = await save({ addons: ['opensubtitles','a','b'].map(addon) }, ['opensubtitles']);
  assert.deepEqual(pushed.addons.map(a => a.id), ['opensubtitles','a','b']);
  assert.deepEqual(pushed.addonChanges, {});
});

test('removal applies to root and every profile without resurrecting deleted entries', async () => {
  const list = ['opensubtitles','a','b'].map(addon);
  const pushed = await save({ addons: list, addonsByProfile: { p1: list, p2: list } }, ['opensubtitles'],
    { changes: { a: { updatedAt: 200, removed: true }, b: { updatedAt: 200, removed: true } } });
  assert.deepEqual(pushed.addons.map(a => a.id), ['opensubtitles']);
  for (const list of Object.values(pushed.addonsByProfile)) assert.deepEqual(list.map(a => a.id), ['opensubtitles']);
});

test('replaying old removal after reinstall does not delete it again', async () => {
  const pushed = await save({ addons: ['opensubtitles','a'].map(addon), addonChanges: { a: {updatedAt:300,removed:false} } },
    ['opensubtitles'], { changes: { a: { updatedAt:200,removed:true } } });
  assert.deepEqual(pushed.addons.map(a => a.id), ['opensubtitles','a']);
  assert.equal(pushed.addonChanges.a.updatedAt, 300);
});

test('stale list cannot resurrect a tombstone but explicit reinstall can', async () => {
  const root = { addons: [addon('opensubtitles')], addonChanges: { a: {updatedAt:200,removed:true} } };
  const stale = await save(root, ['opensubtitles','a']);
  assert.deepEqual(stale.addons.map(a => a.id), ['opensubtitles']);
  const installed = await save(root, ['opensubtitles','a'], {changes:{a:{updatedAt:300,removed:false}}});
  assert.deepEqual(installed.addons.map(a => a.id), ['opensubtitles','a']);
});

test('outbox retries retain operation timestamps and reinstall replaces removal', async () => {
  const disk = storage(); const sent = []; let fail = true;
  const api = load('lib/addonOutbox.ts', { './storage': disk, './cloud': {saveCloudAddons: async (...args) => {
    sent.push(JSON.parse(JSON.stringify(args[3]))); if (fail) throw Error('offline');
  }} });
  api.queueAddons(auth, [], 'p1', ['a']);
  await assert.rejects(api.flushAddonOutbox(auth), /offline/);
  fail = false; await api.flushAddonOutbox(auth);
  assert.deepEqual(sent[0].changes, sent[1].changes);
  api.queueAddons(auth, [], 'p1', ['a']);
  api.queueAddons(auth, [addon('a')], 'p1', [], ['a']);
  await api.flushAddonOutbox(auth);
  assert.equal(sent.at(-1).changes.a.removed, false);
  assert.deepEqual(sent.at(-1).removedIds, []);
});

test('legacy outbox removal is migrated once before a failed upload', async () => {
  const disk = storage(); const sent = [];
  disk.saveStored('arvio.web.addonOutbox.v1:account', [{id:'legacy',profileId:'p1',addons:[],removedIds:['a']}]);
  const api = load('lib/addonOutbox.ts', { './storage': disk, './cloud': {saveCloudAddons: async (...args) => {
    sent.push(JSON.parse(JSON.stringify(args[3]))); throw Error('offline');
  }} });
  await assert.rejects(api.flushAddonOutbox(auth), /offline/);
  await assert.rejects(api.flushAddonOutbox(auth), /offline/);
  assert.equal(sent[0].changes.a.removed, true);
  assert.deepEqual(sent[0].changes, sent[1].changes);
});
