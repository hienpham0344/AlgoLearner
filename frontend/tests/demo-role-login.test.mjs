import test from 'node:test';
import assert from 'node:assert/strict';
import { demoRoleIdentity } from '../dev/demoRoleIdentity.ts';

test('teacher and admin demo sign-in accept both username and example.test email', () => {
  assert.equal(demoRoleIdentity('teacher', 'demo'), 'teacher');
  assert.equal(demoRoleIdentity('Teacher@Example.Test', 'demo'), 'teacher');
  assert.equal(demoRoleIdentity('admin', 'demo'), 'admin');
  assert.equal(demoRoleIdentity('admin@example.test', 'demo'), 'admin');
});

test('demo role aliases reject other domains and reserve student for backend mock mode', () => {
  assert.equal(demoRoleIdentity('teacher@example.com', 'demo'), null);
  assert.equal(demoRoleIdentity('student@example.test', 'demo'), null);
  assert.equal(demoRoleIdentity('student@example.test', 'backend-mock'), 'student');
});
