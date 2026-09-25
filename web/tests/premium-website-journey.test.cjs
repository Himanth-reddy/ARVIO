const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../../netlify-arvio-tv-site/assets/premium-journey.js'), 'utf8');
const id = '38b5a038-5e30-4325-81ad-a2e6d000de18';
function run({ url = 'https://arvio.tv/premium/?lang=es-ES', referrer = '', navigator = {}, fetchFails = false } = {}) {
  const links = ['/premium/?lang=es-ES', 'https://web.arvio.tv/?intent=trial', 'https://ko-fi.com/arvio/tiers', 'https://github.com/ProdigyV21/ARVIO'].map(href => ({
    href: new URL(href, url).href, dataset: {}, closest(selector) { return selector === 'a[href]' ? this : null; }
  }));
  const events = [], listeners = {};
  const window = { location: { href: url }, crypto: { randomUUID: () => id } };
  for (const name of ['localStorage', 'sessionStorage']) Object.defineProperty(window, name, { get() { throw new Error('Storage must not be touched'); } });
  vm.runInNewContext(code, { URL, Set, navigator, window, document: {
    referrer, querySelectorAll: () => links, addEventListener: (name, handler) => { listeners[name] = handler; }
  }, fetch: (_url, options) => { events.push(JSON.parse(options.body)); return fetchFails ? Promise.reject(new Error('offline')) : Promise.resolve({ ok: true }); } });
  return { links, events, listeners };
}
test('Premium landing preserves language and carries journey only to first-party destinations', () => {
  const result = run();
  assert.equal(result.events[0].event_name, 'premium_page_view');
  assert.equal(new URL(result.links[0].href).searchParams.get('lang'), 'es-ES');
  assert.equal(new URL(result.links[1].href).searchParams.get('arvio_journey'), id);
  assert.equal(new URL(result.links[1].href).searchParams.get('intent'), 'trial');
  assert.equal(result.links[2].href, 'https://ko-fi.com/arvio/tiers');
  assert.equal(result.links[3].href, 'https://github.com/ProdigyV21/ARVIO');
});
test('Homepage visits are not counted as Premium landing visits; outbound clicks are deduplicated', () => {
  const result = run({ url: 'https://arvio.tv/' });
  assert.equal(result.events.length, 0);
  result.listeners.click({ target: result.links[2] });
  result.listeners.auxclick({ type: 'auxclick', target: result.links[2], button: 1 });
  result.listeners.auxclick({ type: 'auxclick', target: result.links[1], button: 2 });
  assert.equal(result.events.length, 1);
  assert.equal(result.events[0].event_name, 'membership_clicked');
});
test('Malicious tags and full referrer paths are not collected', () => {
  const result = run({ url: 'https://arvio.tv/premium/?utm_source=user%40example.com&arvio_journey=not-valid', referrer: 'https://example.org/private?token=secret' });
  assert.equal(result.events[0].metadata.source, 'example.org');
  assert.equal(result.events[0].journey_id, id);
  assert.ok(!JSON.stringify(result.events).includes('secret'));
});
test('DNT and GPC disable measurement and decoration', () => {
  for (const navigator of [{ doNotTrack: '1' }, { globalPrivacyControl: true }]) {
    const result = run({ navigator });
    assert.equal(result.events.length, 0);
    assert.equal(result.links[1].href, 'https://web.arvio.tv/?intent=trial');
  }
});
test('Blocked analytics never prevent membership navigation', async () => {
  const result = run({ fetchFails: true });
  result.listeners.click({ target: result.links[2], preventDefault() { throw new Error('Navigation must remain immediate'); } });
  await Promise.resolve();
  assert.equal(result.links[2].href, 'https://ko-fi.com/arvio/tiers');
});
