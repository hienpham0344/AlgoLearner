export type PracticeScope = 'classroom' | 'public';

export type PracticeLanguage = 'JAVA' | 'PYTHON';

export type PracticeProblemType = 'STANDARD_IO' | 'FUNCTION_METHOD';

export type PracticePublicationStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export type PracticeCompletionStatus = 'COMPLETED' | 'NOT_COMPLETED';

export type PracticeVerdict =
  | 'QUEUED'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILATION_ERROR'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'OUTPUT_LIMIT_EXCEEDED'
  | 'INTERNAL_ERROR'
  | 'INFRASTRUCTURE_ERROR';

/** SRS 4.8 Classroom Practice summary returned by GET /api/v1/classrooms/{classroomId}/practices */
export interface ClassPracticeSummary {
  id: string;
  classroomId: string;
  lessonId: string | null;
  title: string;
  problemType: PracticeProblemType;
  status: PracticePublicationStatus;
  available: boolean;
  allowedLanguages: PracticeLanguage[];
  timeLimitMs: number;
  memoryLimitMb: number;
  completionStatus: PracticeCompletionStatus | null;
  createdAt: string;
  updatedAt: string;
}

export interface SampleTestcase {
  id: string;
  input: string;
  expectedOutput: string;
  orderIndex: number;
}

/** SRS 4.8 Classroom Practice detail returned by GET /api/v1/classrooms/{classroomId}/practices/{practiceId} */
export interface ClassPracticeDetail extends ClassPracticeSummary {
  description: string;
  starterCode: Partial<Record<PracticeLanguage, string>>;
  testcaseVersion: number;
  sampleTestcases: SampleTestcase[];
}

export interface RunCaseResult {
  index: number;
  input: string;
  expectedOutput: string;
  verdict: PracticeVerdict;
  stdout: string;
  stderr: string;
  runtimeMs: number | null;
}

export interface RunResult {
  verdict: PracticeVerdict;
  cases: RunCaseResult[];
}

/** SRS 4.8 Official submission returned by POST .../submit and GET .../submissions/me */
export interface PracticeSubmission {
  id: string;
  practiceId: string;
  studentId: string;
  studentUsername: string;
  language: PracticeLanguage;
  code: string;
  testcaseVersion: number;
  verdict: PracticeVerdict;
  testsPassed: number;
  totalTests: number;
  runtimeMs: number | null;
  memoryKb: number | null;
  rejudgeStatus: string | null;
  submittedAt: string;
}

/** SRS 4.12 Public Problem summary returned by GET /api/v1/problems */
export interface PublicProblemSummary {
  id: string;
  title: string;
  status: string;
  createdAt: string;
}

/** SRS 4.12 Public Problem detail returned by GET /api/v1/problems/{id} */
export interface PublicProblemDetail {
  id: string;
  revisionId: string;
  title: string;
  statement: string;
  languages: PracticeLanguage[];
  samples: Array<{
    input: string;
    expectedOutput: string;
    sample: boolean;
    allowDetails: boolean;
  }>;
  limits: {
    compileSeconds: number;
    runtimeMillis: number;
    outputBytes: number;
  };
  status: string;
}

/** SRS 4.12 Public Judge Job result returned by GET /api/v1/problems/{id}/jobs/{job} */
export interface PublicEvaluationResult {
  jobId: string;
  generation: number;
  verdict: PracticeVerdict;
  passed: number;
  total: number;
  cases: Array<{
    index: number;
    verdict: PracticeVerdict;
    input: string | null;
    expectedOutput: string | null;
    actualOutput: string | null;
  }>;
}

/** SRS 4.12 Public Submission summary returned by GET /api/v1/problems/{id}/submissions */
export interface PublicSubmissionRecord {
  jobId: string;
  submissionId: string;
  status: PracticeVerdict;
  submittedAt: string;
}

export interface PublicPracticeStatistics {
  solved: number;
  currentStreak: number;
  highestStreak: number;
}

/** Normalized item for the Practice Studio library table & Problem Inspector */
export interface PracticeLibraryItem {
  id: string;
  scope: PracticeScope;
  classroomId: string | null;
  classroomName: string | null;
  lessonId: string | null;
  title: string;
  problemType: PracticeProblemType | null;
  allowedLanguages: PracticeLanguage[];
  timeLimitMs: number | null;
  memoryLimitMb: number | null;
  available: boolean;
  completionStatus: 'COMPLETED' | 'IN_PROGRESS' | 'UNSOLVED';
  createdAt: string;
  updatedAt: string;
}

export interface SavedDraftMetadata {
  userId: string;
  scope: PracticeScope;
  classroomId: string | null;
  classroomName: string | null;
  problemId: string;
  title: string;
  problemType: PracticeProblemType | null;
  language: PracticeLanguage;
  updatedAt: string;
}

