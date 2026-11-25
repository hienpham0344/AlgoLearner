/**
 * TypeScript definitions for Module 4.7 (Classroom Quiz - Teacher Scope)
 * Adheres strictly to Backend 4.7 API contract and SRS ver 1.1 requirements.
 */

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
export type ScoringPolicy = 'ALL_OR_NOTHING' | 'PARTIAL';
export type ReleasePolicy = 'MANUAL' | 'IMMEDIATE';
export type TimeoutMode = 'AUTO_SUBMIT' | 'LOCK';
export type QuizStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

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
  id: string;
  text: string;
}

export interface StudentQuestion {
  id: string;
  prompt: string;
  type: QuestionType;
  points: number;
  options: StudentOption[];
}

export interface AnswerView {
  questionId: string;
  selectedOptionIds: string[];
  version: number;
}

export interface AttemptView {
  id: string;
  quizId: string;
  status: string; // e.g., 'IN_PROGRESS', 'SUBMITTED', 'EXPIRED'
  startedAt: string;
  cutoffAt?: string | null;
  submittedAt?: string | null;
  score?: number | null;
  gradePublished: boolean;
  questions: StudentQuestion[];
  answers: AnswerView[];
  serverTime: string;
  late: boolean;
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
