import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';

test('Student Quiz: Time utilities, clock drift and countdown formatting', async () => {
  const compiled = await build({
    stdin: {
      contents: "export * from './src/features/classroom/quizzes/utils/quizTimeUtils.ts';",
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    alias: { '@': './src' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
  });

  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
    module,
    module.exports,
    createRequire(import.meta.url)
  );

  const {
    calculateRemainingSeconds,
    formatCountdown,
    getTimerUrgency,
    formatDurationMinutes,
    isPastDeadline,
    isBeforeStartTime,
  } = module.exports;

  // 1. Countdown format
  assert.equal(formatCountdown(null), 'No limit');
  assert.equal(formatCountdown(0), '00:00');
  assert.equal(formatCountdown(-5), '00:00');
  assert.equal(formatCountdown(65), '01:05');
  assert.equal(formatCountdown(3665), '01:01:05');

  // 2. Timer Urgency
  assert.equal(getTimerUrgency(null), 'normal');
  assert.equal(getTimerUrgency(600), 'normal');
  assert.equal(getTimerUrgency(300), 'warning'); // <= 5 minutes
  assert.equal(getTimerUrgency(120), 'warning');
  assert.equal(getTimerUrgency(60), 'critical'); // <= 1 minute
  assert.equal(getTimerUrgency(10), 'critical');
  assert.equal(getTimerUrgency(0), 'expired');

  // 3. Duration display
  assert.equal(formatDurationMinutes(null), 'No limit');
  assert.equal(formatDurationMinutes(45), '45s');
  assert.equal(formatDurationMinutes(1800), '30 mins');
  assert.equal(formatDurationMinutes(1845), '30m 45s');

  // 4. Deadline checks
  const future = new Date(Date.now() + 600000).toISOString();
  const past = new Date(Date.now() - 600000).toISOString();
  assert.equal(isPastDeadline(future), false);
  assert.equal(isPastDeadline(past), true);
  assert.equal(isBeforeStartTime(future), true);
  assert.equal(isBeforeStartTime(past), false);

  // 5. Clock drift compensation
  // Server is 5 seconds ahead of client: cutoff is 60s from serverTime -> remaining should be ~60s
  const now = Date.now();
  const driftMs = 5000; // server clock ahead by 5s
  const cutoffAt = new Date(now + driftMs + 60000).toISOString();
  const remaining = calculateRemainingSeconds(cutoffAt, driftMs);
  assert.ok(remaining >= 59 && remaining <= 61, `Remaining seconds (${remaining}) should be ~60s with drift compensation`);
});

test('Student Quiz: Query Key Factory scopes TanStack Query v5 keys', async () => {
  const compiled = await build({
    stdin: {
      contents: "export { quizKeys } from './src/features/classroom/quizzes/api/quizKeys.ts';",
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    alias: { '@': './src' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
  });

  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
    module,
    module.exports,
    createRequire(import.meta.url)
  );

  const { quizKeys } = module.exports;

  assert.deepEqual(quizKeys.list('class-1'), ['quizzes', 'list', 'class-1']);
  assert.deepEqual(quizKeys.detail('class-1', 'quiz-1'), ['quizzes', 'detail', 'class-1', 'quiz-1']);
  assert.deepEqual(quizKeys.attemptDetail('class-1', 'quiz-1', 'att-1'), ['quizzes', 'attempts', 'detail', 'class-1', 'quiz-1', 'att-1']);
  assert.deepEqual(quizKeys.attemptHistory('class-1', 'quiz-1'), ['quizzes', 'attempts', 'history', 'class-1', 'quiz-1', 'self']);
  assert.deepEqual(quizKeys.review('class-1', 'quiz-1', 'att-1'), ['quizzes', 'attempts', 'review', 'class-1', 'quiz-1', 'att-1']);
  assert.deepEqual(quizKeys.studentResult('class-1', 'quiz-1', 'student-1'), ['quizzes', 'results', 'class-1', 'quiz-1', 'student-1']);
});

test('Student Quiz: API client strictly follows backend 4.7 contract endpoints', async () => {
  globalThis.localStorage = { getItem: () => 'test-token' };

  const compiled = await build({
    stdin: {
      contents: "export { apiClient } from './src/services/apiClient.ts'; export { quizApi } from './src/features/classroom/quizzes/api/quizApi.ts';",
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    alias: { '@': './src' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    external: ['axios'],
    define: { 'import.meta.env.DEV': 'false' },
  });

  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
    module,
    module.exports,
    createRequire(import.meta.url)
  );

  const { apiClient, quizApi } = module.exports;
  const recordedCalls = [];

  apiClient.defaults.adapter = async (config) => {
    recordedCalls.push({
      method: config.method.toUpperCase(),
      url: config.url,
      params: config.params,
      body: config.data ? JSON.parse(config.data) : null,
      headers: config.headers,
    });

    let mockData = null;
    if (config.url.endsWith('/quizzes')) {
      mockData = [{ id: 'quiz-1', title: 'Midterm Quiz', status: 'PUBLISHED' }];
    } else if (config.url.endsWith('/attempts') && config.method.toUpperCase() === 'POST') {
      mockData = { id: 'att-1', quizId: 'quiz-1', status: 'IN_PROGRESS', questions: [], answers: [] };
    } else if (config.url.endsWith('/answers') && config.method.toUpperCase() === 'PUT') {
      mockData = { accepted: true, attemptStatus: 'IN_PROGRESS', questionId: 'q-1', version: 1 };
    } else if (config.url.endsWith('/submit') && config.method.toUpperCase() === 'POST') {
      mockData = { id: 'att-1', quizId: 'quiz-1', status: 'SUBMITTED', score: 90 };
    } else if (config.url.endsWith('/review')) {
      mockData = [{ questionId: 'q-1', correctOptionIds: ['opt-1'], explanation: 'Test explanation' }];
    } else if (config.url.includes('/result')) {
      mockData = { submitted: true, completed: true, finalScore: 90 };
    }

    return {
      config,
      status: 200,
      statusText: 'OK',
      headers: {},
      data: { success: true, data: mockData },
    };
  };

  // 1. List quizzes
  const list = await quizApi.list('c-1');
  assert.equal(list[0].id, 'quiz-1');

  // 2. Start attempt
  const attempt = await quizApi.startAttempt('c-1', 'q-1');
  assert.equal(attempt.id, 'att-1');

  // 3. Save answer (atomic PUT with expectedVersion and idempotencyKey)
  const receipt = await quizApi.saveAnswer('c-1', 'q-1', 'att-1', {
    questionId: 'q-1',
    selectedOptionIds: ['opt-1'],
    expectedVersion: 0,
    idempotencyKey: '00000000-0000-0000-0000-000000000001',
  });
  assert.equal(receipt.accepted, true);
  assert.equal(receipt.version, 1);

  // 4. Submit attempt
  const submitted = await quizApi.submitAttempt('c-1', 'q-1', 'att-1');
  assert.equal(submitted.status, 'SUBMITTED');

  // 5. Review
  const review = await quizApi.review('c-1', 'q-1', 'att-1');
  assert.equal(review[0].questionId, 'q-1');

  // 6. Result
  const result = await quizApi.result('c-1', 'q-1', 'student-1');
  assert.equal(result.completed, true);

  // Verify all URL routes match backend contracts
  assert.deepEqual(
    recordedCalls.map((c) => `${c.method} ${c.url}`),
    [
      'GET /classrooms/c-1/quizzes',
      'POST /classrooms/c-1/quizzes/q-1/attempts',
      'PUT /classrooms/c-1/quizzes/q-1/attempts/att-1/answers',
      'POST /classrooms/c-1/quizzes/q-1/attempts/att-1/submit',
      'GET /classrooms/c-1/quizzes/q-1/attempts/att-1/review',
      'GET /classrooms/c-1/quizzes/q-1/students/student-1/result',
    ]
  );

  // Verify save answer payload format
  assert.deepEqual(recordedCalls[2].body, {
    questionId: 'q-1',
    selectedOptionIds: ['opt-1'],
    expectedVersion: 0,
    idempotencyKey: '00000000-0000-0000-0000-000000000001',
  });
});

test('Student Quiz: AutosaveQueue debounce, version increment and sequential dispatch', async () => {
  globalThis.localStorage = { getItem: () => 'test-token' };

  const compiled = await build({
    stdin: {
      contents: "export { apiClient } from './src/services/apiClient.ts'; export { AutosaveQueue } from './src/features/classroom/quizzes/utils/autosaveQueue.ts';",
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    alias: { '@': './src' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    external: ['axios'],
    define: { 'import.meta.env.DEV': 'false' },
  });

  const module = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
    module,
    module.exports,
    createRequire(import.meta.url)
  );

  const { apiClient, AutosaveQueue } = module.exports;
  const networkCalls = [];

  let currentServerVersion = 0;
  apiClient.defaults.adapter = async (config) => {
    const body = JSON.parse(config.data);
    networkCalls.push({ ...body });
    currentServerVersion++;
    return {
      config,
      status: 200,
      statusText: 'OK',
      headers: {},
      data: {
        success: true,
        data: {
          accepted: true,
          attemptStatus: 'IN_PROGRESS',
          questionId: body.questionId,
          version: currentServerVersion,
        },
      },
    };
  };

  const savedReceipts = [];
  const statusTransitions = [];

  const queue = new AutosaveQueue({
    classId: 'c-1',
    quizId: 'q-1',
    attemptId: 'att-1',
    debounceMs: 50,
    onStatusChange: (status) => statusTransitions.push(status),
    onAnswerSaved: (receipt) => savedReceipts.push(receipt),
  });

  // Rapid changes for question-A (debounce coalescing)
  queue.enqueue('question-A', ['opt-1']);
  queue.enqueue('question-A', ['opt-1', 'opt-2']);
  queue.enqueue('question-A', ['opt-2']); // Only this final selection should be dispatched

  // Wait for debounce timer to fire
  await new Promise((r) => setTimeout(r, 120));

  assert.equal(networkCalls.length, 1, 'Rapid enqueues should be debounced into a single request');
  assert.deepEqual(networkCalls[0].selectedOptionIds, ['opt-2']);
  assert.equal(networkCalls[0].expectedVersion, 0);
  assert.equal(queue.getVersion('question-A'), 1, 'Version should increment to 1 after receipt');

  // Next change on question-A should send expectedVersion = 1
  queue.enqueue('question-A', ['opt-3']);
  await queue.flush();

  assert.equal(networkCalls.length, 2);
  assert.equal(networkCalls[1].expectedVersion, 1, 'Next request must send expectedVersion = 1');
  assert.equal(queue.getVersion('question-A'), 2);

  assert.ok(statusTransitions.includes('saving'));
  assert.ok(statusTransitions.includes('saved'));

  queue.destroy();
});
