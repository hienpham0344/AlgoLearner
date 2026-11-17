import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';

test('classroom API uses backend routes and nested page envelope', async () => {
  globalThis.localStorage = { getItem: () => null };
  const compiled = await build({
    stdin: {
      contents: "export { apiClient } from './src/services/apiClient.ts'; export { classroomApi, classroomApiUnavailable } from './src/features/classroom/api/classroomApi.ts';",
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    alias: { '@': './src' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    external: ['axios'],
    define: { 'import.meta.env.DEV': 'true' },
  });
  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
    module, module.exports, createRequire(import.meta.url),
  );
  const { apiClient, classroomApi, classroomApiUnavailable } = module.exports;
  {
    const calls = [];
    apiClient.defaults.adapter = async config => {
      calls.push({ method: config.method, url: config.url, params: config.params, body: config.data ? JSON.parse(config.data) : null });
      const data = config.method === 'get' && (config.url?.endsWith('/members') || config.url?.endsWith('/join-requests') || config.url === '/classrooms')
        ? { items: [], pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0, isFirst: true, isLast: true } }
        : config.url === '/classrooms/join'
          ? { outcome: 'PENDING_APPROVAL', classroomId: 'class-1', requestId: 'request-1' }
          : { id: 'class-1', name: 'Algorithms' };
      return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data, timestamp: '2026-11-09T00:00:00Z' } };
    };

    const list = await classroomApi.list(2);
    assert.deepEqual(list.items, []);
    assert.equal(list.pagination.totalElements, 0);
    assert.deepEqual(calls[0], { method: 'get', url: '/classrooms', params: { page: 2, size: 20 }, body: null });

    const created = await classroomApi.create({ name: 'Algorithms', description: '', joinMode: 'AUTO_JOIN', codeExpiresAt: null });
    assert.equal(created.id, 'class-1');
    assert.equal(calls[1].method, 'post');
    assert.equal(calls[1].url, '/classrooms');
    assert.equal(calls[1].body.name, 'Algorithms');

    const joined = await classroomApi.join('ABCD1234');
    assert.equal(joined.outcome, 'PENDING_APPROVAL');
    assert.deepEqual(calls[2].body, { classCode: 'ABCD1234' });

    await classroomApi.members('class-1');
    assert.equal(calls[3].url, '/classrooms/class-1/members');
    await classroomApi.archive('class-1');
    assert.equal(calls[4].url, '/classrooms/class-1/archive');

    await classroomApi.get('class-1');
    await classroomApi.update('class-1', { name: 'Updated', description: 'Details' });
    await classroomApi.remove('class-1');
    await classroomApi.configure('class-1', { codeExpiresAt: null, codeLocked: true, joiningOpen: false, joinMode: 'REQUIRE_APPROVAL' });
    await classroomApi.regenerateCode('class-1');
    await classroomApi.leave('class-1');
    await classroomApi.directAdd('class-1', 'student@example.test');
    await classroomApi.removeMember('class-1', 'student-1');
    await classroomApi.requests('class-1', 1);
    await classroomApi.approve('class-1', 'request-1');
    await classroomApi.invite('class-1', 'student@example.test', '2026-11-10T00:00:00Z');
    await classroomApi.revoke('class-1', 'invitation-1');
    await classroomApi.acceptInvitation('invitation-1');
    await classroomApi.declineInvitation('invitation-1');
    await classroomApi.transferOwner('class-1', 'teacher-2');

    assert.deepEqual(calls.map(({ method, url }) => `${method.toUpperCase()} ${url}`), [
      'GET /classrooms', 'POST /classrooms', 'POST /classrooms/join',
      'GET /classrooms/class-1/members', 'POST /classrooms/class-1/archive',
      'GET /classrooms/class-1', 'PATCH /classrooms/class-1', 'DELETE /classrooms/class-1',
      'PATCH /classrooms/class-1/join-configuration', 'POST /classrooms/class-1/class-code/regenerate',
      'POST /classrooms/class-1/leave', 'POST /classrooms/class-1/members/direct',
      'DELETE /classrooms/class-1/members/student-1', 'GET /classrooms/class-1/join-requests',
      'POST /classrooms/class-1/join-requests/request-1/approve',
      'POST /classrooms/class-1/invitations', 'POST /classrooms/class-1/invitations/invitation-1/revoke',
      'POST /classrooms/invitations/invitation-1/accept', 'POST /classrooms/invitations/invitation-1/decline',
      'POST /classrooms/class-1/transfer-owner',
    ]);
    assert.deepEqual(calls[8].body, { codeExpiresAt: null, codeLocked: true, joiningOpen: false, joinMode: 'REQUIRE_APPROVAL' });
    assert.deepEqual(calls[11].body, { usernameOrEmail: 'student@example.test' });
    assert.deepEqual(calls[15].body, { email: 'student@example.test', expiresAt: '2026-11-10T00:00:00Z' });
    assert.deepEqual(calls[19].body, { targetTeacherId: 'teacher-2' });
    assert.equal(classroomApiUnavailable({ isAxiosError: true, response: { status: 401 } }), false);
    assert.equal(classroomApiUnavailable({ isAxiosError: true, response: { status: 403 } }), false);
    assert.equal(classroomApiUnavailable({ isAxiosError: true, response: { status: 404 } }), true);
    assert.equal(classroomApiUnavailable({ isAxiosError: true, response: { status: 500, data: { message: 'Database error' } } }), false);
    assert.equal(classroomApiUnavailable({ isAxiosError: true }), true);
  }
});
