import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiErrorResponse, ApiResponse, PageResponse, PaginatedData } from '@/types/api';
import type {
  AdjustAttemptsRequest,
  AttemptView,
  GradeChange,
  GradeOverrideRequest,
  QuizConfigureRequest,
  QuizResult,
  QuizView,
  ReviewQuestion,
  SaveAnswerRequest,
  SaveReceipt,
} from '../types';

const base = (classId: string) => `/classrooms/${encodeURIComponent(classId)}/quizzes`;
const quizResource = (classId: string, quizId: string) =>
  `${base(classId)}/${encodeURIComponent(quizId)}`;

function unwrap<T>({ data }: { data: ApiResponse<T> }): T {
  if (!data.success) {
    throw new Error(data.message || 'Quiz request failed.');
  }
  return data.data;
}

export const quizApi = {
  // Common / Teacher & Student
  list: async (classId: string, signal?: AbortSignal): Promise<QuizView[]> =>
    unwrap(await apiClient.get<ApiResponse<QuizView[]>>(base(classId), { signal })),

  get: async (classId: string, quizId: string, signal?: AbortSignal): Promise<QuizView> =>
    unwrap(await apiClient.get<ApiResponse<QuizView>>(quizResource(classId, quizId), { signal })),

  // Student Attempt Lifecycle
  startAttempt: async (classId: string, quizId: string): Promise<AttemptView> =>
    unwrap(await apiClient.post<ApiResponse<AttemptView>>(`${quizResource(classId, quizId)}/attempts`)),

  resumeAttempt: async (
    classId: string,
    quizId: string,
    attemptId: string,
    signal?: AbortSignal
  ): Promise<AttemptView> =>
    unwrap(
      await apiClient.get<ApiResponse<AttemptView>>(
        `${quizResource(classId, quizId)}/attempts/${encodeURIComponent(attemptId)}`,
        { signal }
      )
    ),

  saveAnswer: async (
    classId: string,
    quizId: string,
    attemptId: string,
    request: SaveAnswerRequest
  ): Promise<SaveReceipt> =>
    unwrap(
      await apiClient.put<ApiResponse<SaveReceipt>>(
        `${quizResource(classId, quizId)}/attempts/${encodeURIComponent(attemptId)}/answers`,
        request
      )
    ),

  submitAttempt: async (classId: string, quizId: string, attemptId: string): Promise<AttemptView> =>
    unwrap(
      await apiClient.post<ApiResponse<AttemptView>>(
        `${quizResource(classId, quizId)}/attempts/${encodeURIComponent(attemptId)}/submit`
      )
    ),

  review: async (
    classId: string,
    quizId: string,
    attemptId: string,
    signal?: AbortSignal
  ): Promise<ReviewQuestion[]> =>
    unwrap(
      await apiClient.get<ApiResponse<ReviewQuestion[]>>(
        `${quizResource(classId, quizId)}/attempts/${encodeURIComponent(attemptId)}/review`,
        { signal }
      )
    ),

  history: async (
    classId: string,
    quizId: string,
    studentId?: string,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PaginatedData<AttemptView>> => {
    const params = new URLSearchParams();
    if (studentId) params.append('studentId', studentId);
    params.append('page', String(page));
    params.append('size', String(size));

    const response = await apiClient.get<PageResponse<AttemptView>>(
      `${quizResource(classId, quizId)}/attempts?${params.toString()}`,
      { signal }
    );
    return unwrap(response);
  },

  result: async (
    classId: string,
    quizId: string,
    studentId: string,
    signal?: AbortSignal
  ): Promise<QuizResult> =>
    unwrap(
      await apiClient.get<ApiResponse<QuizResult>>(
        `${quizResource(classId, quizId)}/students/${encodeURIComponent(studentId)}/result`,
        { signal }
      )
    ),

  // Teacher Scope Operations
  authoring: async (classId: string, quizId: string, signal?: AbortSignal): Promise<QuizConfigureRequest> =>
    unwrap(await apiClient.get<ApiResponse<QuizConfigureRequest>>(`${quizResource(classId, quizId)}/authoring`, { signal })),

  create: async (classId: string, request: QuizConfigureRequest): Promise<QuizView> =>
    unwrap(await apiClient.post<ApiResponse<QuizView>>(base(classId), request)),

  update: async (classId: string, quizId: string, request: QuizConfigureRequest): Promise<QuizView> =>
    unwrap(await apiClient.put<ApiResponse<QuizView>>(quizResource(classId, quizId), request)),

  publish: async (classId: string, quizId: string): Promise<QuizView> =>
    unwrap(await apiClient.post<ApiResponse<QuizView>>(`${quizResource(classId, quizId)}/publish`)),

  close: async (classId: string, quizId: string): Promise<QuizView> =>
    unwrap(await apiClient.post<ApiResponse<QuizView>>(`${quizResource(classId, quizId)}/close`)),

  duplicate: async (classId: string, quizId: string): Promise<QuizView> =>
    unwrap(await apiClient.post<ApiResponse<QuizView>>(`${quizResource(classId, quizId)}/duplicate`)),

  publishGrades: async (classId: string, quizId: string): Promise<void> => {
    unwrap(await apiClient.post<ApiResponse<null>>(`${quizResource(classId, quizId)}/publish-grades`));
  },

  adjustAttempts: async (classId: string, quizId: string, request: AdjustAttemptsRequest): Promise<void> => {
    unwrap(await apiClient.post<ApiResponse<null>>(`${quizResource(classId, quizId)}/attempt-rights`, request));
  },

  overrideGrade: async (classId: string, quizId: string, studentId: string, request: GradeOverrideRequest): Promise<GradeChange> =>
    unwrap(await apiClient.post<ApiResponse<GradeChange>>(`${quizResource(classId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`, request)),

  gradeHistory: async (classId: string, quizId: string, studentId: string, signal?: AbortSignal): Promise<GradeChange[]> =>
    unwrap(await apiClient.get<ApiResponse<GradeChange[]>>(`${quizResource(classId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`, { signal })),
};

export function quizError(error: unknown): string {
  if (isAxiosError(error)) {
    const errData = error.response?.data as ApiErrorResponse | undefined;
    if (errData?.message) return errData.message;
    if (error.response?.status === 400) return 'Invalid quiz request or data validation failed.';
    if (error.response?.status === 401) return 'Session expired. Please sign in again.';
    if (error.response?.status === 403) return 'You do not have permission to perform this action.';
    if (error.response?.status === 404) return 'Quiz or attempt not found.';
    if (error.response?.status === 409) {
      if (errData?.errorCode === 'QUIZ_ATTEMPT_LIMIT') {
        return 'You have reached the maximum allowed attempts for this quiz.';
      }
      if (errData?.errorCode === 'QUIZ_UNAVAILABLE') {
        return 'This quiz is not currently open or has already closed.';
      }
      if (errData?.errorCode === 'QUIZ_TEACHER_PERMISSION_REQUIRED') {
        return 'Your previous attempt was locked on timeout. Teacher authorization is required.';
      }
      if (errData?.errorCode === 'ANSWER_VERSION_CONFLICT') {
        return 'Answer version conflict. Your responses are being re-synchronized.';
      }
      return 'Operation conflicts with the current quiz state.';
    }
    return error.message || 'Error communicating with quiz service.';
  }
  if (error instanceof Error) return error.message;
  return 'An unexpected error occurred.';
}
