/**
 * TypeScript definitions for Module 4.7 (Classroom Quiz - Full Scope)
 * Adheres strictly to Backend 4.7 API contract (QuizDtos.java) and SRS ver 1.1 requirements.
 */

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
export type ScoringPolicy = 'ALL_OR_NOTHING' | 'PARTIAL';
export type ReleasePolicy = 'MANUAL' | 'IMMEDIATE';
export type TimeoutMode = 'AUTO_SUBMIT' | 'LOCK';
export type QuizStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'LOCKED';

export interface Option {
  id: string; // UUID
  text: string;
  correct: boolean;
  weight?: number | null; // Used for PARTIAL scoring
}

export interface Question {
  id: string; // UUID
  prompt: string;
  type: QuestionType;
  scoring: ScoringPolicy;
  points: number;
  options: Option[];
  explanation?: string | null;
}

export interface QuizConfigureRequest {
  title: string;
  graded: boolean;
  opensAt?: string | null; // ISO-8601 UTC
  closesAt?: string | null; // ISO-8601 UTC
  durationSeconds?: number | null; // null or >= 1
  maxAttempts: number; // >= 1
  allowLate: boolean;
  shuffleQuestions: boolean;
  shuffleAnswers: boolean;
  releasePolicy: ReleasePolicy;
  timeoutMode: TimeoutMode;
  questions: Question[];
  showCorrectAnswers: boolean;
  showExplanations: boolean;
  passingScore?: number | null; // 0..100 or null
}

export interface QuizView {
  id: string; // UUID
  classroomId: string; // UUID
  title: string;
  status: QuizStatus;
  graded: boolean;
  opensAt?: string | null;
  closesAt?: string | null;
  durationSeconds?: number | null;
  maxAttempts: number;
  allowLate: boolean;
  releasePolicy: ReleasePolicy;
}

export interface StudentOption {
  id: string; // UUID
  text: string;
}

export interface StudentQuestion {
  id: string; // UUID
  prompt: string;
  type: QuestionType;
  points: number;
  options: StudentOption[];
}

export interface AnswerView {
  questionId: string; // UUID
  selectedOptionIds: string[]; // UUIDs
  version: number;
}

export interface AttemptView {
  id: string; // UUID
  quizId: string; // UUID
  status: string; // 'IN_PROGRESS' | 'SUBMITTED' | 'LOCKED'
  startedAt: string; // ISO-8601 UTC
  cutoffAt?: string | null; // ISO-8601 UTC
  submittedAt?: string | null; // ISO-8601 UTC
  score?: number | null; // Only populated when published or for teacher
  gradePublished: boolean;
  questions: StudentQuestion[];
  answers: AnswerView[];
  serverTime: string; // ISO-8601 UTC for clock drift compensation
  late: boolean;
}

export interface SaveAnswerRequest {
  questionId: string; // UUID
  selectedOptionIds: string[]; // UUIDs
  expectedVersion: number; // >= 0
  idempotencyKey: string; // UUID
}

export interface SaveReceipt {
  accepted: boolean;
  attemptStatus: string; // 'IN_PROGRESS' | 'SUBMITTED' | 'LOCKED'
  questionId: string; // UUID
  version: number;
}

export interface ReviewQuestion {
  questionId: string; // UUID
  correctOptionIds?: string[] | null; // Only present if showCorrectAnswers
  explanation?: string | null; // Only present if showExplanations
}

export interface AdjustAttemptsRequest {
  studentId: string;
  action: 'GRANT' | 'RESET';
  additionalAttempts: number;
  reason: string;
}

export interface GradeOverrideRequest {
  score: number; // 0..100
  reason: string;
}

export interface GradeChange {
  id: number;
  actorId: string;
  originalScore?: number | null;
  finalScore: number;
  reason: string;
  createdAt: string;
}

export interface QuizResult {
  submitted: boolean;
  completed: boolean;
  originalScore?: number | null;
  finalScore?: number | null;
}
