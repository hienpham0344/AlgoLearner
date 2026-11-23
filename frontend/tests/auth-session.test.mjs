import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
const axios = createRequire(import.meta.url)('axios');

test('expired concurrent requests share one refresh; public auth never carries stale bearer', async () => {
  const storage = new Map([['algolearner_token', 'expired-access']]);
  globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) };
  globalThis.window = { dispatchEvent() {}, location: { pathname: '/profile' } };
  let refreshes = 0;
  let rejected = false;
  axios.defaults.adapter = async config => {
    const response = data => ({ status: 200, statusText: 'OK', config, headers: {}, data: { data } });
    if (config.url === '/auth/refresh-token') {
      refreshes++;
      assert.equal(config.headers.get('Authorization'), undefined);
      assert.equal(config.headers.get('X-Requested-With'), 'XMLHttpRequest');
      await new Promise(resolve => setTimeout(resolve, 10));
      if (rejected) throw new axios.AxiosError('Expired session', 'ERR_BAD_REQUEST', config, undefined, { status: 401 });
      return response({ token: 'fresh-access', user: { username: 'learner' } });
    }
    if (config.url === '/auth/login') {
      assert.equal(config.headers.get('Authorization'), undefined);
      throw new axios.AxiosError('Wrong password', 'ERR_BAD_REQUEST', config, undefined, { status: 401 });
    }
    if (config.headers.get('Authorization') !== 'Bearer fresh-access') {
      throw new axios.AxiosError('Expired access', 'ERR_BAD_REQUEST', config, undefined, { status: 401 });
    }
    return response({ username: 'learner' });
  };
  const compiled = await build({
    entryPoints: ['./src/services/apiClient.ts'], bundle: true, platform: 'node', format: 'cjs', write: false,
    external: ['axios'], alias: { '@': './src' },
  });
  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(module, module.exports, createRequire(import.meta.url));
  const { apiClient } = module.exports;
  await Promise.all([apiClient.get('/users/me/profile'), apiClient.get('/classrooms')]);
  assert.equal(refreshes, 1);
  assert.equal(storage.get('algolearner_token'), 'fresh-access');
  await assert.rejects(apiClient.post('/auth/login', {}));
  assert.equal(refreshes, 1);
  assert.equal(storage.get('algolearner_token'), 'fresh-access');
  storage.set('algolearner_token', 'expired-again');
  rejected = true;
  await assert.rejects(apiClient.get('/users/me/profile'));
  assert.equal(refreshes, 2);
  assert.equal(storage.get('algolearner_token'), undefined);
});

test('logout revokes by bearer and retries with cookie when access token expired', async () => {
  const storage = new Map([['algolearner_token', 'current-access']]);
  globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) };
  globalThis.window = { dispatchEvent() {}, location: { pathname: '/profile' } };
  const sent = [];
  axios.defaults.adapter = async config => {
    assert.equal(config.url, '/auth/logout');
    sent.push(config.headers.get('Authorization'));
    if (sent.length === 2) throw new axios.AxiosError('Expired access', 'ERR_BAD_REQUEST', config, undefined, { status: 401 });
    return { status: 200, statusText: 'OK', config, headers: {}, data: { data: {} } };
  };
  const compiled = await build({
    entryPoints: ['./src/services/apiClient.ts'], bundle: true, platform: 'node', format: 'cjs', write: false,
    external: ['axios'], alias: { '@': './src' },
  });
  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(module, module.exports, createRequire(import.meta.url));
  await module.exports.logoutSession();
  assert.deepEqual(sent, ['Bearer current-access']);
  assert.equal(storage.get('algolearner_token'), undefined);
  storage.set('algolearner_token', 'expired-access');
  await module.exports.logoutSession();
  assert.deepEqual(sent, ['Bearer current-access', 'Bearer expired-access', undefined]);
  assert.equal(storage.get('algolearner_token'), undefined);
});
