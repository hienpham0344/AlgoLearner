import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';

test('account and moderation clients use authenticated backend contracts', async () => {
  globalThis.localStorage = { getItem: () => 'access-token' };
  const compiled = await build({
    stdin: {
      contents: "export { apiClient } from './src/services/apiClient.ts'; export { authApi } from './src/features/auth/api/authApi.ts'; export { adminAccountsApi } from './src/features/admin/api/adminAccountsApi.ts';",
      resolveDir: process.cwd(), loader: 'ts',
    },
    alias: { '@': './src' }, bundle: true, platform: 'node', format: 'cjs', write: false,
    external: ['axios'], define: { 'import.meta.env.DEV': 'false' },
  });
  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
    module, module.exports, createRequire(import.meta.url),
  );
  const { apiClient, authApi, adminAccountsApi } = module.exports;
  const calls = [];
  apiClient.defaults.adapter = async config => {
    calls.push({ method: config.method, url: config.url, params: config.params,
      body: config.data ? JSON.parse(config.data) : null, bearer: config.headers.get('Authorization') });
    const data = config.url === '/auth/sessions' ? [{ id: 'session-1', current: true }]
      : config.url === '/admin/accounts' ? { items: [], pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0, isFirst: true, isLast: true } }
        : { id: 'account-1', status: 'SUSPENDED' };
    return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data } };
  };

  assert.equal((await authApi.sessions())[0].id, 'session-1');
  await authApi.revokeSession('session-2');
  await authApi.deactivate('ExamplePass123!');
  assert.deepEqual((await adminAccountsApi.list(0)).items, []);
  await adminAccountsApi.suspend('account-1', { reason: 'Policy violation', endsAt: null });
  await adminAccountsApi.unsuspend('account-1');
  await adminAccountsApi.reactivate('account-1');

  assert.deepEqual(calls.map(call => `${call.method.toUpperCase()} ${call.url}`), [
    'GET /auth/sessions', 'DELETE /auth/sessions/session-2', 'POST /auth/deactivate',
    'GET /admin/accounts', 'POST /admin/accounts/account-1/suspend',
    'POST /admin/accounts/account-1/unsuspend', 'POST /admin/accounts/account-1/reactivate',
  ]);
  assert.deepEqual(calls[2].body, { currentPassword: 'ExamplePass123!', confirmed: true });
  assert.deepEqual(calls[4].body, { reason: 'Policy violation', endsAt: null });
  assert.equal(calls[3].params.page, 0);
  assert.ok(calls.every(call => call.bearer === 'Bearer access-token'));

  let registrationTimeout;
  apiClient.defaults.adapter = async config => {
    registrationTimeout = config.timeout;
    return { config, status: 201, statusText: 'Created', headers: {}, data: { success: true, data: { email: 'new@example.test', message: 'Sent' } } };
  };
  await authApi.register({ email: 'new@example.test' });
  assert.equal(registrationTimeout, 32_000);
});
