/**
 * AlgoLearner Module 4.7 — Classroom Quiz Types
 * Strictly aligned with Backend SRS 4.7 Contract (com.algolearner.platform.quiz.QuizDtos)
 */

export type QuizKind = 'GRADED' | 'UNGRADED';
export type QuizStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';
export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
export type ScoringMode = 'ALL_OR_NOTHING' | 'PARTIAL_CREDIT';
export type TimeoutMode = 'AUTO_SUBMIT' | 'LOCK_WITHOUT_SUBMIT';
export type CompletionMode = 'SUBMIT_IS_ENOUGH' | 'PASSING_SCORE';
export type PublicationMode = 'AUTO' | 'MANUAL';
export type AttemptStatus = 'ACTIVE' | 'SUBMITTED' | 'EXPIRED';

/**
 * Quiz option configuration (Teacher authoring & stored entity)
 */
export interface QuizOption {
  id: string; // UUID
  text: string; // max 4000 chars
  correct: boolean;
  weight?: number | null; // Required if scoring === 'PARTIAL_CREDIT'; sum of correct weights must equal question points
}

/**
 * Quiz question configuration (Teacher authoring)
 */
export interface QuizQuestion {
  id: string; // UUID
  text: string; // max 20000 chars
  type: QuestionType;
  points: number; // 0.01 to 1000
  explanation?: string | null; // max 20000 chars
  options: QuizOption[]; // 2 to 20 options
}

/**
 * Full Quiz configuration
 */
export interface QuizConfig {
  kind: QuizKind;
  maxAttempts: number; // 1 to 10000
  startTime: string | null; // ISO-8601 UTC
  deadline: string | null; // ISO-8601 UTC
  allowLateAttempts: boolean;
  timeLimitSeconds: number | null; // 1 to 604800 (or null if untimed)
  timeout: TimeoutMode;
  shuffleQuestions: boolean;
  shuffleAnswers: boolean;
  scoring: ScoringMode;
  completion: CompletionMode;
  passingScore: number | null; // 0 to 100 (required if completion === 'PASSING_SCORE')
  publication: PublicationMode;
  showCorrectAnswers: boolean;
  showExplanations: boolean;
  questions: QuizQuestion[]; // 1 to 200 questions
}

/**
 * Sanitized option view (Student receives without correct indicator unless released/teacher)
 */
export interface OptionView {
  id: string;
  text: string;
  correct?: boolean | null; // Only present for teacher or when released
}

/**
 * Sanitized question view
 */
export interface QuestionView {
  id: string;
  text: string;
  type: QuestionType;
  points: number;
  explanation?: string | null; // Only present for teacher or when released
  options: OptionView[];
}

/**
 * Quiz view model returned by GET /classrooms/{classroomId}/quizzes and /{quizId}
 */
export interface QuizView {
  id: string;
  classroomId: string;
  title: string;
  status: QuizStatus;
  teacherConfiguration: QuizConfig | null; // null for student
  kind: QuizKind;
  maxAttempts: number;
  startTime: string | null; // ISO-8601 UTC
  deadline: string | null; // ISO-8601 UTC
  timeLimitSeconds: number | null;
  questions: QuestionView[]; // Teacher gets all questions; student gets empty on GET /quiz
}

/**
 * Attempt view model returned by start, resume, autosave, submit, list attempts
 */
export interface AttemptView {
  id: string;
  studentId: string;
  number: number;
  status: AttemptStatus;
  serverTime: string; // ISO-8601 UTC
  startedAt: string; // ISO-8601 UTC
  deadline: string | null; // Calculated server deadline ISO-8601 UTC
  submittedAt: string | null; // ISO-8601 UTC
  late: boolean;
  score: number | null; // null if not released or UNGRADED
  completed: boolean | null; // null if not released
  questions: QuestionView[]; // Student attempt snapshot in persisted (shuffled) order
  answers: Record<string, string[]>; // questionId -> list of selected optionIds
}

/**
 * Student official result summary
 */
export interface QuizResult {
  studentId: string;
  originalAutoScore: number | null;
  finalScore: number | null;
  completed: boolean | null;
  attemptsUsed: number;
  attemptsAllowed: number;
}

/**
 * Audit log entry for teacher grade override
 */
export interface GradeChange {
  id: string;
  actorId: string;
  originalScore: number;
  finalScore: number;
  reason: string;
  createdAt: string; // ISO-8601 UTC
}

// Request Payloads

export interface CreateQuizRequest {
  title: string;
  config: QuizConfig;
}

export interface UpdateQuizRequest {
  title: string;
  config: QuizConfig;
}

export interface ChangeQuizStatusRequest {
  status: QuizStatus;
}

export interface PublishGradesRequest {
  published: boolean;
}

export interface SaveAnswerRequest {
  optionIds: string[];
}

export interface AccessOverrideRequest {
  resetAttempts: boolean;
  extraAttempts: number;
  deadline: string | null;
  timeLimitSeconds: number | null;
}

export interface GradeOverrideRequest {
  score: number;
  reason: string;
}

// UI State & Helper Types

export type AutosaveStatus = 'idle' | 'saving' | 'saved' | 'error';

export interface AnswerState {
  selectedOptionIds: string[];
  status: AutosaveStatus;
  lastSavedAt?: number;
  errorMessage?: string;
}

export interface QuestionValidationResult {
  isValid: boolean;
  errors: Record<string, string>;
}

export interface QuizFormValidationResult {
  isValid: boolean;
  errors: Record<string, string>;
}
