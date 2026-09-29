const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');
const ts = require('typescript');

const site = path.resolve(__dirname, '../../netlify-arvio-tv-site');
const html = fs.readFileSync(path.join(site, 'collections-catalogs/index.html'), 'utf8');
const sandbox = { exports: {}, crypto: webcrypto, TextEncoder, URL };
vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.resolve(__dirname, '../lib/customCollections.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText, sandbox);

test('downloadable guide starter imports as one rail and two populated source folders', async () => {
  const json = fs.readFileSync(path.join(site, 'collections-catalogs/collections.json'), 'utf8');
  const imported = await sandbox.exports.parseCustomCollections(json, 'https://arvio.tv/collections-catalogs/collections.json');
  assert.equal(imported.length, 3);
  assert.equal(imported[0].kind, 'COLLECTION_RAIL');
  assert.equal(imported[1].collectionSources[0].mediaType, 'movie');
  assert.equal(imported[2].collectionSources[0].mediaType, 'tv');
  assert.ok(imported.slice(1).every(c => c.collectionSources[0].discoverParams['vote_count.gte'] === '100'));
});

test('visible JSON example imports with the real collection parser', async () => {
  const json = html.match(/<code id="collection-example">([\s\S]*?)<\/code>/)[1];
  const imported = await sandbox.exports.parseCustomCollections(json);
  assert.equal(imported.length, 2);
  assert.equal(imported[1].collectionSources[0].sortBy, 'popularity.desc');
});

test('guide is discoverable and its metadata, anchors and local assets resolve', () => {
  assert.match(fs.readFileSync(path.join(site, 'guides/index.html'), 'utf8'), /href="\/collections-catalogs\/"/);
  assert.match(fs.readFileSync(path.join(site, 'sitemap.xml'), 'utf8'), /<loc>https:\/\/arvio.tv\/collections-catalogs\/<\/loc>/);
  assert.match(html, /Uploading a file directly into ARVIO is not yet supported/);
  for (const [, json] of html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) JSON.parse(json);
  for (const [, id] of html.matchAll(/href="#([^"]+)"/g)) assert.ok(html.includes(`id="${id}"`), id);
  for (const [, url] of html.matchAll(/(?:href|src)="(\/[^"#]*)"/g)) {
    if (url === '/privacy') continue;
    const file = path.join(site, url, url.endsWith('/') ? 'index.html' : '');
    assert.ok(fs.existsSync(file), `Missing local link: ${url}`);
  }
});
