import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { build } from 'esbuild';
import { createRequire } from 'node:module';

test('backend responses never trigger mock; connection refusal does, and recovery restores backend', async () => {
  const result = await build({ entryPoints: ['dev/backendFirstPlugin.ts'], bundle: true, platform: 'node', format: 'cjs', write: false });
  const module = { exports: {} };
  new Function('module', 'exports', 'require', result.outputFiles[0].text)(module, module.exports, createRequire(import.meta.url));
  const { createBackendMiddleware, isOfflineRequest } = module.exports;
  const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
  const close = server => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); });
  let receivedWrites = 0;
  const backend = createServer(async (req, res) => {
    let body = ''; for await (const part of req) body += part;
    if (req.url === '/api/reset') {
      receivedWrites++;
      req.socket.destroy();
      return;
    }
    res.writeHead(Number(req.url.split('/').at(-1)), { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ body, authorization: req.headers.authorization ?? null }));
  });
  const port = await listen(backend);
  const gateway = createBackendMiddleware(`http://127.0.0.1:${port}`);
  let fallbacks = 0;
  const frontend = createServer((req, res) => gateway(req, res, async () => {
    assert.equal(isOfflineRequest(req), true); fallbacks++;
    let body = ''; for await (const part of req) body += part;
    res.end(body);
  }));
  const frontendPort = await listen(frontend);
  try {
    for (const status of [200, 400, 401, 403, 404, 500, 503]) {
      const response = await fetch(`http://127.0.0.1:${frontendPort}/api/${status}`, { method: 'POST', body: 'payload' });
      assert.equal(response.status, status);
      assert.equal(response.headers.get('x-algolearner-source'), 'backend');
      assert.equal((await response.json()).body, 'payload');
    }
    assert.equal(fallbacks, 0);
    const interrupted = await fetch(`http://127.0.0.1:${frontendPort}/api/reset`, { method: 'POST', body: 'already received' });
    assert.equal(interrupted.status, 502);
    assert.equal(interrupted.headers.get('x-algolearner-source'), 'backend');
    assert.equal((await interrupted.json()).errorCode, 'BACKEND_UNREACHABLE');
    assert.equal(receivedWrites, 1);
    assert.equal(fallbacks, 0, 'A disconnected backend must not replay a write against mock data');
    await close(backend);
    const offline = await fetch(`http://127.0.0.1:${frontendPort}/api/v1/auth/login`, { method: 'POST', body: 'unchanged body' });
    assert.equal(offline.headers.get('x-algolearner-source'), 'mock');
    assert.equal(await offline.text(), 'unchanged body');
    assert.equal(fallbacks, 1);
    await new Promise(resolve => backend.listen(port, '127.0.0.1', resolve));
    const recovered = await fetch(`http://127.0.0.1:${frontendPort}/api/200`, { headers: { Authorization: 'Bearer mock-student' } });
    assert.equal(recovered.headers.get('x-algolearner-source'), 'backend');
    assert.equal((await recovered.json()).authorization, null);
    assert.equal(fallbacks, 1);
  } finally { await close(backend); await close(frontend); }
});
