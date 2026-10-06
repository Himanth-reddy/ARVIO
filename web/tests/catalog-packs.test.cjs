const test = require('node:test');
const assert = require('node:assert/strict');
const ts = require('typescript');
const fs = require('node:fs');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');

// Load customCollections dependency first
const customColCode = ts.transpileModule(fs.readFileSync(require.resolve('../lib/customCollections.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText;
const customColSandbox = { exports: {}, crypto: webcrypto, TextEncoder, URL };
vm.runInNewContext(customColCode, customColSandbox);

// Load catalogPacks
const catalogPacksCode = ts.transpileModule(fs.readFileSync(require.resolve('../lib/catalogPacks.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText;
const sandbox = {
  exports: {},
  require: (id) => {
    if (id.includes('customCollections')) return customColSandbox.exports;
    return require(id);
  },
  crypto: webcrypto,
  TextEncoder,
  URL,
  Response,
  fetch: async (url) => {
    if (url.includes('cinema-essentials.json')) {
      const data = {
        id: 'cinema-essentials',
        name: 'Cinema Essentials',
        author: 'ARVIO Team',
        version: '1.0.0',
        catalogs: [
          { name: 'Trending in Movies', url: 'https://mdblist.com/lists/snoak/trending-movies' },
          { name: 'Top 10 Movies Today', url: 'https://mdblist.com/lists/snoak/top-10-movies-of-the-day' }
        ]
      };
      return new Response(JSON.stringify(data), { status: 200, statusText: 'OK' });
    }
    if (url.includes('sample-collections.json')) {
      const data = [
        {
          id: 'col-1',
          title: 'My Custom Rail',
          folders: [
            {
              id: 'f-1',
              title: 'Popular Sci-Fi',
              sources: [{ provider: 'tmdb', tmdbSourceType: 'DISCOVER', mediaType: 'MOVIE', filters: {} }]
            }
          ]
        }
      ];
      return new Response(JSON.stringify(data), { status: 200, statusText: 'OK' });
    }
    return new Response('Not found', { status: 404, statusText: 'Not Found' });
  }
};
vm.runInNewContext(catalogPacksCode, sandbox);

const {
  parsePackJson,
  fetchAndParsePack,
  installPackToCatalogs,
  removePackFromCatalogs,
  isPackInstalled,
  getInstalledPacksSummary
} = sandbox.exports;

test('parsePackJson parses raw JSON string directly without network requests', async () => {
  const json = JSON.stringify({
    id: 'pasted-pack',
    name: 'Pasted Catalog Pack',
    catalogs: [
      { name: 'Row 1', url: 'https://mdblist.com/lists/user/row-1' },
      { name: 'Row 2', url: 'https://trakt.tv/users/me/lists/row-2' }
    ]
  });

  const parsed = await parsePackJson(json, 'clipboard-paste');
  assert.equal(parsed.type, 'catalog-pack');
  assert.equal(parsed.packId, 'pack_pasted-pack');
  assert.equal(parsed.packName, 'Pasted Catalog Pack');
  assert.equal(parsed.catalogs.length, 2);
  assert.equal(parsed.catalogs[1].sourceType, 'trakt');
});

test('parsePackJson parses uploaded custom collections JSON file content', async () => {
  const collectionsJson = JSON.stringify({
    name: 'My Uploaded Collections',
    collections: [
      {
        id: 'col-upload-1',
        title: 'Action Collection',
        folders: [
          {
            id: 'fold-1',
            title: 'Blockbusters',
            sources: [{ provider: 'tmdb', tmdbSourceType: 'DISCOVER', mediaType: 'MOVIE', filters: {} }]
          }
        ]
      }
    ]
  });

  const parsed = await parsePackJson(collectionsJson, 'my-collection.json');
  assert.equal(parsed.type, 'collections');
  assert.ok(parsed.packId.startsWith('usercol_'));
  assert.ok(parsed.catalogs.length >= 2);
});

test('parsePackJson throws helpful error on invalid JSON', async () => {
  await assert.rejects(
    async () => parsePackJson('{ not valid json }', 'bad.json'),
    /Invalid JSON/
  );
  await assert.rejects(
    async () => parsePackJson('   ', 'empty.json'),
    /JSON content is empty/
  );
});

test('fetchAndParsePack parses a Catalog Pack manifest', async () => {
  const result = await fetchAndParsePack('https://example.com/packs/cinema-essentials.json');
  assert.equal(result.type, 'catalog-pack');
  assert.equal(result.packId, 'pack_cinema-essentials');
  assert.equal(result.packName, 'Cinema Essentials');
  assert.equal(result.catalogs.length, 2);
  assert.equal(result.catalogs[0].sourceType, 'mdblist');
  assert.equal(result.catalogs[0].name, 'Trending in Movies');
  assert.equal(result.catalogs[0].packId, 'pack_cinema-essentials');
});

test('fetchAndParsePack parses a Custom Collections document', async () => {
  const result = await fetchAndParsePack('https://example.com/sample-collections.json');
  assert.equal(result.type, 'collections');
  assert.ok(result.packId.startsWith('usercol_'));
  assert.ok(result.catalogs.length >= 2); // 1 rail + 1 folder
});

test('installPackToCatalogs prepends catalogs and avoids duplicate sources', () => {
  const initial = [
    { id: 'cat-1', name: 'Existing 1', sourceUrl: 'https://example.com/old', enabled: true },
    { id: 'cat-2', name: 'Existing 2', sourceUrl: 'https://example.com/other', enabled: true }
  ];

  const parsed = {
    type: 'catalog-pack',
    packId: 'pack_test',
    packName: 'Test Pack',
    catalogs: [
      { id: 'cat-new-1', name: 'New 1', sourceUrl: 'https://example.com/new1', packId: 'pack_test', packName: 'Test Pack', enabled: true },
      { id: 'cat-new-2', name: 'New 2', sourceUrl: 'https://example.com/new2', packId: 'pack_test', packName: 'Test Pack', enabled: true }
    ]
  };

  const { nextCatalogs, count } = installPackToCatalogs(initial, parsed);
  assert.equal(count, 2);
  assert.equal(nextCatalogs.length, 4);
  assert.equal(nextCatalogs[0].id, 'cat-new-1');

  // Verify isPackInstalled
  assert.equal(isPackInstalled(nextCatalogs, 'test'), true);
  assert.equal(isPackInstalled(nextCatalogs, 'pack_test'), true);
  assert.equal(isPackInstalled(nextCatalogs, 'unknown'), false);

  // Test getInstalledPacksSummary
  const summary = getInstalledPacksSummary(nextCatalogs);
  assert.equal(summary.length, 1);
  assert.equal(summary[0].packId, 'pack_test');
  assert.equal(summary[0].count, 2);

  // Test removePackFromCatalogs
  const removed = removePackFromCatalogs(nextCatalogs, 'pack_test');
  assert.equal(removed.length, 2);
  assert.equal(isPackInstalled(removed, 'test'), false);
});
