/**
 * Domain types and View Models for Module 4.6 (Classroom Assignments)
 * Matches Spring Boot backend DTOs (com.algolearner.platform.assignment.AssignmentDtos)
 */

export type AssignmentType = 'GRADED' | 'UNGRADED';
export type AssignmentStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'CLOSED';
export type TimeExpiredAction = 'AUTO_SUBMIT' | 'LOCK_WITHOUT_SUBMIT';
export type ScoringPolicy = 'HIGHEST' | 'LATEST';
export type GradePublishMode = 'IMMEDIATE' | 'AFTER_DEADLINE' | 'MANUAL';
export type ViewAnswersMode = 'ALWAYS' | 'AFTER_DEADLINE' | 'AFTER_SUBMIT' | 'NEVER';
export type CodeFeedbackLevel = 'PASS_FAIL' | 'TESTCASE_STATUS' | 'FULL_DETAILS';

export type QuestionType = 'CODE' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'ESSAY';
export type GradingPolicy = 'ALL_OR_NOTHING' | 'PARTIAL_CREDIT';
export type EssayFormat = 'TEXT_ONLY' | 'FILE_ONLY' | 'TEXT_AND_FILE';

export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'EVALUATED' | 'TIMED_OUT' | 'LOCKED';

export interface OptionView {
  id: string;
  content: string;
  isCorrect?: boolean; // Only present for teacher authoring / after answers released
}

export interface TestCaseSnapshot {
  input: string;
  expectedOutput: string;
  sample: boolean;
  allowDetails?: boolean;
}

export interface QuestionView {
  id: string;
  questionType: QuestionType;
  title: string;
  prompt: string;
  points: number;
  displayOrder: number;
  gradingPolicy: GradingPolicy;
  essayFormat?: EssayFormat;
  codeTemplate?: string;
  codeLanguage?: string;
  options: OptionView[];
  explanation?: string;
  questionWeight: number;
  codeCaseWeights?: number[];
  testCases?: TestCaseSnapshot[]; // Scoped by permission: students only see sample testcases
}

export interface AnswerView {
  id: string;
  questionId: string;
  selectedOptionIds?: string;
  textAnswer?: string;
  fileUrl?: string;
  codeSubmission?: string;
  isDraft: boolean;
  score?: number | null;
  feedback?: string | null;
  version: number;
  fileId?: string | null;
}

export interface CodeJobView {
  questionId: string;
  jobId: string;
  generation: number;
}

export interface AttemptView {
  id: string;
  assignmentId: string;
  studentId: string;
  studentName?: string;
  attemptNumber: number;
  status: AttemptStatus;
  startedAt: string; // ISO-8601 UTC string
  submittedAt?: string | null; // ISO-8601 UTC string
  isLate: boolean;
  autoScore?: number | null;
  manualScore?: number | null;
  overriddenScore?: number | null;
  finalScore?: number | null;
  overrideReason?: string | null;
  teacherFeedback?: string | null;
  remainingSeconds?: number | null;
  answers: AnswerView[];
  revisionId?: string | null;
  snapshotProvenance?: string | null;
  questions?: QuestionView[];
  codeJobs?: CodeJobView[];
}

export interface AssignmentSummaryView {
  id: string;
  classroomId: string;
  title: string;
  type: AssignmentType;
  status: AssignmentStatus;
  startTime?: string | null; // ISO-8601 UTC string
  deadline?: string | null; // ISO-8601 UTC string
  durationMinutes?: number | null;
  maxAttempts: number; // 0 means unlimited
  isGradePublished?: boolean;
  myRemainingAttempts?: number | null;
  myBestScore?: number | null;
  createdAt: string;
}

export interface AssignmentDetailView {
  id: string;
  classroomId: string;
  title: string;
  description?: string;
  type: AssignmentType;
  status: AssignmentStatus;
  startTime?: string | null;
  deadline?: string | null;
  allowLateSubmission: boolean;
  latePenaltyPercent?: number | null;
  durationMinutes?: number | null;
  timeExpiredAction: TimeExpiredAction;
  maxAttempts: number;
  scoringPolicy: ScoringPolicy;
  shuffleQuestions: boolean;
  shuffleAnswers: boolean;
  gradePublishMode: GradePublishMode;
  isGradePublished: boolean;
  viewAnswersMode: ViewAnswersMode;
  codeFeedbackLevel: CodeFeedbackLevel;
  questions: QuestionView[];
  myRemainingAttempts?: number | null;
  myBestScore?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface TestCaseResult {
  passed: boolean;
  verdict: string;
  timeMs?: number;
  memoryKb?: number;
  input?: string;
  expectedOutput?: string;
  actualOutput?: string;
  errorMessage?: string;
}

export interface EvaluationResult {
  jobId: string;
  generation: number;
  verdict: string; // ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, COMPILATION_ERROR, RUNTIME_ERROR
  passed: number;
  total: number;
  executionTimeMs?: number;
  memoryUsedKb?: number;
  cases?: TestCaseResult[];
  compilerOutput?: string;
}

export interface EvaluationPart {
  partType: string;
  questionId: string;
  score: number;
  maxScore: number;
  verdict?: string;
  details?: string;
}

export interface EvaluationHistoryItem {
  generation: number;
  createdAt: string;
  totalScore: number;
  evaluatorType: 'AUTOMATED' | 'TEACHER' | 'OVERRIDE' | 'REJUDGE';
  evaluatorId?: string;
  reason?: string;
  parts: EvaluationPart[];
}
