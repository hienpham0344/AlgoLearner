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
  list: async (classId: string, signal?: AbortSignal): Promise<QuizView[]> =>
    unwrap(await apiClient.get<ApiResponse<QuizView[]>>(base(classId), { signal })),

  get: async (classId: string, quizId: string, signal?: AbortSignal): Promise<QuizView> =>
    unwrap(await apiClient.get<ApiResponse<QuizView>>(quizResource(classId, quizId), { signal })),

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

  result: async (classId: string, quizId: string, studentId: string, signal?: AbortSignal): Promise<QuizResult> =>
    unwrap(await apiClient.get<ApiResponse<QuizResult>>(`${quizResource(classId, quizId)}/students/${encodeURIComponent(studentId)}/result`, { signal })),

  overrideGrade: async (classId: string, quizId: string, studentId: string, request: GradeOverrideRequest): Promise<GradeChange> =>
    unwrap(await apiClient.post<ApiResponse<GradeChange>>(`${quizResource(classId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`, request)),

  gradeHistory: async (classId: string, quizId: string, studentId: string, signal?: AbortSignal): Promise<GradeChange[]> =>
    unwrap(await apiClient.get<ApiResponse<GradeChange[]>>(`${quizResource(classId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`, { signal })),
};

export function quizError(error: unknown): string {
  if (isAxiosError(error)) {
    const errData = error.response?.data as ApiErrorResponse | undefined;
    if (errData?.message) return errData.message;
    if (error.response?.status === 400) return 'Invalid quiz configuration or request data.';
    if (error.response?.status === 401) return 'Session expired. Please sign in again.';
    if (error.response?.status === 403) return 'You do not have permission to manage quizzes in this classroom.';
    if (error.response?.status === 404) return 'Quiz or classroom not found.';
    if (error.response?.status === 409) return 'Conflict: Quiz status does not allow this operation.';
    return error.message || 'Quiz service encountered an unexpected error.';
  }
  if (error instanceof Error) return error.message;
  return 'An unexpected error occurred while communicating with the quiz service.';
}
