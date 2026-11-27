import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiErrorResponse, ApiResponse, PageResponse, PaginatedData } from '@/types/api';
import type {
  ClassPracticeDetail,
  ClassPracticeSummary,
  PracticeLanguage,
  PracticeSubmission,
  PublicEvaluationResult,
  PublicPracticeStatistics,
  PublicProblemDetail,
  PublicProblemSummary,
  PublicSubmissionRecord,
  RunResult,
} from '../types';

export type {
  ClassPracticeDetail as PracticeDetail,
  ClassPracticeSummary as PracticeSummary,
  PracticeLanguage,
  PracticeSubmission as Submission,
  RunResult,
};

export const practiceKeys = {
  all: ['practice'] as const,
  classList: (classroomId: string, page: number) =>
    [...practiceKeys.all, 'class-list', classroomId, page] as const,
  classDetail: (classroomId: string, practiceId: string) =>
    [...practiceKeys.all, 'class-detail', classroomId, practiceId] as const,
  classHistory: (classroomId: string, practiceId: string, page: number) =>
    [...practiceKeys.all, 'class-history', classroomId, practiceId, page] as const,
  classSubmission: (classroomId: string, practiceId: string, submissionId: string) =>
    [...practiceKeys.all, 'class-submission', classroomId, practiceId, submissionId] as const,
  publicList: (page: number) => [...practiceKeys.all, 'public-list', page] as const,
  publicDetail: (problemId: string) => [...practiceKeys.all, 'public-detail', problemId] as const,
  publicHistory: (problemId: string, page: number) =>
    [...practiceKeys.all, 'public-history', problemId, page] as const,
  publicJob: (problemId: string, jobId: string) =>
    [...practiceKeys.all, 'public-job', problemId, jobId] as const,
  publicStats: () => [...practiceKeys.all, 'public-stats'] as const,
};

const classBase = (classroomId: string) =>
  `/classrooms/${encodeURIComponent(classroomId)}/practices`;
const publicBase = '/problems';

const unwrap = <T>(response: { data: ApiResponse<T> }): T => response.data.data;
const pageData = <T>(response: { data: PageResponse<T> }): PaginatedData<T> => response.data.data;

/** SRS 4.8 Classroom Practice API client */
export const practiceApi = {
  list: async (classroomId: string, page = 0): Promise<PaginatedData<ClassPracticeSummary>> =>
    pageData(
      await apiClient.get<PageResponse<ClassPracticeSummary>>(classBase(classroomId), {
        params: { page, size: 20 },
      }),
    ),

  detail: async (classroomId: string, practiceId: string): Promise<ClassPracticeDetail> =>
    unwrap(
      await apiClient.get<ApiResponse<ClassPracticeDetail>>(
        `${classBase(classroomId)}/${encodeURIComponent(practiceId)}`,
      ),
    ),

  run: async (
    classroomId: string,
    practiceId: string,
    input: { language: PracticeLanguage; sourceCode: string; customInput?: string },
  ): Promise<RunResult> =>
    unwrap(
      await apiClient.post<ApiResponse<RunResult>>(
        `${classBase(classroomId)}/${encodeURIComponent(practiceId)}/run`,
        input,
      ),
    ),

  submit: async (
    classroomId: string,
    practiceId: string,
    input: { language: PracticeLanguage; sourceCode: string },
  ): Promise<PracticeSubmission> =>
    unwrap(
      await apiClient.post<ApiResponse<PracticeSubmission>>(
        `${classBase(classroomId)}/${encodeURIComponent(practiceId)}/submit`,
        input,
      ),
    ),

  history: async (
    classroomId: string,
    practiceId: string,
    page = 0,
  ): Promise<PaginatedData<PracticeSubmission>> =>
    pageData(
      await apiClient.get<PageResponse<PracticeSubmission>>(
        `${classBase(classroomId)}/${encodeURIComponent(practiceId)}/submissions/me`,
        { params: { page, size: 20 } },
      ),
    ),

  submission: async (
    classroomId: string,
    practiceId: string,
    submissionId: string,
  ): Promise<PracticeSubmission> =>
    unwrap(
      await apiClient.get<ApiResponse<PracticeSubmission>>(
        `${classBase(classroomId)}/${encodeURIComponent(practiceId)}/submissions/${encodeURIComponent(submissionId)}`,
      ),
    ),
};

/** SRS 4.12 Public Practice API client */
export const publicPracticeApi = {
  list: async (page = 0): Promise<PaginatedData<PublicProblemSummary>> =>
    pageData(
      await apiClient.get<PageResponse<PublicProblemSummary>>(publicBase, {
        params: { page, size: 20 },
      }),
    ),

  detail: async (problemId: string): Promise<PublicProblemDetail> =>
    unwrap(
      await apiClient.get<ApiResponse<PublicProblemDetail>>(
        `${publicBase}/${encodeURIComponent(problemId)}`,
      ),
    ),

  run: async (
    problemId: string,
    input: { language: PracticeLanguage; source: string; requestKey: string },
  ): Promise<string> =>
    unwrap(
      await apiClient.post<ApiResponse<string>>(
        `${publicBase}/${encodeURIComponent(problemId)}/run`,
        input,
      ),
    ),

  submit: async (
    problemId: string,
    input: { language: PracticeLanguage; source: string; requestKey: string },
  ): Promise<string> =>
    unwrap(
      await apiClient.post<ApiResponse<string>>(
        `${publicBase}/${encodeURIComponent(problemId)}/submissions`,
        input,
      ),
    ),

  jobResult: async (problemId: string, jobId: string): Promise<PublicEvaluationResult> =>
    unwrap(
      await apiClient.get<ApiResponse<PublicEvaluationResult>>(
        `${publicBase}/${encodeURIComponent(problemId)}/jobs/${encodeURIComponent(jobId)}`,
      ),
    ),

  history: async (
    problemId: string,
    page = 0,
  ): Promise<PaginatedData<PublicSubmissionRecord>> =>
    pageData(
      await apiClient.get<PageResponse<PublicSubmissionRecord>>(
        `${publicBase}/${encodeURIComponent(problemId)}/submissions`,
        { params: { page, size: 20 } },
      ),
    ),

  statistics: async (): Promise<PublicPracticeStatistics> =>
    unwrap(await apiClient.get<ApiResponse<PublicPracticeStatistics>>(`${publicBase}/statistics/me`)),
};

export interface ClassifiedPracticeError {
  kind: 'rate_limit' | 'infrastructure' | 'conflict' | 'forbidden' | 'unauthorized' | 'not_found' | 'validation' | 'network';
  heading: string;
  message: string;
}

export function classifyPracticeError(error: unknown): ClassifiedPracticeError {
  if (isAxiosError<ApiErrorResponse>(error)) {
    const status = error.response?.status;
    const apiMessage = error.response?.data?.message;
    const errorCode = error.response?.data?.errorCode;

    if (status === 429) {
      return {
        kind: 'rate_limit',
        heading: 'Execution rate limit reached',
        message: apiMessage || 'Trial runs are capped at 5 per minute. Wait a moment before running again.',
      };
    }
    if (status === 503 || errorCode === 'JUDGE_SERVICE_UNAVAILABLE') {
      return {
        kind: 'infrastructure',
        heading: 'Code Judge temporarily unavailable',
        message:
          apiMessage ||
          'The isolated execution worker could not complete this request. Your code was not marked incorrect and no failed submission was recorded.',
      };
    }
    if (errorCode === 'PRACTICE_TESTCASES_CHANGED') {
      return {
        kind: 'conflict',
        heading: 'Test suite updated during grading',
        message:
          apiMessage ||
          'The instructor updated the test cases while your submission was running. Submit again to evaluate against the latest version.',
      };
    }
    if (errorCode === 'CLASSROOM_ARCHIVED' || errorCode === 'PRACTICE_READ_ONLY') {
      return {
        kind: 'conflict',
        heading: 'Practice is read-only',
        message: apiMessage || 'This classroom or practice has been archived and no longer accepts runs or submissions.',
      };
    }
    if (errorCode === 'PRACTICE_NOT_READY') {
      return {
        kind: 'conflict',
        heading: 'Practice test suite incomplete',
        message: apiMessage || 'This practice problem does not have usable test cases configured yet.',
      };
    }
    if (status === 409) {
      return {
        kind: 'conflict',
        heading: 'Request conflict',
        message: apiMessage || 'A conflicting state prevented this action. Refresh and try again.',
      };
    }
    if (status === 401) {
      return {
        kind: 'unauthorized',
        heading: 'Session expired',
        message: 'Sign in again to continue coding practice.',
      };
    }
    if (status === 403) {
      return {
        kind: 'forbidden',
        heading: 'Access restricted',
        message: apiMessage || 'Your account does not have permission to access or submit to this practice.',
      };
    }
    if (status === 404) {
      return {
        kind: 'not_found',
        heading: 'Problem unavailable',
        message: apiMessage || 'This practice problem does not exist or is not published for your account.',
      };
    }
    if (status && status >= 400 && status < 500) {
      return {
        kind: 'validation',
        heading: 'Invalid request',
        message: apiMessage || `Request rejected (HTTP ${status}).`,
      };
    }
    return {
      kind: status ? 'infrastructure' : 'network',
      heading: status ? 'Server error' : 'Connection interrupted',
      message:
        apiMessage ||
        (status
          ? `The server returned HTTP ${status}. Your code draft remains safe on this device.`
          : 'Cannot reach the practice service. Check your connection and try again.'),
    };
  }

  return {
    kind: 'network',
    heading: 'Request failed',
    message: error instanceof Error ? error.message : 'An unexpected error occurred.',
  };
}

export function practiceError(error: unknown): string {
  return classifyPracticeError(error).message;
}
