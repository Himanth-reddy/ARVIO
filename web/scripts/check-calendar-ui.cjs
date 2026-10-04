/* Production Calendar and loader, deterministic local provider/metadata adapters. No account access. */
const path = require('node:path');
const fs = require('node:fs');
const http = require('node:http');
const assert = require('node:assert/strict');
const esbuild = require('esbuild');
const { chromium } = require('@playwright/test');
(async () => {
  const root = path.resolve(__dirname, '..');
  const out = path.join(root, '.calendar-ui-test'); fs.mkdirSync(out, { recursive: true });
  const stub = path.join(root, 'tests/calendar-ui/stubs.ts');
  await esbuild.build({ entryPoints: [path.join(root, 'tests/library-ui/entry.tsx')], bundle: true, outfile: path.join(out, 'app.js'), jsx: 'automatic', nodePaths: (process.env.NODE_PATH || '').split(path.delimiter).filter(Boolean), define: { 'process.env.NODE_ENV': '"test"', 'process.env': '{}' }, plugins: [{ name: 'offline-calendar-adapters', setup(build) {
    build.onResolve({ filter: /^@\/lib\/(store|tmdb|imdbRatings|homeserver|cloud|watchlistOutbox|simkl|mdblist|mappers)$/ }, () => ({ path: stub }));
    build.onResolve({ filter: /^\.\/tmdb$/ }, args => args.importer.endsWith('calendarLoader.ts') ? { path: stub } : undefined);
    build.onResolve({ filter: /^@\// }, args => ({ path: ['.tsx', '.ts', '.js', '/index.tsx', '/index.ts', ''].map(ext => path.join(root, args.path.slice(2) + ext)).find(file => fs.existsSync(file)) }));
  } }] });
  const fixture = path.resolve(root, '../app/src/androidTest/assets/library');
  const server = http.createServer((req, res) => {
    const file = req.url.split('?')[0];
    if (file === '/') { res.setHeader('content-type', 'text/html'); res.end('<html><head><meta name="viewport" content="width=device-width,initial-scale=1"/><link rel="stylesheet" href="/globals.css"/><link rel="stylesheet" href="/calendar.css"/><style>.library-test-header{z-index:100;height:74px;position:absolute;top:0;left:0;right:0;display:flex;align-items:center;justify-content:space-between;padding:0 3vw;background:#000;color:#fff}.library-test-header nav,.library-test-header span{display:flex;gap:12px;align-items:center}.library-test-header nav{gap:32px}.library-test-header .active{background:#242426;border-radius:28px;padding:14px 24px}.library-test-avatar{background:#242426;border-radius:50%;padding:12px}.library-test-header svg{width:22px}@media(max-width:650px){.library-test-header{display:none}}</style></head><body><div id="root"></div><script src="/app.js"></script></body></html>'); return; }
    const location = file === '/app.js' ? path.join(out, 'app.js') : file === '/globals.css' || file === '/calendar.css' ? path.join(root, 'app', file) : file.startsWith('/fixtures/') ? path.join(fixture, path.basename(file)) : path.join(root, 'public', file);
    if (!fs.existsSync(location)) { res.statusCode = 404; res.end(); return; }
    res.setHeader('content-type', file.endsWith('.css') ? 'text/css' : file.endsWith('.js') ? 'application/javascript' : file.endsWith('.png') ? 'image/png' : file.endsWith('.jpg') ? 'image/jpeg' : file.endsWith('.svg') ? 'image/svg+xml' : 'application/octet-stream'); fs.createReadStream(location).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const url = `http://127.0.0.1:${server.address().port}`;
  const evidence = path.resolve(root, '../artifacts/calendar'); fs.mkdirSync(evidence, { recursive: true });
  try {
    for (const [name, width, height] of [['web-tv', 1672, 940], ['web-tablet', 1024, 768], ['web-phone', 390, 844]]) {
      const page = await browser.newPage({ viewport: { width, height }, timezoneId: 'Europe/Amsterdam' });
      const errors = []; page.on('pageerror', error => { errors.push(error.message); console.error('Fixture page error:', error.message); });
      await page.route('**/*', route => route.request().url().startsWith(url) ? route.continue() : route.abort());
      await page.clock.setFixedTime(new Date('2026-10-16T12:00:00Z'));
      await page.goto(url); await page.getByRole('button', { name: 'Calendar', exact: true }).click();
      await page.locator('.calendar-month-grid[aria-busy=false]').waitFor();
      await page.locator('.calendar-release-logo').first().waitFor();
      assert.equal(await page.locator('[role=row]').count(), 5);
      assert.equal(await page.locator('[role=gridcell]').count(), 35);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'No page overflow');
      assert.equal(await page.locator('.calendar-timezone').textContent(), 'Local time · Amsterdam');
      assert.ok((await page.locator('.calendar-release-row').textContent()).includes('Time TBA'));
      assert.ok((await page.locator('.calendar-release-row').textContent()).includes('21:00'));
      await page.screenshot({ path: path.join(evidence, `${name}.png`) });
      const selected = page.locator('[data-calendar-date="2026-10-16"]'); await selected.focus();
      await page.keyboard.press('ArrowRight');
      await page.waitForFunction(() => document.activeElement?.getAttribute('data-calendar-date') === '2026-10-17');
      await page.keyboard.press('ArrowLeft'); await page.keyboard.press('Enter');
      await page.waitForFunction(() => document.activeElement?.classList.contains('calendar-release-card'));
      await page.keyboard.press('Escape');
      await page.waitForFunction(() => document.activeElement?.getAttribute('data-calendar-date') === '2026-10-16');
      await page.locator('.calendar-release-card').first().click();
      assert.ok(await page.evaluate(() => Boolean(window.openedLibraryItem?.id)), 'Release opens real media details');
      await page.getByLabel('Calendar watchlist source').selectOption('arvio');
      assert.equal(await page.locator('.calendar-release-card').count(), 3);
      await page.getByRole('button', { name: 'Next month', exact: true }).click();
      assert.match(await page.locator('.calendar-month-controls h1').textContent(), /November 2026/);
      await page.getByRole('button', { name: 'Today', exact: true }).click();
      assert.match(await page.locator('.calendar-selected-heading h2').textContent(), /October 16|16 October/);
      await page.goto(url + '/?arvio=1'); await page.getByRole('button', { name: 'Calendar', exact: true }).click();
      await page.locator('.calendar-month-grid[aria-busy=false]').waitFor();
      assert.equal(await page.getByLabel('Calendar watchlist source').locator('option').count(), 2);
      assert.equal(await page.locator('.calendar-release-card').count(), 3, 'ARVIO-only source loads cloud releases');
      await page.screenshot({ path: path.join(evidence, `${name}-arvio-only.png`) });
      assert.deepEqual(errors, []); await page.close(); console.log(`${name}: passed`);
    }
    const page = await browser.newPage(); await page.clock.setFixedTime(new Date('2026-10-16T12:00:00Z'));
    await page.goto(url + '/?partial=1'); await page.getByRole('button', { name: 'Calendar', exact: true }).click();
    await page.locator('.calendar-partial').waitFor(); assert.match(await page.locator('.calendar-partial').textContent(), /MDBList/);
    assert.ok(await page.locator('.calendar-release-card').count(), 'One unavailable source does not erase other releases');
    await page.goto(url + '/?arvio=1&empty=1'); await page.getByRole('button', { name: 'Calendar', exact: true }).click();
    await page.locator('.calendar-month-grid[aria-busy=false]').waitFor(); assert.match(await page.locator('.calendar-empty').textContent(), /Add movies and series/);
    await page.close(); console.log('partial and empty states: passed');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
