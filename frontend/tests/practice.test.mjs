import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

async function loadTsModule(relativeUrl, replacements = []) {
  const fileUrl = new URL(relativeUrl, import.meta.url);
  let source = await readFile(fileUrl, 'utf8');
  for (const [target, replacement] of replacements) {
    source = source.replace(target, replacement);
  }
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

const formatters = await loadTsModule('../src/features/practice/utils/practiceFormatters.ts');

test('practiceFormatters formats UTC timestamps in Asia/Ho_Chi_Minh and groups ledger days', () => {
  const formatted = formatters.formatVietnamDateTime('2026-11-25T16:30:00Z');
  // 16:30 UTC == 23:30 UTC+7 on 25 Nov 2026
  assert.match(formatted, /25 Nov 2026/);
  assert.match(formatted, /23:30/);

  const now = new Date('2026-11-25T12:00:00Z');
  assert.equal(formatters.getVietnamDayGroup('2026-11-25T05:00:00Z', now), 'TODAY');
  assert.equal(formatters.getVietnamDayGroup('2026-11-24T05:00:00Z', now), 'YESTERDAY');
  assert.match(formatters.getVietnamDayGroup('2026-11-10T05:00:00Z', now), /10 NOV 2026/);
});

test('practiceFormatters provides non-color symbols, memory units, 64 KB stdin validation, and return paths', () => {
  const accepted = formatters.getVerdictMeta('ACCEPTED');
  assert.equal(accepted.shortSymbol, '✓');
  assert.equal(accepted.tone, 'accepted');

  const infra = formatters.getVerdictMeta('INFRASTRUCTURE_ERROR');
  assert.equal(infra.shortSymbol, '⚠');
  assert.equal(infra.tone, 'system');

  assert.equal(formatters.formatMemoryUsage(null), '—');
  assert.equal(formatters.formatMemoryUsage(512), '512 KB');
  assert.equal(formatters.formatMemoryUsage(15360), '15.0 MB');

  assert.equal(formatters.validateCustomInputBytes('5 8\n3 1 2 7 4'), null);
  const overLimit = 'a'.repeat(65 * 1024);
  assert.match(formatters.validateCustomInputBytes(overLimit), /maximum allowed is 64 KB/);

  assert.equal(
    formatters.buildProblemWorkspacePath('classroom', 'cls-1', 'p-1', '/student/classrooms/cls-1/practice?status=unsolved'),
    '/student/classrooms/cls-1/practice/p-1?from=%2Fstudent%2Fclassrooms%2Fcls-1%2Fpractice%3Fstatus%3Dunsolved',
  );
  assert.equal(
    formatters.buildProblemWorkspacePath('public', null, 'pub-1'),
    '/practice/problems/pub-1',
  );
});

test('practiceStorage isolates drafts by user and language and purges drafts on logout', async () => {
  const store = new Map();
  globalThis.localStorage = {
    get length() {
      return store.size;
    },
    key(index) {
      return Array.from(store.keys())[index] ?? null;
    },
    getItem(k) {
      return store.has(k) ? store.get(k) : null;
    },
    setItem(k, v) {
      store.set(k, String(v));
    },
    removeItem(k) {
      store.delete(k);
    },
    clear() {
      store.clear();
    },
  };

  const storage = await loadTsModule('../src/features/practice/utils/practiceStorage.ts', [
    ["import { UNAUTHORIZED_EVENT } from '@/services/apiClient';", "const UNAUTHORIZED_EVENT = 'algolearn:unauthorized';"],
  ]);

  storage.writePracticeDraft({
    userId: 'student-1',
    scope: 'classroom',
    classroomId: 'c1',
    classroomName: 'Algorithms 101',
    problemId: 'p1',
    title: 'Two Sum',
    problemType: 'STANDARD_IO',
    language: 'JAVA',
    code: 'public class Main { /* custom java */ }',
    starterCode: 'public class Main {}',
  });

  storage.writePracticeDraft({
    userId: 'student-1',
    scope: 'classroom',
    classroomId: 'c1',
    problemId: 'p1',
    title: 'Two Sum',
    problemType: 'STANDARD_IO',
    language: 'PYTHON',
    code: 'print("python draft")',
    starterCode: 'print(0)',
  });

  assert.equal(
    storage.readPracticeDraft('student-1', 'classroom', 'c1', 'p1', 'JAVA'),
    'public class Main { /* custom java */ }',
  );
  assert.equal(
    storage.readPracticeDraft('student-1', 'classroom', 'c1', 'p1', 'PYTHON'),
    'print("python draft")',
  );
  // Another user must never see student-1's drafts
  assert.equal(storage.readPracticeDraft('student-2', 'classroom', 'c1', 'p1', 'JAVA'), null);
  assert.equal(storage.readRecentDrafts('student-1').length, 1);

  // Resetting back to starter template removes the draft entry cleanly
  storage.writePracticeDraft({
    userId: 'student-1',
    scope: 'classroom',
    classroomId: 'c1',
    problemId: 'p1',
    title: 'Two Sum',
    problemType: 'STANDARD_IO',
    language: 'JAVA',
    code: 'public class Main {}',
    starterCode: 'public class Main {}',
  });
  assert.equal(storage.readPracticeDraft('student-1', 'classroom', 'c1', 'p1', 'JAVA'), null);

  // Purging on logout clears all user draft and recent keys
  storage.purgeOtherUserDrafts(null);
  assert.equal(storage.readPracticeDraft('student-1', 'classroom', 'c1', 'p1', 'PYTHON'), null);
  assert.equal(storage.readRecentDrafts('student-1').length, 0);
});

test('practiceApi classifyPracticeError distinguishes 429 rate limits, 503 Judge outages, and 409 conflicts', async () => {
  const apiModule = await loadTsModule('../src/features/practice/api/practiceApi.ts', [
    ["import { isAxiosError } from 'axios';", 'const isAxiosError = e => Boolean(e && e.isAxiosError);'],
    ["import { apiClient } from '@/services/apiClient';", 'const apiClient = {};'],
  ]);

  const makeAxiosErr = (status, errorCode, message) => ({
    isAxiosError: true,
    response: { status, data: { success: false, errorCode, message } },
  });

  const rateLimit = apiModule.classifyPracticeError(makeAxiosErr(429, 'RATE_LIMIT', 'Too many runs'));
  assert.equal(rateLimit.kind, 'rate_limit');
  assert.equal(rateLimit.message, 'Too many runs');

  const judgeOutage = apiModule.classifyPracticeError(makeAxiosErr(503, 'JUDGE_SERVICE_UNAVAILABLE', ''));
  assert.equal(judgeOutage.kind, 'infrastructure');
  assert.match(judgeOutage.message, /not marked incorrect/);

  const conflict = apiModule.classifyPracticeError(makeAxiosErr(409, 'PRACTICE_TESTCASES_CHANGED', ''));
  assert.equal(conflict.kind, 'conflict');
  assert.match(conflict.heading, /Test suite updated/);

  const archived = apiModule.classifyPracticeError(makeAxiosErr(409, 'CLASSROOM_ARCHIVED', ''));
  assert.equal(archived.kind, 'conflict');
  assert.match(archived.heading, /read-only/i);

  const notReady = apiModule.classifyPracticeError(makeAxiosErr(409, 'PRACTICE_NOT_READY', ''));
  assert.equal(notReady.kind, 'conflict');
  assert.match(notReady.heading, /incomplete/i);

  assert.deepEqual(apiModule.practiceKeys.classList('cls-1', 0), ['practice', 'class-list', 'cls-1', 0]);
  assert.deepEqual(apiModule.practiceKeys.publicDetail('pub-1'), ['practice', 'public-detail', 'pub-1']);
});

test('practiceFormatters parses Java and Python diagnostic line numbers accurately', () => {
  assert.equal(
    formatters.parseDiagnosticLine("Main.java:14: error: ';' expected\n        return\n              ^", 'JAVA'),
    14,
  );
  assert.equal(
    formatters.parseDiagnosticLine('  File "/sandbox/solution.py", line 7, in solve\n    print(x)\nNameError: name \'x\' is not defined', 'PYTHON'),
    7,
  );
  assert.equal(formatters.parseDiagnosticLine('Runtime failed without line info', 'JAVA'), null);
  assert.equal(formatters.parseDiagnosticLine('', 'PYTHON'), null);
});

test('practiceStorage returns Editorial IDE defaults and clamps saved workspace layout preferences', async () => {
  const store = new Map();
  globalThis.localStorage = {
    get length() { return store.size; },
    key(index) { return Array.from(store.keys())[index] ?? null; },
    getItem(k) { return store.has(k) ? store.get(k) : null; },
    setItem(k, v) { store.set(k, String(v)); },
    removeItem(k) { store.delete(k); },
    clear() { store.clear(); },
  };

  const storage = await loadTsModule('../src/features/practice/utils/practiceStorage.ts', [
    ["import { UNAUTHORIZED_EVENT } from '@/services/apiClient';", "const UNAUTHORIZED_EVENT = 'algolearn:unauthorized';"],
  ]);

  assert.deepEqual(storage.readWorkspaceLayout(), {
    mode: 'split',
    leftWidthPercent: 36,
    editorHeightPercent: 64,
    consoleState: 'compact',
  });

  store.set(
    'algolearner:practice-layout:v1',
    JSON.stringify({ mode: 'invalid', leftWidthPercent: 10, editorHeightPercent: 95, consoleState: 'expanded' }),
  );
  assert.deepEqual(storage.readWorkspaceLayout(), {
    mode: 'split',
    leftWidthPercent: 26,
    editorHeightPercent: 76,
    consoleState: 'expanded',
  });

  storage.writeWorkspaceLayout({
    mode: 'focus',
    leftWidthPercent: 42,
    editorHeightPercent: 55,
    consoleState: 'collapsed',
  });
  assert.deepEqual(storage.readWorkspaceLayout(), {
    mode: 'focus',
    leftWidthPercent: 42,
    editorHeightPercent: 55,
    consoleState: 'collapsed',
  });
});

