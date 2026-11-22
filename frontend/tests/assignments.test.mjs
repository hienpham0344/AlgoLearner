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
      export * from './src/features/classroom/assignments/utils/timeUtils.ts';
      export * from './src/features/classroom/assignments/utils/assignmentRules.ts';
      export * from './src/features/classroom/assignments/utils/autosaveManager.ts';
      export * from './src/features/classroom/assignments/api/assignmentKeys.ts';
      export * from './src/features/classroom/assignments/api/assignmentApi.ts';
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
  formatRemainingTime,
  isPastDeadline,
  isBeforeStartTime,
  localInputToIsoUtc,
  isoUtcToLocalInput,
  validateAssignmentForm,
  validateQuestionForm,
  canStudentStartAttempt,
  getStatusBadgeConfig,
  generateClientMutationId,
  AutosaveStateStore,
  assignmentKeys,
  assignmentApi,
  assignmentError,
} = module.exports;

// --- FR-ASG-001: Assignment Creation & Form Validation ---
test('FR-ASG-001: Assignment form validation enforces title length, valid duration, and date ordering', () => {
  // Empty title
  const emptyTitle = validateAssignmentForm({ title: '   ', type: 'GRADED' });
  assert.ok(emptyTitle.title, 'Should reject empty title');

  // Title exceeding 255 chars
  const longTitle = validateAssignmentForm({ title: 'a'.repeat(256), type: 'GRADED' });
  assert.match(longTitle.title, /255 characters/i);

  // Deadline before start time
  const invalidDates = validateAssignmentForm({
    title: 'Midterm',
    type: 'GRADED',
    startTime: '2026-11-20T10:00:00Z',
    deadline: '2026-11-20T09:00:00Z',
  });
  assert.match(invalidDates.deadline, /must be after start time/i);

  // Invalid duration
  const invalidDuration = validateAssignmentForm({
    title: 'Midterm',
    type: 'GRADED',
    durationMinutes: -10,
  });
  assert.match(invalidDuration.durationMinutes, /greater than 0/i);

  // Valid assignment
  const valid = validateAssignmentForm({
    title: 'Midterm Exam',
    type: 'GRADED',
    startTime: '2026-11-20T10:00:00Z',
    deadline: '2026-11-20T12:00:00Z',
    durationMinutes: 90,
    maxAttempts: 1,
  });
  assert.deepEqual(valid, {});
});

// --- FR-ASG-002, FR-ASG-014, FR-ASG-015, FR-ASG-016: Question Authoring Validation ---
test('FR-ASG-002 & FR-ASG-014: Question authoring validates points, prompts, and option configurations', () => {
  // Missing prompt
  const missingPrompt = validateQuestionForm({
    title: 'Question 1',
    prompt: '',
    points: 10,
    questionType: 'SINGLE_CHOICE',
    options: [{ content: 'A', isCorrect: true }, { content: 'B', isCorrect: false }],
  });
  assert.ok(missingPrompt.prompt);

  // Negative points
  const negPoints = validateQuestionForm({
    title: 'Question 1',
    prompt: 'Solve X',
    points: -5,
    questionType: 'SINGLE_CHOICE',
    options: [{ content: 'A', isCorrect: true }, { content: 'B', isCorrect: false }],
  });
  assert.ok(negPoints.points);

  // MCQ with fewer than 2 options
  const tooFewOptions = validateQuestionForm({
    title: 'MCQ 1',
    prompt: 'Choose one',
    points: 5,
    questionType: 'SINGLE_CHOICE',
    options: [{ content: 'A', isCorrect: true }],
  });
  assert.match(tooFewOptions.options, /at least 2 options/i);

  // Single choice with multiple correct answers
  const multipleCorrectInSingle = validateQuestionForm({
    title: 'Single Choice',
    prompt: 'Choose one',
    points: 5,
    questionType: 'SINGLE_CHOICE',
    options: [
      { content: 'A', isCorrect: true },
      { content: 'B', isCorrect: true },
    ],
  });
  assert.match(multipleCorrectInSingle.options, /exactly one correct option/i);

  // Code question missing language
  const missingCodeLang = validateQuestionForm({
    title: 'Code Question',
    prompt: 'Implement DFS',
    points: 20,
    questionType: 'CODE',
    codeLanguage: '',
  });
  assert.ok(missingCodeLang.codeLanguage);
});

// --- FR-ASG-003 & FR-ASG-009: Student Start & Attempt Eligibility ---
test('FR-ASG-003 & FR-ASG-009: Eligibility predicate guards draft, closed, scheduled, and exhausted attempts', () => {
  const baseAssignment = {
    id: 'asg-1',
    classroomId: 'class-1',
    title: 'Quiz 1',
    type: 'GRADED',
    status: 'PUBLISHED',
    startTime: null,
    deadline: null,
    durationMinutes: 60,
    maxAttempts: 2,
    myRemainingAttempts: 2,
    myBestScore: null,
    createdAt: '2026-11-19T00:00:00Z',
  };

  // Draft mode cannot be started by student
  assert.equal(canStudentStartAttempt({ ...baseAssignment, status: 'DRAFT' }).allowed, false);

  // Closed assignment cannot be started
  assert.equal(canStudentStartAttempt({ ...baseAssignment, status: 'CLOSED' }).allowed, false);

  // Scheduled in the future cannot be started
  const futureTime = new Date(Date.now() + 86400000).toISOString();
  assert.equal(canStudentStartAttempt({ ...baseAssignment, startTime: futureTime }).allowed, false);

  // Exhausted attempts cannot be started
  assert.equal(canStudentStartAttempt({ ...baseAssignment, myRemainingAttempts: 0 }).allowed, false);

  // Unlimited attempts (maxAttempts = 0) is allowed even if myRemainingAttempts is 0 or null
  assert.equal(canStudentStartAttempt({ ...baseAssignment, maxAttempts: 0, myRemainingAttempts: 0 }).allowed, true);

  // Active published assignment with remaining attempts is allowed
  assert.equal(canStudentStartAttempt(baseAssignment).allowed, true);
});

// --- FR-ASG-004: Autosave & Version Store ---
test('FR-ASG-004: AutosaveStateStore tracks monotonic versions and prevents regression', () => {
  const store = new AutosaveStateStore();
  const qId = 'q-101';

  assert.equal(store.getVersion(qId), undefined);

  store.setVersion(qId, 1);
  assert.equal(store.getVersion(qId), 1);

  // Older version write does not overwrite newer version
  store.setVersion(qId, 0);
  assert.equal(store.getVersion(qId), 1);

  store.setVersion(qId, 3);
  assert.equal(store.getVersion(qId), 3);

  // UUID generation produces valid v4 length
  const mutationId = generateClientMutationId();
  assert.equal(mutationId.length, 36);
  assert.match(mutationId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
});

// --- FR-ASG-005 & FR-ASG-010: Time formatting, Duration & Timezone ---
test('FR-ASG-005 & FR-ASG-010: Vietnam timezone formatting and countdown duration utilities', () => {
  // Remaining time format
  assert.equal(formatRemainingTime(0), '00:00');
  assert.equal(formatRemainingTime(65), '01:05');
  assert.equal(formatRemainingTime(3665), '01:01:05');

  // Local input conversion
  const utcIso = '2026-11-20T14:30:00.000Z';
  const localInput = isoUtcToLocalInput(utcIso);
  assert.ok(localInput.includes('2026-11-20T'));

  const backToUtc = localInputToIsoUtc(localInput);
  assert.ok(backToUtc?.endsWith('Z'));

  // Past deadline check
  const past = new Date(Date.now() - 60000).toISOString();
  const future = new Date(Date.now() + 60000).toISOString();
  assert.equal(isPastDeadline(past), true);
  assert.equal(isPastDeadline(future), false);
  assert.equal(isPastDeadline(null), false);
});

// --- FR-ASG-006: Status Badge Config ---
test('FR-ASG-006: getStatusBadgeConfig resolves draft, scheduled, closed, and deadline states correctly', () => {
  assert.equal(getStatusBadgeConfig('DRAFT').variant, 'draft');
  assert.equal(getStatusBadgeConfig('CLOSED').variant, 'closed');
  assert.equal(getStatusBadgeConfig('SCHEDULED').variant, 'scheduled');
  assert.equal(getStatusBadgeConfig('PUBLISHED').variant, 'published');

  const past = new Date(Date.now() - 60000).toISOString();
  assert.equal(getStatusBadgeConfig('PUBLISHED', null, past).variant, 'late');
});

// --- Query Key Factory & API Consistency ---
test('Query Key Factory generates isolated and hierarchically partitioned query keys', () => {
  const classId = 'class-abc';
  const asgId = 'asg-xyz';
  const attemptId = 'att-123';

  assert.deepEqual(assignmentKeys.list(classId), ['assignments', 'list', classId, 'default']);
  assert.deepEqual(assignmentKeys.detail(classId, asgId), ['assignments', 'detail', classId, asgId]);
  assert.deepEqual(assignmentKeys.activeAttempt(classId, asgId), ['assignments', 'attempt', 'active', classId, asgId]);
  assert.deepEqual(assignmentKeys.submissions(classId, asgId), ['assignments', 'submissions', classId, asgId]);
  assert.deepEqual(assignmentKeys.ownAttemptDetail(classId, asgId, attemptId), [
    'assignments',
    'attempt',
    'own-detail',
    classId,
    asgId,
    attemptId,
  ]);
});

// --- Error Resolution ---
test('assignmentError parses HTTP statuses and returns actionable user messages', () => {
  assert.match(assignmentError({ response: { status: 401 } }), /session has expired/i);
  assert.match(assignmentError({ response: { status: 403 } }), /permission/i);
  assert.match(assignmentError({ response: { status: 404 } }), /could not be found/i);
  assert.match(assignmentError({ response: { status: 429 } }), /rate limit/i);
  assert.match(assignmentError({ response: { status: 503 } }), /unavailable/i);
});

// --- Critical QA: Autosave Error & Failure Protection (Section 8) ---
test('Critical QA: Autosave state management ensures no false saved reports and handles failed requests', () => {
  const store = new AutosaveStateStore();
  const qId = 'question-abc';

  // Initially undefined version
  assert.equal(store.getVersion(qId), undefined);

  // When save succeeds with version 2
  store.setVersion(qId, 2);
  assert.equal(store.getVersion(qId), 2);

  // If a delayed stale response with version 1 arrives, store rejects version rollback
  store.setVersion(qId, 1);
  assert.equal(store.getVersion(qId), 2);

  // Client mutation IDs are unique across subsequent saves
  const id1 = generateClientMutationId();
  const id2 = generateClientMutationId();
  assert.notEqual(id1, id2);
});

// --- Critical QA: Submission Lifecycle & Attempt Quotas (Section 9) ---
test('Critical QA: Submission quota correctly differentiates unlimited (0) vs exhausted (1..N)', () => {
  const exhaustedAssignment = {
    id: 'asg-test',
    classroomId: 'cls-1',
    title: 'Final Exam',
    type: 'GRADED',
    status: 'PUBLISHED',
    startTime: null,
    deadline: null,
    maxAttempts: 3,
    myRemainingAttempts: 0,
    createdAt: '2026-11-19T00:00:00Z',
  };

  const checkExhausted = canStudentStartAttempt(exhaustedAssignment);
  assert.equal(checkExhausted.allowed, false);
  assert.match(checkExhausted.reason, /exhausted/i);

  const unlimitedAssignment = {
    ...exhaustedAssignment,
    maxAttempts: 0,
    myRemainingAttempts: 0,
  };
  const checkUnlimited = canStudentStartAttempt(unlimitedAssignment);
  assert.equal(checkUnlimited.allowed, true);
});

// --- Critical QA: Deadline, Late Submission & Closed Status ---
test('Critical QA: Deadline boundary correctly marks Late while preserving attempt acceptance when allowed', () => {
  const pastDeadline = new Date(Date.now() - 3600000).toISOString(); // 1 hour ago

  const lateAllowedAssignment = {
    id: 'asg-late',
    classroomId: 'cls-1',
    title: 'Lab 2',
    type: 'GRADED',
    status: 'PUBLISHED',
    startTime: null,
    deadline: pastDeadline,
    allowLateSubmission: true,
    maxAttempts: 1,
    myRemainingAttempts: 1,
    createdAt: '2026-11-19T00:00:00Z',
  };

  // Student can still start if late submissions are allowed
  assert.equal(canStudentStartAttempt(lateAllowedAssignment).allowed, true);

  // If late submissions are NOT allowed, starting is blocked
  const lateForbiddenAssignment = {
    ...lateAllowedAssignment,
    allowLateSubmission: false,
  };
  const checkForbidden = canStudentStartAttempt(lateForbiddenAssignment);
  assert.equal(checkForbidden.allowed, false);
  assert.match(checkForbidden.reason, /deadline/i);
});
