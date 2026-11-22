import type {
  AssignmentType,
  CodeFeedbackLevel,
  EssayFormat,
  GradePublishMode,
  GradingPolicy,
  QuestionType,
  ScoringPolicy,
  TestCaseSnapshot,
  TimeExpiredAction,
  ViewAnswersMode,
} from './assignment';

export interface CreateAssignmentRequest {
  title: string;
  description?: string;
  type: AssignmentType;
  startTime?: string | null; // ISO-8601 UTC
  deadline?: string | null; // ISO-8601 UTC
  allowLateSubmission?: boolean;
  latePenaltyPercent?: number | null;
  durationMinutes?: number | null;
  timeExpiredAction?: TimeExpiredAction;
  maxAttempts?: number;
  scoringPolicy?: ScoringPolicy;
  shuffleQuestions?: boolean;
  shuffleAnswers?: boolean;
  gradePublishMode?: GradePublishMode;
  viewAnswersMode?: ViewAnswersMode;
  codeFeedbackLevel?: CodeFeedbackLevel;
}

export type UpdateAssignmentRequest = CreateAssignmentRequest;

export interface OptionRequest {
  content: string;
  isCorrect: boolean;
}

export interface AddQuestionRequest {
  questionType: QuestionType;
  title: string;
  prompt: string;
  points: number;
  gradingPolicy?: GradingPolicy;
  essayFormat?: EssayFormat;
  explanation?: string;
  codeTemplate?: string;
  codeLanguage?: string;
  options?: OptionRequest[];
  publicProblemId?: string | null;
  testCases?: TestCaseSnapshot[];
  questionWeight?: number;
  codeCaseWeights?: number[];
}

export interface SaveDraftAnswerRequest {
  questionId: string;
  selectedOptionIds?: string;
  textAnswer?: string;
  fileUrl?: string;
  codeSubmission?: string;
  expectedVersion?: number;
  clientMutationId?: string;
  fileId?: string | null;
  selectedOptions?: string[];
}

export interface AttemptGrantRequest {
  extraAttempts: number;
  reset: boolean;
  reason: string;
}

export interface GradeEssayRequest {
  questionId: string;
  score: number;
  feedback?: string;
}

export interface OverrideGradeRequest {
  overriddenScore: number;
  reason: string;
}

export interface RejudgeRequest {
  reason?: string;
  target?: 'ORIGINAL' | 'LATEST_PUBLISHED';
}

export interface CodeCaseWeightsRequest {
  weights: number[];
  reason: string;
}

export interface RunCodeRequest {
  questionId: string;
  source: string;
}
