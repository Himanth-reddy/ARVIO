const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../../netlify-arvio-tv-site');
const output = path.resolve(__dirname, '../../artifacts/collections-guide');
const types = { '.html': 'text/html', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.woff2': 'font/woff2', '.js': 'text/javascript' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const file = path.resolve(root, '.' + decodeURIComponent(url.pathname), url.pathname.endsWith('/') ? 'index.html' : '');
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404).end(); return;
  }
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  fs.mkdirSync(output, { recursive: true });
  let browser;
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) });
    for (const [name, width, height] of [['desktop', 1440, 1000], ['tablet', 768, 1024], ['mobile', 390, 844], ['small-mobile', 320, 740]]) {
      const page = await browser.newPage({ viewport: { width, height } });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/guides/');
      await page.getByRole('link', { name: /Create collections and add catalogs/ }).click();
      await page.locator('h1').waitFor();
      await page.evaluate(() => document.fonts.ready);
      assert.equal(await page.title(), 'Create and Add Collections and Catalogs - ARVIO Guide');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${name}: page overflow`);
      assert.equal(await page.locator('img').evaluateAll(imgs => imgs.every(img => img.complete && img.naturalWidth > 0)), true);
      await page.screenshot({ path: path.join(output, `${name}.png`) });
      await page.getByRole('link', { name: 'Get a public URL', exact: true }).click();
      assert.match(page.url(), /#host$/);
      await page.locator('#host').scrollIntoViewIfNeeded();
      await page.screenshot({ path: path.join(output, `${name}-hosting.png`) });
      await page.getByText('Can I select a JSON file from my phone or computer?', { exact: true }).click();
      assert.equal(await page.locator('details[open]').count(), 1);
      const download = page.waitForEvent('download');
      await page.getByRole('link', { name: 'Download the starter JSON' }).click();
      assert.equal((await download).suggestedFilename(), 'collections.json');
      assert.deepEqual(errors, []);
      console.log(`${name}: navigation, JSON download, FAQ, assets and overflow passed`);
      await page.close();
    }
  } finally {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
