const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const { load, storage } = require('./load.cjs');

function harness() {
  const disk = storage();
  let remote = { accentColor: '#ffffff', profileSettingsById: { p1: { contentLanguage: 'en-US' } } };
  let reject = false;
  let pause;
  const cloud = load('lib/cloud.ts', {
    './config': { config: { netlifyBackendUrl: 'https://backend.invalid' }, hasNetlifyBackendUrl: () => true },
    './homeserver': { serializeHomeServerConnectionJson: () => '[]' },
    './iptv': load('lib/iptv.ts', { './http': {}, './storage': disk }), './mediaImages': {},
    './http': { jsonRequest: async (_url, options) => {
      if (options?.method === 'POST') {
        if (pause) { const wait = pause; pause = null; await wait; }
        if (reject) return { accepted: false };
        remote = JSON.parse(options.body).payload;
        return { accepted: true };
      }
      return { payload: structuredClone(remote) };
    } },
  });
  const auth = { session: { userId: 'account', accessToken: 'test' }, isNetlifySession: true, accessToken: async () => 'test' };
  const createOutbox = () => load('lib/settingsOutbox.ts', { './storage': disk, './cloud': cloud });
  const outbox = createOutbox();
  const initial = { language: 'en-US', accentColor: '#ffffff', iptvPlaylists: [], homeServers: [], catalogs: [],
    hiddenCatalogIds: [], hiddenHomeServerCatalogIds: [], favoriteChannelIds: [], favoriteGroupIds: [], hiddenGroupIds: [], groupOrder: [],
    subtitleStyle: 'outline', subtitleSize: 100, subtitleOffset: 0, subtitleColorName: 'white', frameRateMatchingMode: 'off', qualityFilters: [] };
  const settingsRef = { current: initial };
  let state = initial;
  let sync;
  const source = fs.readFileSync(path.join(__dirname, '../lib/store.tsx'), 'utf8');
  const start = source.indexOf('  const updateSettings = useCallback(');
  const end = source.indexOf('  const isWatched =', start);
  const code = ts.transpileModule(source.slice(start,end) + '\nresult = updateSettings;', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
  const context = { result: null, useCallback: fn => fn, settingsRef, activeProfileIdRef: {current:'p1'}, authClient: auth,
    queueSettings: outbox.queueSettings, saveStored: disk.saveStored, settingsKey:'settings',
    setSettings: next => { state = typeof next === 'function' ? next(state) : next; },
    setSettingsSyncState: next => {sync = next;}, setToast: () => {} };
  vm.runInNewContext(code, context);
  return { auth, initial, outbox, createOutbox, cloud, disk, edit: context.result,
    remote: () => remote, state: () => state, sync: () => sync,
    reject: value => { reject = value; }, pause: promise => {pause = promise;} };
}

test('an edit before initial cloud hydration is durable immediately and survives reload', async () => {
  const h = harness();
  h.edit({language:'nl-NL'});
  assert.equal(h.outbox.hasPendingSettings(h.auth,'p1'),true);
  assert.equal(h.sync(),'pending');
  assert.equal(h.disk.loadStored('settings',{}).language,'nl-NL');
  // Reload: a fresh outbox instance must replay the persisted edit.
  await h.createOutbox().flushSettingsOutbox(h.auth);
  const restored = await h.cloud.pullCloudPayload(h.auth,'p1');
  assert.equal(restored.settings.language,'nl-NL');
  assert.equal(restored.settings.accentColor,'#ffffff');
});

test('back-to-back edits preserve both changes before React effects run', async () => {
  const h = harness();
  h.edit({language:'nl-NL'});
  h.edit({accentColor:'#ff0000'});
  await h.outbox.flushSettingsOutbox(h.auth);
  const restored = await h.cloud.pullCloudPayload(h.auth,'p1');
  assert.equal(restored.settings.language,'nl-NL');
  assert.equal(restored.settings.accentColor,'#ff0000');
});

test('reverting a setting while its earlier save is in flight persists the latest choice', async () => {
  const h = harness();
  let release;
  h.pause(new Promise(resolve => {release = resolve;}));
  h.edit({accentColor:'#ff0000'});
  const saving = h.outbox.flushSettingsOutbox(h.auth);
  await new Promise(setImmediate);
  h.edit({accentColor:'#ffffff'});
  release();
  await saving;
  assert.equal(h.remote().accentColor,'#ffffff');
  assert.equal(h.outbox.hasPendingSettings(h.auth),false);
});

test('rejected saves remain queued across refresh and succeed on retry', async () => {
  const h = harness();
  h.reject(true);
  h.edit({language:'es-ES'});
  await assert.rejects(h.outbox.flushSettingsOutbox(h.auth),/did not accept/);
  assert.equal(h.outbox.hasPendingSettings(h.auth),true);
  h.reject(false);
  await h.createOutbox().flushSettingsOutbox(h.auth);
  assert.equal((await h.cloud.pullCloudPayload(h.auth,'p1')).settings.language,'es-ES');
});

test('explicit pre-hydration edits do not overwrite unrelated settings on another device', async () => {
  const h = harness();
  h.remote().accentColor='#00ff00';
  h.edit({language:'fr-FR'});
  await h.outbox.flushSettingsOutbox(h.auth);
  assert.equal(h.remote().accentColor,'#00ff00');
  assert.equal(h.remote().profileSettingsById.p1.contentLanguage,'fr-FR');
});

test('a late hydration response preserves the edit while adopting unrelated cloud settings', async () => {
  const h = harness();
  h.edit({language:'nl-NL'});
  const remote = {...h.initial, accentColor:'#00ff00'};
  const merged = h.outbox.settingsWithPendingEdits(h.auth,'p1',remote,h.initial,h.state());
  assert.equal(merged.language,'nl-NL');
  assert.equal(merged.accentColor,'#00ff00');
  // The autosave effect sees the merged state, not the old local defaults.
  h.outbox.queueSettings(h.auth,'p1',merged,remote);
  // A second remote edit after the pull must also survive the queued save.
  h.remote().accentColor='#0000ff';
  await h.outbox.flushSettingsOutbox(h.auth);
  assert.equal(h.remote().accentColor,'#0000ff');
  assert.equal(h.remote().profileSettingsById.p1.contentLanguage,'nl-NL');
});
