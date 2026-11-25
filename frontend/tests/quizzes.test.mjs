import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';

const mockStorage = {};
globalThis.window = {
  addEventListener: () => {},
  removeEventListener: () => {},
  confirm: () => true,
};
globalThis.localStorage = {
  getItem: (k) => mockStorage[k] || null,
  setItem: (k, v) => { mockStorage[k] = v; },
  removeItem: (k) => { delete mockStorage[k]; },
};

const compiled = await build({
  stdin: {
    contents: `
      export * from './src/features/classroom/quizzes/utils/timeUtils.ts';
      export * from './src/features/classroom/quizzes/utils/quizValidation.ts';
      export * from './src/features/classroom/quizzes/utils/autosaveQueue.ts';
      export * from './src/features/classroom/quizzes/api/quizKeys.ts';
      export * from './src/features/classroom/quizzes/api/quizApi.ts';
      export { apiClient } from './src/services/apiClient.ts';
    `,
    resolveDir: process.cwd(),
    loader: 'ts',
  },
  alias: { '@': './src' },
  bundle: true,
  platform: 'node',
  format: 'cjs',
  write: false,
  jsx: 'automatic',
  external: ['axios', 'react', 'react/jsx-runtime'],
});

const module = { exports: {} };
new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
  module,
  module.exports,
  createRequire(import.meta.url)
);

const {
  formatVietnamDateTime,
  formatVietnamDateTimeFull,
  formatRemainingTime,
  isPastDeadline,
  isBeforeStartTime,
  localInputToIsoUtc,
  isoUtcToLocalInput,
  formatDurationHuman,
  validateQuestion,
  validateQuizConfig,
  canStudentStartAttempt,
  calculateQuizScoreDisplay,
  QuizAutosaveManager,
  quizKeys,
  quizApi,
  quizError,
  apiClient,
} = module.exports;

test('TimeUtils: formatVietnamDateTime properly formats UTC to UTC+7', () => {
  const utc = '2026-11-20T07:30:00Z'; // 07:30 UTC = 14:30 Vietnam (UTC+7)
  const formatted = formatVietnamDateTime(utc);
  assert.match(formatted, /14:30/);
  assert.match(formatted, /20\/11\/2026/);
});

test('TimeUtils: formatRemainingTime produces valid MM:SS and HH:MM:SS', () => {
  assert.equal(formatRemainingTime(0), '00:00');
  assert.equal(formatRemainingTime(65), '01:05');
  assert.equal(formatRemainingTime(3600), '01:00:00');
  assert.equal(formatRemainingTime(3665), '01:01:05');
});

test('TimeUtils: isPastDeadline and isBeforeStartTime correctly evaluate dates', () => {
  const past = new Date(Date.now() - 60000).toISOString();
  const future = new Date(Date.now() + 60000).toISOString();

  assert.equal(isPastDeadline(past), true);
  assert.equal(isPastDeadline(future), false);
  assert.equal(isBeforeStartTime(future), true);
  assert.equal(isBeforeStartTime(past), false);
});

test('TimeUtils: localInputToIsoUtc and isoUtcToLocalInput roundtrip conversion', () => {
  const localInput = '2026-11-20T14:30';
  const isoUtc = localInputToIsoUtc(localInput);
  assert.ok(isoUtc);
  assert.ok(isoUtc.endsWith('Z'));

  const backToLocal = isoUtcToLocalInput(isoUtc);
  assert.equal(backToLocal, localInput);
});

test('TimeUtils: formatDurationHuman produces readable strings', () => {
  assert.equal(formatDurationHuman(null), 'Untimed');
  assert.equal(formatDurationHuman(30), '30s');
  assert.equal(formatDurationHuman(1800), '30 mins');
  assert.equal(formatDurationHuman(3600), '1 hour');
  assert.equal(formatDurationHuman(5400), '1h 30m');
});

test('Validation: Single Choice requires exactly 1 correct answer', () => {
  const validSingle = {
    id: 'q1',
    text: 'What is 2 + 2?',
    type: 'SINGLE_CHOICE',
    points: 1,
    options: [
      { id: 'o1', text: '4', correct: true },
      { id: 'o2', text: '5', correct: false },
    ],
  };
  const resValid = validateQuestion(validSingle, 'ALL_OR_NOTHING');
  assert.equal(resValid.isValid, true);

  // 1 correct answer with 3 options -> Valid
  const validThreeOptions = {
    ...validSingle,
    options: [
      { id: 'o1', text: '4', correct: true },
      { id: 'o2', text: '5', correct: false },
      { id: 'o3', text: '6', correct: false },
    ],
  };
  const resValidThree = validateQuestion(validThreeOptions, 'ALL_OR_NOTHING');
  assert.equal(resValidThree.isValid, true);

  // 0 correct answers (all false) -> Invalid
  const zeroCorrectSingle = {
    ...validSingle,
    options: [
      { id: 'o1', text: '4', correct: false },
      { id: 'o2', text: '5', correct: false },
    ],
  };
  const resZero = validateQuestion(zeroCorrectSingle, 'ALL_OR_NOTHING');
  assert.equal(resZero.isValid, false);
  assert.match(resZero.errors.correct, /exactly one correct answer/);

  // Multiple correct answers (2 of 2) -> Invalid
  const invalidSingle = {
    ...validSingle,
    options: [
      { id: 'o1', text: '4', correct: true },
      { id: 'o2', text: '2 * 2', correct: true },
    ],
  };
  const resInvalid = validateQuestion(invalidSingle, 'ALL_OR_NOTHING');
  assert.equal(resInvalid.isValid, false);
  assert.match(resInvalid.errors.correct, /exactly one correct answer/);

  // Multiple correct answers (2 of 3) -> Invalid
  const multiCorrectThree = {
    ...validSingle,
    options: [
      { id: 'o1', text: '4', correct: true },
      { id: 'o2', text: '2 * 2', correct: true },
      { id: 'o3', text: '5', correct: false },
    ],
  };
  const resMultiThree = validateQuestion(multiCorrectThree, 'ALL_OR_NOTHING');
  assert.equal(resMultiThree.isValid, false);
  assert.match(resMultiThree.errors.correct, /exactly one correct answer/);
});

test('Validation: Multiple Choice requires at least one correct and at least one incorrect answer', () => {
  const baseMC = {
    id: 'q-mc',
    text: 'Select even numbers',
    type: 'MULTIPLE_CHOICE',
    points: 2,
    options: [
      { id: 'm1', text: '2', correct: true },
      { id: 'm2', text: '4', correct: true },
      { id: 'm3', text: '5', correct: false },
    ],
  };
  // Valid MC (2 correct, 1 incorrect)
  assert.equal(validateQuestion(baseMC).isValid, true);

  // 0 correct (all false) -> Error: at least one correct
  const allFalseMC = {
    ...baseMC,
    options: [
      { id: 'm1', text: '2', correct: false },
      { id: 'm2', text: '4', correct: false },
    ],
  };
  const resAllFalse = validateQuestion(allFalseMC);
  assert.equal(resAllFalse.isValid, false);
  assert.match(resAllFalse.errors.correct, /At least one correct answer must be selected/);

  // 0 incorrect (all true) -> Error: at least one incorrect
  const allTrueMC = {
    ...baseMC,
    options: [
      { id: 'm1', text: '2', correct: true },
      { id: 'm2', text: '4', correct: true },
    ],
  };
  const resAllTrue = validateQuestion(allTrueMC);
  assert.equal(resAllTrue.isValid, false);
  assert.match(resAllTrue.errors.correct, /At least one incorrect answer is required/);
});

test('Validation: True/False requires exactly 2 options', () => {
  const validTF = {
    id: 'q-tf',
    text: 'Water freezes at 0 degrees Celsius.',
    type: 'TRUE_FALSE',
    points: 1,
    options: [
      { id: 'tf1', text: 'True', correct: true },
      { id: 'tf2', text: 'False', correct: false },
    ],
  };
  assert.equal(validateQuestion(validTF).isValid, true);

  const invalidTF = {
    ...validTF,
    options: [
      { id: 'tf1', text: 'True', correct: true },
      { id: 'tf2', text: 'False', correct: false },
      { id: 'tf3', text: 'Maybe', correct: false },
    ],
  };
  const resTF = validateQuestion(invalidTF);
  assert.equal(resTF.isValid, false);
  assert.match(resTF.errors.options, /exactly 2 options/);
});

test('Validation: Partial Credit requires option weights summing to question points', () => {
  const partialQ = {
    id: 'q-partial',
    text: 'Select prime numbers',
    type: 'MULTIPLE_CHOICE',
    points: 2.0,
    options: [
      { id: 'o1', text: '2', correct: true, weight: 1.0 },
      { id: 'o2', text: '3', correct: true, weight: 1.0 },
      { id: 'o3', text: '4', correct: false, weight: 0.0 },
    ],
  };
  const resValid = validateQuestion(partialQ, 'PARTIAL_CREDIT');
  assert.equal(resValid.isValid, true);

  // Mismatched sum
  const partialQBadSum = {
    ...partialQ,
    options: [
      { id: 'o1', text: '2', correct: true, weight: 0.5 },
      { id: 'o2', text: '3', correct: true, weight: 0.5 },
      { id: 'o3', text: '4', correct: false, weight: 0.0 },
    ],
  };
  const resBadSum = validateQuestion(partialQBadSum, 'PARTIAL_CREDIT');
  assert.equal(resBadSum.isValid, false);
  assert.match(resBadSum.errors.weightsSum, /must equal question points/);
});

test('Validation: validateQuizConfig checks mandatory rules and limits', () => {
  const validConfig = {
    kind: 'GRADED',
    maxAttempts: 2,
    startTime: '2026-11-20T00:00:00Z',
    deadline: '2026-11-25T00:00:00Z',
    allowLateAttempts: false,
    timeLimitSeconds: 3600,
    timeout: 'AUTO_SUBMIT',
    shuffleQuestions: true,
    shuffleAnswers: true,
    scoring: 'ALL_OR_NOTHING',
    completion: 'PASSING_SCORE',
    passingScore: 75,
    publication: 'AUTO',
    showCorrectAnswers: true,
    showExplanations: true,
    questions: [
      {
        id: 'q1',
        text: 'Sample question',
        type: 'SINGLE_CHOICE',
        points: 1,
        options: [
          { id: 'o1', text: 'Yes', correct: true },
          { id: 'o2', text: 'No', correct: false },
        ],
      },
    ],
  };

  const validRes = validateQuizConfig('Sample Quiz Title', validConfig);
  assert.equal(validRes.isValid, true);

  // Blank title
  assert.equal(validateQuizConfig('', validConfig).isValid, false);

  // Inverted deadline
  const invalidDates = {
    ...validConfig,
    startTime: '2026-11-25T00:00:00Z',
    deadline: '2026-11-20T00:00:00Z',
  };
  assert.equal(validateQuizConfig('Title', invalidDates).isValid, false);

  // Empty questions
  assert.equal(validateQuizConfig('Title', { ...validConfig, questions: [] }).isValid, false);
});

test('Eligibility: canStudentStartAttempt correctly checks status, timing, and attempts', () => {
  const baseQuiz = {
    id: 'quiz-1',
    classroomId: 'class-1',
    title: 'Midterm Test',
    status: 'PUBLISHED',
    kind: 'GRADED',
    maxAttempts: 2,
    startTime: null,
    deadline: null,
    timeLimitSeconds: 3600,
    questions: [],
    teacherConfiguration: null,
  };

  // 1. Published, within attempts
  const res1 = canStudentStartAttempt(baseQuiz, { attemptsUsed: 0, attemptsAllowed: 2 });
  assert.equal(res1.canStart, true);

  // 2. Draft quiz -> cannot start
  const resDraft = canStudentStartAttempt({ ...baseQuiz, status: 'DRAFT' }, null);
  assert.equal(resDraft.canStart, false);

  // 3. Closed quiz -> cannot start
  const resClosed = canStudentStartAttempt({ ...baseQuiz, status: 'CLOSED' }, null);
  assert.equal(resClosed.canStart, false);

  // 4. Exhausted attempts -> cannot start
  const resExhausted = canStudentStartAttempt(baseQuiz, { attemptsUsed: 2, attemptsAllowed: 2 });
  assert.equal(resExhausted.canStart, false);

  // 5. Active attempt exists -> can always resume even if attempts used up or deadline passed!
  const resResume = canStudentStartAttempt(
    { ...baseQuiz, deadline: '2020-01-01T00:00:00Z' },
    { attemptsUsed: 2, attemptsAllowed: 2 },
    true // activeAttemptExists
  );
  assert.equal(resResume.canStart, true);
});

test('AutosaveManager: debounces and guarantees sequential processing without race conditions', async () => {
  const recordedSaves = [];
  const manager = new QuizAutosaveManager(async (qId, optionIds) => {
    recordedSaves.push({ qId, optionIds: [...optionIds] });
  }, 20);

  manager.enqueue('q1', ['o1']);
  manager.enqueue('q1', ['o1', 'o2']);
  manager.enqueue('q1', ['o2']); // Final selection

  await manager.flush();

  // Final processed call should be the latest selection
  assert.ok(recordedSaves.length >= 1);
  const lastCall = recordedSaves[recordedSaves.length - 1];
  assert.equal(lastCall.qId, 'q1');
  assert.deepEqual(lastCall.optionIds, ['o2']);

  manager.destroy();
});

test('QuizKeys: factory returns structured and segregable query keys', () => {
  assert.deepEqual(quizKeys.all, ['quizzes']);
  assert.deepEqual(quizKeys.list('c1', 0, 20), ['quizzes', 'list', 'c1', { page: 0, size: 20 }]);
  assert.deepEqual(quizKeys.detail('c1', 'q1'), ['quizzes', 'detail', 'c1', 'q1']);
  assert.deepEqual(quizKeys.attempt('c1', 'q1', 'a1'), ['quizzes', 'attempts', 'c1', 'q1', 'a1']);
  assert.deepEqual(quizKeys.result('c1', 'q1', 's1'), ['quizzes', 'results', 'c1', 'q1', 's1']);
});

test('QuizApi: maps frontend payloads and responses strictly to Backend QuizController contract', async () => {
  const calls = [];
  mockStorage.algolearner_user = JSON.stringify({ id: 'teacher-1', role: 'TEACHER' });

  const qId = '11111111-1111-4111-8111-111111111111';
  const o1 = '22222222-2222-4222-8222-222222222222';
  const o2 = '33333333-3333-4333-8333-333333333333';

  apiClient.defaults.adapter = async (config) => {
    calls.push({
      method: config.method,
      url: config.url,
      params: config.params,
      body: config.data ? JSON.parse(config.data) : null,
    });

    const wrap = (data) => ({
      config,
      status: 200,
      statusText: 'OK',
      headers: {},
      data: { success: true, data, timestamp: '2026-11-25T00:00:00Z' },
    });

    if (config.method === 'get' && config.url === '/classrooms/c1/quizzes') {
      return wrap([
        {
          id: 'quiz-1',
          classroomId: 'c1',
          title: 'Midterm Quiz',
          status: 'DRAFT',
          graded: true,
          opensAt: '2026-11-20T00:00:00Z',
          closesAt: '2026-11-30T00:00:00Z',
          durationSeconds: 1800,
          maxAttempts: 2,
          allowLate: false,
          releasePolicy: 'MANUAL',
        },
      ]);
    }
    if (config.method === 'get' && config.url === '/classrooms/c1/quizzes/quiz-1/authoring') {
      return wrap({
        title: 'Midterm Quiz',
        graded: true,
        opensAt: '2026-11-20T00:00:00Z',
        closesAt: '2026-11-30T00:00:00Z',
        durationSeconds: 1800,
        maxAttempts: 2,
        allowLate: false,
        shuffleQuestions: true,
        shuffleAnswers: false,
        releasePolicy: 'MANUAL',
        timeoutMode: 'AUTO_SUBMIT',
        showCorrectAnswers: true,
        showExplanations: true,
        passingScore: 60,
        questions: [
          {
            id: qId,
            prompt: '2 + 2 = ?',
            type: 'SINGLE_CHOICE',
            scoring: 'ALL_OR_NOTHING',
            points: 10,
            explanation: 'Basic arithmetic',
            options: [
              { id: o1, text: '4', correct: true, weight: null },
              { id: o2, text: '5', correct: false, weight: null },
            ],
          },
        ],
      });
    }
    if (config.url === '/classrooms/c1/quizzes' || config.url === '/classrooms/c1/quizzes/quiz-1' || config.url?.endsWith('/publish') || config.url?.endsWith('/close')) {
      return wrap({
        id: 'quiz-1',
        classroomId: 'c1',
        title: 'Midterm Quiz',
        status: config.url?.endsWith('/publish') ? 'PUBLISHED' : config.url?.endsWith('/close') ? 'CLOSED' : 'DRAFT',
        graded: true,
        opensAt: '2026-11-20T00:00:00Z',
        closesAt: '2026-11-30T00:00:00Z',
        durationSeconds: 1800,
        maxAttempts: 2,
        allowLate: false,
        releasePolicy: 'MANUAL',
      });
    }
    if (config.method === 'put' && config.url === '/classrooms/c1/quizzes/quiz-1/attempts/att-1/answers') {
      const body = JSON.parse(config.data);
      return wrap({
        accepted: true,
        attemptStatus: 'IN_PROGRESS',
        questionId: body.questionId,
        version: body.expectedVersion + 1,
      });
    }
    if (config.method === 'get' && config.url === '/classrooms/c1/quizzes/quiz-1/attempts/att-1/review') {
      return wrap([
        {
          questionId: qId,
          correctOptionIds: [o1],
          explanation: 'Basic arithmetic',
        },
      ]);
    }
    if (config.url === '/classrooms/c1/quizzes/quiz-1/attempts/att-1' || config.url === '/classrooms/c1/quizzes/quiz-1/attempts') {
      if (config.method === 'get' && config.url.endsWith('/attempts')) {
        return wrap({
          items: [
            {
              id: 'att-1',
              quizId: 'quiz-1',
              studentId: 'student-1',
              status: 'SUBMITTED',
              startedAt: '2026-11-25T01:00:00Z',
              cutoffAt: '2026-11-25T01:30:00Z',
              submittedAt: '2026-11-25T01:15:00Z',
              score: 100,
              gradePublished: true,
              questions: [{ id: qId, prompt: '2 + 2 = ?', type: 'SINGLE_CHOICE', points: 10, options: [{ id: o1, text: '4' }, { id: o2, text: '5' }] }],
              answers: [{ questionId: qId, selectedOptionIds: [o1], version: 1 }],
              serverTime: '2026-11-25T01:15:00Z',
              late: false,
            },
          ],
          pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true },
        });
      }
      const isSubmitted = config.url.endsWith('/att-1') && calls.some((c) => c.url?.endsWith('/submit'));
      return wrap({
        id: 'att-1',
        quizId: 'quiz-1',
        studentId: 'student-1',
        status: isSubmitted ? 'SUBMITTED' : 'IN_PROGRESS',
        startedAt: '2026-11-25T01:00:00Z',
        cutoffAt: '2026-11-25T01:30:00Z',
        submittedAt: isSubmitted ? '2026-11-25T01:15:00Z' : null,
        score: isSubmitted ? 100 : null,
        gradePublished: isSubmitted,
        questions: [{ id: qId, prompt: '2 + 2 = ?', type: 'SINGLE_CHOICE', points: 10, options: [{ id: o1, text: '4' }, { id: o2, text: '5' }] }],
        answers: [{ questionId: qId, selectedOptionIds: [o1], version: 2 }],
        serverTime: '2026-11-25T01:05:00Z',
        late: false,
      });
    }
    if (config.url?.endsWith('/submit')) {
      return wrap({
        id: 'att-1',
        quizId: 'quiz-1',
        studentId: 'student-1',
        status: 'SUBMITTED',
        startedAt: '2026-11-25T01:00:00Z',
        cutoffAt: '2026-11-25T01:30:00Z',
        submittedAt: '2026-11-25T01:15:00Z',
        score: 100,
        gradePublished: true,
        questions: [{ id: qId, prompt: '2 + 2 = ?', type: 'SINGLE_CHOICE', points: 10, options: [{ id: o1, text: '4' }, { id: o2, text: '5' }] }],
        answers: [{ questionId: qId, selectedOptionIds: [o1], version: 2 }],
        serverTime: '2026-11-25T01:15:00Z',
        late: false,
      });
    }
    if (config.url?.endsWith('/result')) {
      return wrap({ submitted: true, completed: true, originalScore: 80, finalScore: 95 });
    }
    if (config.method === 'get' && config.url?.endsWith('/grade-overrides')) {
      return wrap([{ id: 1, actorId: 'teacher-1', originalScore: 80, finalScore: 95, reason: 'Bonus', createdAt: '2026-11-25T02:00:00Z' }]);
    }
    return wrap(null);
  };

  // 1. List quizzes wraps Backend array into PaginatedData
  const listPage = await quizApi.list('c1', 0, 20);
  assert.equal(listPage.items.length, 1);
  assert.equal(listPage.items[0].kind, 'GRADED');
  assert.equal(listPage.items[0].timeLimitSeconds, 1800);

  // 2. Create quiz sends Backend Configure DTO
  await quizApi.create('c1', {
    title: 'Midterm Quiz',
    config: {
      kind: 'GRADED',
      maxAttempts: 2,
      startTime: '2026-11-20T00:00:00Z',
      deadline: '2026-11-30T00:00:00Z',
      allowLateAttempts: false,
      timeLimitSeconds: 1800,
      timeout: 'AUTO_SUBMIT',
      shuffleQuestions: true,
      shuffleAnswers: false,
      scoring: 'ALL_OR_NOTHING',
      completion: 'PASSING_SCORE',
      passingScore: 60,
      publication: 'MANUAL',
      showCorrectAnswers: true,
      showExplanations: true,
      questions: [
        {
          id: qId,
          text: '2 + 2 = ?',
          type: 'SINGLE_CHOICE',
          points: 10,
          explanation: 'Basic arithmetic',
          options: [
            { id: o1, text: '4', correct: true },
            { id: o2, text: '5', correct: false },
          ],
        },
      ],
    },
  });
  const createCall = calls.find((c) => c.method === 'post' && c.url === '/classrooms/c1/quizzes');
  assert.equal(createCall.body.graded, true);
  assert.equal(createCall.body.durationSeconds, 1800);
  assert.equal(createCall.body.releasePolicy, 'MANUAL');
  assert.equal(createCall.body.timeoutMode, 'AUTO_SUBMIT');
  assert.equal(createCall.body.questions[0].prompt, '2 + 2 = ?');
  assert.equal(createCall.body.questions[0].scoring, 'ALL_OR_NOTHING');

  // 3. Teacher get fetches /authoring and populates teacherConfiguration
  const detail = await quizApi.get('c1', 'quiz-1');
  assert.ok(detail.teacherConfiguration);
  assert.equal(detail.teacherConfiguration.passingScore, 60);
  assert.equal(detail.questions.length, 1);
  assert.equal(detail.questions[0].text, '2 + 2 = ?');

  // 4. Status transitions use POST /publish and POST /close
  const pub = await quizApi.changeStatus('c1', 'quiz-1', 'PUBLISHED');
  assert.equal(pub.status, 'PUBLISHED');
  assert.ok(calls.some((c) => c.method === 'post' && c.url === '/classrooms/c1/quizzes/quiz-1/publish'));

  const closed = await quizApi.changeStatus('c1', 'quiz-1', 'CLOSED');
  assert.equal(closed.status, 'CLOSED');
  assert.ok(calls.some((c) => c.method === 'post' && c.url === '/classrooms/c1/quizzes/quiz-1/close'));

  // 5. Start/resume attempt maps IN_PROGRESS -> ACTIVE, answers list -> map, and tracks version for autosave
  mockStorage.algolearner_user = JSON.stringify({ id: 'student-1', role: 'STUDENT' });
  const started = await quizApi.startAttempt('c1', 'quiz-1');
  assert.equal(started.status, 'ACTIVE');
  assert.deepEqual(started.answers[qId], [o1]);

  await quizApi.autosaveAnswer('c1', 'quiz-1', 'att-1', qId, [o2]);
  const saveCall = calls.find((c) => c.method === 'put' && c.url === '/classrooms/c1/quizzes/quiz-1/attempts/att-1/answers');
  assert.ok(saveCall);
  assert.equal(saveCall.body.questionId, qId);
  assert.deepEqual(saveCall.body.selectedOptionIds, [o2]);
  assert.equal(saveCall.body.expectedVersion, 2);
  assert.ok(saveCall.body.idempotencyKey);

  // 6. Submit + review enrichment populates correct indicator & explanation
  await quizApi.submitAttempt('c1', 'quiz-1', 'att-1');
  const reviewedAttempt = await quizApi.getAttempt('c1', 'quiz-1', 'att-1');
  assert.equal(reviewedAttempt.status, 'SUBMITTED');
  assert.equal(reviewedAttempt.questions[0].explanation, 'Basic arithmetic');
  assert.equal(reviewedAttempt.questions[0].options[0].correct, true);

  // 7. Result & Teacher attempt-rights + grade-overrides list
  const result = await quizApi.getResult('c1', 'quiz-1', 'student-1');
  assert.equal(result.originalAutoScore, 80);
  assert.equal(result.finalScore, 95);

  await quizApi.overrideAccess('c1', 'quiz-1', 'student-1', {
    resetAttempts: false,
    extraAttempts: 2,
    reason: 'Network disconnect during quiz',
  });
  const accessCall = calls.find((c) => c.method === 'post' && c.url === '/classrooms/c1/quizzes/quiz-1/attempt-rights');
  assert.deepEqual(accessCall.body, {
    studentId: 'student-1',
    action: 'GRANT',
    additionalAttempts: 2,
    reason: 'Network disconnect during quiz',
  });

  const history = await quizApi.listGradeOverrides('c1', 'quiz-1', 'student-1', 0, 20);
  assert.equal(history.items.length, 1);
  assert.equal(history.items[0].finalScore, 95);
});
