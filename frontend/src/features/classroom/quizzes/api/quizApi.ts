/**
 * Quiz API Service
 * Directly interacts with backend /api/v1/classrooms/{classroomId}/quizzes endpoints.
 */

import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  QuizView,
  AttemptView,
  QuizResult,
  GradeChange,
  CreateQuizRequest,
  UpdateQuizRequest,
  ChangeQuizStatusRequest,
  PublishGradesRequest,
  SaveAnswerRequest,
  AccessOverrideRequest,
  GradeOverrideRequest,
  QuizStatus,
} from '../types';

const base = (classroomId: string) =>
  `/classrooms/${encodeURIComponent(classroomId)}/quizzes`;

const quizResource = (classroomId: string, quizId: string) =>
  `${base(classroomId)}/${encodeURIComponent(quizId)}`;

function unwrap<T>(data: ApiResponse<T>): T {
  if (!data.success) {
    throw new Error(data.message || 'Quiz operation failed.');
  }
  return data.data;
}

export const quizApi = {
  // Teacher: Create draft quiz
  create: async (classroomId: string, request: CreateQuizRequest): Promise<QuizView> => {
    const res = await apiClient.post<ApiResponse<QuizView>>(base(classroomId), request);
    return unwrap(res.data);
  },

  // Teacher & Student: List visible quizzes
  list: async (
    classroomId: string,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PageResponse<QuizView>['data']> => {
    const res = await apiClient.get<PageResponse<QuizView>>(base(classroomId), {
      params: { page, size },
      signal,
    });
    return res.data.data;
  },

  // Teacher & Student: Read quiz details
  get: async (classroomId: string, quizId: string, signal?: AbortSignal): Promise<QuizView> => {
    const res = await apiClient.get<ApiResponse<QuizView>>(quizResource(classroomId, quizId), { signal });
    return unwrap(res.data);
  },

  // Teacher: Update quiz before first attempt
  update: async (
    classroomId: string,
    quizId: string,
    request: UpdateQuizRequest
  ): Promise<QuizView> => {
    const res = await apiClient.put<ApiResponse<QuizView>>(quizResource(classroomId, quizId), request);
    return unwrap(res.data);
  },

  // Teacher: Delete unused draft quiz
  delete: async (classroomId: string, quizId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<null>>(quizResource(classroomId, quizId));
  },

  // Teacher: Update quiz status (DRAFT / PUBLISHED / CLOSED)
  changeStatus: async (
    classroomId: string,
    quizId: string,
    status: QuizStatus
  ): Promise<QuizView> => {
    const req: ChangeQuizStatusRequest = { status };
    const res = await apiClient.put<ApiResponse<QuizView>>(
      `${quizResource(classroomId, quizId)}/status`,
      req
    );
    return unwrap(res.data);
  },

  // Teacher: Duplicate quiz into independent draft
  duplicate: async (classroomId: string, quizId: string): Promise<QuizView> => {
    const res = await apiClient.post<ApiResponse<QuizView>>(
      `${quizResource(classroomId, quizId)}/duplicate`
    );
    return unwrap(res.data);
  },

  // Teacher: Publish or withdraw grades
  publishGrades: async (
    classroomId: string,
    quizId: string,
    published: boolean
  ): Promise<void> => {
    const req: PublishGradesRequest = { published };
    await apiClient.put<ApiResponse<null>>(
      `${quizResource(classroomId, quizId)}/grade-publication`,
      req
    );
  },

  // Student: Start new attempt or resume active attempt
  startAttempt: async (classroomId: string, quizId: string): Promise<AttemptView> => {
    const res = await apiClient.post<ApiResponse<AttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts`
    );
    return unwrap(res.data);
  },

  // Teacher & Student: List attempts
  listAttempts: async (
    classroomId: string,
    quizId: string,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PageResponse<AttemptView>['data']> => {
    const res = await apiClient.get<PageResponse<AttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts`,
      { params: { page, size }, signal }
    );
    return res.data.data;
  },

  // Teacher & Student: Get single attempt details (resume state)
  getAttempt: async (
    classroomId: string,
    quizId: string,
    attemptId: string,
    signal?: AbortSignal
  ): Promise<AttemptView> => {
    const res = await apiClient.get<ApiResponse<AttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts/${encodeURIComponent(attemptId)}`,
      { signal }
    );
    return unwrap(res.data);
  },

  // Student: Autosave atomic answer for a single question
  autosaveAnswer: async (
    classroomId: string,
    quizId: string,
    attemptId: string,
    questionId: string,
    optionIds: string[]
  ): Promise<AttemptView> => {
    const req: SaveAnswerRequest = { optionIds };
    const res = await apiClient.put<ApiResponse<AttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts/${encodeURIComponent(attemptId)}/answers/${encodeURIComponent(questionId)}`,
      req
    );
    return unwrap(res.data);
  },

  // Student: Submit attempt
  submitAttempt: async (
    classroomId: string,
    quizId: string,
    attemptId: string
  ): Promise<AttemptView> => {
    const res = await apiClient.post<ApiResponse<AttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts/${encodeURIComponent(attemptId)}/submit`
    );
    return unwrap(res.data);
  },

  // Teacher & Student: Get student official result
  getResult: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    signal?: AbortSignal
  ): Promise<QuizResult> => {
    const res = await apiClient.get<ApiResponse<QuizResult>>(
      `${quizResource(classroomId, quizId)}/students/${encodeURIComponent(studentId)}/result`,
      { signal }
    );
    return unwrap(res.data);
  },

  // Teacher: Access override (reset allowance, extra attempts, override deadline/timelimit)
  overrideAccess: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    request: AccessOverrideRequest
  ): Promise<void> => {
    await apiClient.put<ApiResponse<null>>(
      `${quizResource(classroomId, quizId)}/students/${encodeURIComponent(studentId)}/access-override`,
      request
    );
  },

  // Teacher: Grade override
  overrideGrade: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    request: GradeOverrideRequest
  ): Promise<GradeChange> => {
    const res = await apiClient.post<ApiResponse<GradeChange>>(
      `${quizResource(classroomId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`,
      request
    );
    return unwrap(res.data);
  },

  // Teacher: List grade overrides audit history
  listGradeOverrides: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PageResponse<GradeChange>['data']> => {
    const res = await apiClient.get<PageResponse<GradeChange>>(
      `${quizResource(classroomId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`,
      { params: { page, size }, signal }
    );
    return res.data.data;
  },
};

/**
 * Standard error extractor for Quiz API responses
 */
export function quizError(error: unknown): string {
  if (isAxiosError(error)) {
    const responseData = error.response?.data;
    if (responseData) {
      if (typeof responseData === 'object' && 'message' in responseData) {
        const msg = String(responseData.message);
        if (responseData.errorCode) {
          return `${msg} (${responseData.errorCode})`;
        }
        return msg;
      }
    }
    if (error.response?.status === 401) return 'Session expired. Please log in again.';
    if (error.response?.status === 403) return 'You do not have permission to perform this action.';
    if (error.response?.status === 404) return 'The requested quiz resource was not found.';
    if (error.response?.status === 409) return 'Action conflict. The quiz or attempt state has changed.';
    return error.message || 'Network request failed.';
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'An unexpected error occurred.';
}
