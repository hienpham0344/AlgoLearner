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
      export * from './src/features/classroom/quizzes/utils/quizTimeUtils.ts';
      export * from './src/features/classroom/quizzes/utils/quizRules.ts';
      export * from './src/features/classroom/quizzes/api/quizKeys.ts';
      export * from './src/features/classroom/quizzes/api/quizApi.ts';
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
  external: ['axios', 'react', 'react/jsx-runtime', 'dompurify', 'marked'],
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
  formatDurationMinutes,
  localInputToIsoUtc,
  isoUtcToLocalInput,
  isPastDeadline,
  isBeforeStartTime,
  validateQuizConfig,
  validateQuestion,
  generateClientUuid,
  getQuizStatusBadgeConfig,
  quizKeys,
  quizError,
  quizApi,
} = module.exports;

test('FR-QUZ-001 & FR-QUZ-014: Quiz form validation rules', () => {
  // Title required and max length 255
  const emptyTitle = validateQuizConfig({
    title: '   ',
    graded: true,
    maxAttempts: 1,
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.equal(emptyTitle.title, 'Quiz title is required.');

  const longTitle = validateQuizConfig({
    title: 'A'.repeat(256),
    graded: true,
    maxAttempts: 1,
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.equal(longTitle.title, 'Quiz title cannot exceed 255 characters.');

  // Max attempts >= 1
  const zeroAttempts = validateQuizConfig({
    title: 'Valid Title',
    graded: true,
    maxAttempts: 0,
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.equal(zeroAttempts.maxAttempts, 'Maximum attempts must be at least 1.');

  // Duration must be >= 1 if provided
  const zeroDuration = validateQuizConfig({
    title: 'Valid Title',
    graded: true,
    maxAttempts: 1,
    durationSeconds: 0,
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.ok(zeroDuration.durationSeconds);

  // ClosesAt must be after OpensAt
  const invalidDates = validateQuizConfig({
    title: 'Valid Title',
    graded: true,
    maxAttempts: 1,
    opensAt: '2026-11-20T10:00:00.000Z',
    closesAt: '2026-11-20T09:00:00.000Z',
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.equal(invalidDates.closesAt, 'Closing time (deadline) must be strictly after opening time.');

  // Passing score only on graded
  const passingScoreUngraded = validateQuizConfig({
    title: 'Valid Title',
    graded: false,
    maxAttempts: 1,
    passingScore: 70,
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.equal(passingScoreUngraded.passingScore, 'Passing score is only applicable to graded quizzes.');

  // Passing score out of range
  const outOfRangeScore = validateQuizConfig({
    title: 'Valid Title',
    graded: true,
    maxAttempts: 1,
    passingScore: 105,
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.equal(outOfRangeScore.passingScore, 'Passing score must be between 0 and 100.');

  // Questions must not be empty
  const emptyQuestions = validateQuizConfig({
    title: 'Valid Title',
    graded: true,
    maxAttempts: 1,
    questions: [],
  });
  assert.equal(emptyQuestions.questions, 'Quiz must contain at least one question before publishing or saving.');

  // Fully valid quiz
  const valid = validateQuizConfig({
    title: 'Midterm Quiz',
    graded: true,
    opensAt: '2026-11-20T08:00:00.000Z',
    closesAt: '2026-11-20T10:00:00.000Z',
    durationSeconds: 3600,
    maxAttempts: 2,
    passingScore: 60,
    questions: [{ id: '1', prompt: 'Q', type: 'SINGLE_CHOICE', scoring: 'ALL_OR_NOTHING', points: 1, options: [] }],
  });
  assert.deepEqual(valid, {});
});

test('FR-QUZ-003: Question authoring and scoring policy validation', () => {
  // Empty prompt
  const emptyPrompt = validateQuestion({
    prompt: '',
    type: 'SINGLE_CHOICE',
    scoring: 'ALL_OR_NOTHING',
    points: 1,
    options: [
      { id: '1', text: 'A', correct: true },
      { id: '2', text: 'B', correct: false },
    ],
  });
  assert.equal(emptyPrompt.prompt, 'Question prompt cannot be empty.');

  // Points <= 0
  const zeroPoints = validateQuestion({
    prompt: 'Prompt',
    type: 'SINGLE_CHOICE',
    scoring: 'ALL_OR_NOTHING',
    points: 0,
    options: [
      { id: '1', text: 'A', correct: true },
      { id: '2', text: 'B', correct: false },
    ],
  });
  assert.equal(zeroPoints.points, 'Points must be a positive number greater than 0.');

  // Single choice with multiple correct answers rejected
  const singleMultiCorrect = validateQuestion({
    prompt: 'Prompt',
    type: 'SINGLE_CHOICE',
    scoring: 'ALL_OR_NOTHING',
    points: 1,
    options: [
      { id: '1', text: 'A', correct: true },
      { id: '2', text: 'B', correct: true },
    ],
  });
  assert.equal(singleMultiCorrect.correct, 'Single-choice question must have exactly one correct answer.');

  // Single choice with 0 correct answers rejected
  const singleZeroCorrect = validateQuestion({
    prompt: 'Prompt',
    type: 'SINGLE_CHOICE',
    scoring: 'ALL_OR_NOTHING',
    points: 1,
    options: [
      { id: '1', text: 'A', correct: false },
      { id: '2', text: 'B', correct: false },
    ],
  });
  assert.equal(singleZeroCorrect.correct, 'At least one correct answer must be marked.');

  // True/False with != 2 options rejected
  const tfThreeOptions = validateQuestion({
    prompt: 'Prompt',
    type: 'TRUE_FALSE',
    scoring: 'ALL_OR_NOTHING',
    points: 1,
    options: [
      { id: '1', text: 'True', correct: true },
      { id: '2', text: 'False', correct: false },
      { id: '3', text: 'Maybe', correct: false },
    ],
  });
  assert.equal(tfThreeOptions.options, 'True/False questions must have exactly 2 options.');

  // Multiple choice with at least 1 correct accepted
  const mcValid = validateQuestion({
    prompt: 'Prompt',
    type: 'MULTIPLE_CHOICE',
    scoring: 'PARTIAL',
    points: 2,
    options: [
      { id: '1', text: 'A', correct: true },
      { id: '2', text: 'B', correct: true },
      { id: '3', text: 'C', correct: false },
    ],
  });
  assert.deepEqual(mcValid, {});

  // Partial credit with custom weights: weights must sum to question points
  const partialWeightMismatch = validateQuestion({
    prompt: 'Prompt',
    type: 'MULTIPLE_CHOICE',
    scoring: 'PARTIAL',
    points: 3,
    options: [
      { id: '1', text: 'A', correct: true, weight: 1 },
      { id: '2', text: 'B', correct: true, weight: 1 }, // sum = 2 != 3
      { id: '3', text: 'C', correct: false, weight: 0 },
    ],
  });
  assert.ok(partialWeightMismatch.weights);

  // Partial credit with valid weights
  const partialWeightValid = validateQuestion({
    prompt: 'Prompt',
    type: 'MULTIPLE_CHOICE',
    scoring: 'PARTIAL',
    points: 4,
    options: [
      { id: '1', text: 'A', correct: true, weight: 2 },
      { id: '2', text: 'B', correct: true, weight: 2 },
      { id: '3', text: 'C', correct: false, weight: 0 },
    ],
  });
  assert.deepEqual(partialWeightValid, {});
});

test('FR-QUZ-007: Quiz status badge configuration and transitions', () => {
  const draftConfig = getQuizStatusBadgeConfig('DRAFT');
  assert.equal(draftConfig.label, 'Draft');
  assert.ok(draftConfig.badgeClass.includes('amber'));

  const pubConfig = getQuizStatusBadgeConfig('PUBLISHED');
  assert.equal(pubConfig.label, 'Published');
  assert.ok(pubConfig.badgeClass.includes('emerald'));

  const closedConfig = getQuizStatusBadgeConfig('CLOSED');
  assert.equal(closedConfig.label, 'Closed');
  assert.ok(closedConfig.badgeClass.includes('slate'));
});

test('Time utilities and Vietnam UTC+7 timezone conversion', () => {
  // ISO UTC to Vietnam Time (UTC+7): 2026-11-20T08:30:00Z -> 20/11/2026, 15:30
  const formatted = formatVietnamDateTime('2026-11-20T08:30:00Z');
  assert.ok(formatted.includes('20/11/2026') || formatted.includes('2026'));
  assert.ok(formatted.includes('15:30'));

  // Duration formatting
  assert.equal(formatDurationMinutes(null), 'No limit');
  assert.equal(formatDurationMinutes(undefined), 'No limit');
  assert.equal(formatDurationMinutes(30), '30s');
  assert.equal(formatDurationMinutes(300), '5 min');
  assert.equal(formatDurationMinutes(3600), '60 min');
  assert.equal(formatDurationMinutes(3690), '61m 30s');

  // Input conversion round-trip
  const iso = '2026-11-20T15:30:00.000Z';
  const local = isoUtcToLocalInput(iso);
  assert.ok(local.length >= 16);
  const backToUtc = localInputToIsoUtc(local);
  assert.ok(backToUtc?.endsWith('Z'));
});

test('Query Key Factory ensures hierarchical scoping', () => {
  const classId = 'c123';
  const quizId = 'q456';
  const studentId = 's789';

  assert.deepEqual(quizKeys.all, ['quizzes']);
  assert.deepEqual(quizKeys.list(classId), ['quizzes', 'list', 'c123']);
  assert.deepEqual(quizKeys.detail(classId, quizId), ['quizzes', 'detail', 'c123', 'q456']);
  assert.deepEqual(quizKeys.authoring(classId, quizId), ['quizzes', 'authoring', 'c123', 'q456']);
  assert.deepEqual(quizKeys.attemptHistory(classId, quizId), ['quizzes', 'attempts', 'c123', 'q456', 'all']);
  assert.deepEqual(quizKeys.attemptHistory(classId, quizId, studentId), ['quizzes', 'attempts', 'c123', 'q456', 's789']);
  assert.deepEqual(quizKeys.gradeHistory(classId, quizId, studentId), ['quizzes', 'grade-overrides', 'c123', 'q456', 's789']);
});

test('quizError extracts meaningful status messages', () => {
  const err400 = { isAxiosError: true, response: { status: 400 } };
  assert.equal(quizError(err400), 'Invalid quiz configuration or request data.');

  const err401 = { isAxiosError: true, response: { status: 401 } };
  assert.equal(quizError(err401), 'Session expired. Please sign in again.');

  const err403 = { isAxiosError: true, response: { status: 403 } };
  assert.equal(quizError(err403), 'You do not have permission to manage quizzes in this classroom.');

  const err404 = { isAxiosError: true, response: { status: 404 } };
  assert.equal(quizError(err404), 'Quiz or classroom not found.');

  const err409 = { isAxiosError: true, response: { status: 409 } };
  assert.equal(quizError(err409), 'Conflict: Quiz status does not allow this operation.');

  const customBackendMsg = {
    isAxiosError: true,
    response: { status: 409, data: { success: false, message: 'QUIZ_NOT_DRAFT' } },
  };
  assert.equal(quizError(customBackendMsg), 'QUIZ_NOT_DRAFT');
});
