import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/features/auth/utils/loginAttempts.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } });
const { readLoginAttempts, nextLoginAttempt, emptyLoginAttempts, LOCKOUT_DURATION_MS, formatLockTime } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('a restored restriction keeps its original expiry instead of restarting on refresh', () => {
  const now = 1000000;
  const stored = JSON.stringify({ attempts: 5, lockedUntil: now + LOCKOUT_DURATION_MS });
  const restored = readLoginAttempts(stored, now + 120000);
  assert.equal(restored.lockedUntil, now + LOCKOUT_DURATION_MS);
  assert.equal(restored.attempts, 5);
  assert.deepEqual(nextLoginAttempt(restored, now + 120000), restored);
});

test('five rejected attempts restrict sign-in for fifteen minutes', () => {
  let state = emptyLoginAttempts();
  for (let attempt = 1; attempt < 5; attempt++) {
    state = nextLoginAttempt(state, 1000);
    assert.equal(state.lockedUntil, null);
  }
  state = nextLoginAttempt(state, 1000);
  assert.equal(state.attempts, 5);
  assert.equal(state.lockedUntil, 1000 + 15 * 60 * 1000);
});

test('expiry clears previous failures and permits a fresh attempt', () => {
  const stored = JSON.stringify({ attempts: 5, lockedUntil: 2000 });
  assert.deepEqual(readLoginAttempts(stored, 2000), emptyLoginAttempts());
  assert.deepEqual(nextLoginAttempt({ attempts: 5, lockedUntil: 2000 }, 3000), { attempts: 1, lockedUntil: null });
});

test('malformed or invalid stored state cannot leave the form permanently locked', () => {
  for (const stored of ['broken', 'null', '[]', '{}', '{"attempts":-1,"lockedUntil":null}', '{"attempts":99,"lockedUntil":null}', '{"attempts":5,"lockedUntil":null}', '{"attempts":1,"lockedUntil":"never"}']) {
    assert.deepEqual(readLoginAttempts(stored), emptyLoginAttempts());
  }
});

test('retry time never goes negative and rounds up partial seconds', () => {
  assert.equal(formatLockTime(-1), '00:00');
  assert.equal(formatLockTime(899), '14:59');
  assert.equal(formatLockTime(60.2), '01:01');
});
