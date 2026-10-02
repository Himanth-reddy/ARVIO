// Smoke only containers created by this script. No real accounts/keys are used.
const assert = require('node:assert/strict');
const { execFileSync, spawnSync } = require('node:child_process');
const { setTimeout: delay } = require('node:timers/promises');

const image = process.argv[2] || 'arvio-web:unraid-test';
if (!/^[a-z0-9][a-z0-9._/:-]*$/.test(image)) throw new Error('Invalid image reference.');
const prefix = `arvio-unraid-smoke-${process.pid}-${Date.now()}`;
const created = new Set();
function docker(args) {
  return execFileSync('docker', args, { encoding: 'utf8', timeout: 60_000, stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}
async function waitReady(url) {
  const deadline = Date.now() + 45_000;
  while (true) {
    try {
      const response = await fetch(`${url}/version.json`, { signal: AbortSignal.timeout(2_000) });
      if (response.ok) return;
    } catch {}
    if (Date.now() >= deadline) throw new Error('Container did not become ready within 45 seconds.');
    await delay(250);
  }
}
async function start(suffix, environment) {
  const name = `${prefix}-${suffix}`;
  const args = ['run', '--detach', '--name', name, '--init', '--cap-drop=ALL',
    '--security-opt=no-new-privileges', '--publish', '127.0.0.1::3000'];
  for (const [key, value] of Object.entries(environment)) args.push('--env', `${key}=${value}`);
  docker([...args, image]);
  created.add(name);
  const url = loopbackUrl(name);
  await waitReady(url);
  assert.notEqual(docker(['exec', name, 'id', '-u']), '0', 'Container must run as non-root.');
  return { name, url };
}
function loopbackUrl(name) {
  assert.ok(created.has(name), 'Only inspect containers created by this test.');
  const ports = JSON.parse(docker(['inspect', '--format', '{{json .NetworkSettings.Ports}}', name]));
  const mapping = ports['3000/tcp'].find(entry => entry.HostIp === '127.0.0.1');
  assert.ok(mapping, 'Test container must bind loopback only.');
  return `http://127.0.0.1:${mapping.HostPort}`;
}
async function configuration(url) {
  const response = await fetch(`${url}/api/selfhost-config`, { signal: AbortSignal.timeout(5_000) });
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /application\/javascript/);
  assert.match(response.headers.get('cache-control'), /no-store/);
  assert.equal(response.headers.get('x-content-type-options'), 'nosniff');
  const script = await response.text();
  const match = script.match(/^window\.__ARVIO_SELFHOST_CONFIG__ = (.+);\n?$/);
  assert.ok(match, 'Expected a JSON-only allowlisted configuration bootstrap.');
  const values = JSON.parse(match[1]);
  assert.deepEqual(Object.keys(values).sort(), ['resolverUrl','simklClientId','telegramApiHash','telegramApiId','traktClientId']);
  return { values, script };
}

(async () => {
  try {
    const baked = JSON.parse(docker(['image', 'inspect', '--format', '{{json .Config.Env}}', image]));
    assert.ok(baked.includes('NEXT_PUBLIC_SELF_HOSTED=true'));
    assert.ok(!baked.some(entry => /^(TMDB_API_KEY|TRAKT_CLIENT_SECRET|SIMKL_CLIENT_SECRET|APP_ANON_KEY|SUPABASE_ANON_KEY)=.+/.test(entry)), 'Image must not bake account keys or OAuth secrets.');
    const secretMarker = 'unraid-smoke-server-only-never-public';
    const configured = await start('configured', {
      NEXT_PUBLIC_SELF_HOSTED: 'false', // A runtime env cannot change baked deployment mode.
      TRAKT_CLIENT_ID: 'unraid-trakt-public-id',
      TRAKT_CLIENT_SECRET: secretMarker,
      SIMKL_CLIENT_ID: 'unraid-simkl-public-id',
      SIMKL_CLIENT_SECRET: secretMarker,
      TELEGRAM_API_ID: '123456', TELEGRAM_API_HASH: 'c'.repeat(32),
      ARVIO_RESOLVER_URL: 'https://resolver.example.invalid',
      ALLOW_PRIVATE_PROXY: 'false'
    });
    const { values, script } = await configuration(configured.url);
    assert.equal(values.traktClientId, 'unraid-trakt-public-id');
    assert.equal(values.simklClientId, 'unraid-simkl-public-id');
    assert.equal(values.telegramApiId, '123456');
    assert.equal(values.telegramApiHash, 'c'.repeat(32));
    assert.equal(values.resolverUrl, 'https://resolver.example.invalid');
    assert.ok(!script.includes(secretMarker), 'Bootstrap must never expose OAuth secrets.');
    const page = await fetch(configured.url).then(response => response.text());
    assert.ok(page.includes('/api/selfhost-config'), 'Independent HTML must bootstrap public runtime settings.');
    assert.ok(!page.includes(secretMarker), 'Server-rendered page must not expose OAuth secrets.');
    const cloud = await fetch(`${configured.url}/api/cloud-auth/auth-login`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}'
    });
    assert.equal(cloud.status, 503, 'Independent image must not use hosted Cloud login.');
    const metadata = await fetch(`${configured.url}/api/tmdb/movie/550`);
    assert.equal(metadata.status, 500, 'Missing personal metadata key must not borrow owner credentials.');
    assert.ok(!(await metadata.text()).includes(secretMarker));
    docker(['exec', configured.name, 'test', '-s', '/app/licenses/ARVIO-LICENSE']);
    docker(['exec', configured.name, 'test', '-s', '/app/licenses/third-party/packages.json']);
    docker(['restart', configured.name]);
    // Ephemeral host ports can be reassigned on restart (Docker Desktop does
    // this). Inspect the new mapping instead of probing the now-stale port.
    configured.url = loopbackUrl(configured.name);
    await waitReady(configured.url);
    const restarted = await configuration(configured.url);
    assert.deepEqual(restarted.values, values, 'Runtime settings must survive a container restart.');
    console.log('Container HTTP/security/runtime/restart smoke checks passed (dummy credentials only).');
    const { checkUnraidBrowser } = require('./check-unraid-browser.cjs');
    console.log(JSON.stringify(await checkUnraidBrowser(configured.url), null, 2));
    const empty = await start('empty', { ALLOW_PRIVATE_PROXY: 'false' });
    const defaults = await configuration(empty.url);
    assert.ok(Object.values(defaults.values).every(value => value === ''), 'No owner integration IDs may appear in an unconfigured image.');
    console.log('Unconfigured-image defaults passed. This is Docker testing, not proof of a real Unraid installation or public registry access.');
  } catch (error) {
    // Logs belong only to disposable containers with the dummy env above.
    // Preserve startup diagnostics before the exact-name cleanup runs.
    for (const name of created) {
      try {
        const logs = spawnSync('docker', ['logs', '--tail', '40', name],
          { encoding: 'utf8', timeout: 10_000, stdio: ['ignore', 'pipe', 'pipe'] });
        console.error(`Diagnostics for ${name}:\n${logs.stdout || ''}${logs.stderr || ''}`);
      } catch {}
    }
    throw error;
  } finally {
    for (const name of created) {
      // Exact generated test names only; never prune or remove other containers.
      try { docker(['rm', '--force', name]); } catch { console.error(`Could not remove disposable test container ${name}.`); }
    }
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
