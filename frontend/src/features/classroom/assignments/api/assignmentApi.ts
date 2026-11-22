import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiErrorResponse, ApiResponse } from '@/types/api';
import type {
  AddQuestionRequest,
  AnswerView,
  AssignmentDetailView,
  AssignmentSummaryView,
  AttemptGrantRequest,
  AttemptView,
  CodeCaseWeightsRequest,
  CreateAssignmentRequest,
  EvaluationHistoryItem,
  EvaluationResult,
  GradeEssayRequest,
  OverrideGradeRequest,
  QuestionView,
  RejudgeRequest,
  RunCodeRequest,
  SaveDraftAnswerRequest,
  UpdateAssignmentRequest,
} from '../types';

const base = (classId: string) => `/classrooms/${encodeURIComponent(classId)}/assignments`;
const assignmentResource = (classId: string, assignmentId: string) =>
  `${base(classId)}/${encodeURIComponent(assignmentId)}`;

function unwrap<T>({ data }: { data: ApiResponse<T> }): T {
  if (!data.success) {
    throw new Error(data.message || 'Assignment request failed.');
  }
  return data.data;
}

export const assignmentApi = {
  // --- Teacher & Common Assignment Authoring / Management ---

  list: async (classId: string, signal?: AbortSignal): Promise<AssignmentSummaryView[]> =>
    unwrap(await apiClient.get<ApiResponse<AssignmentSummaryView[]>>(base(classId), { signal })),

  get: async (classId: string, assignmentId: string, signal?: AbortSignal): Promise<AssignmentDetailView> =>
    unwrap(await apiClient.get<ApiResponse<AssignmentDetailView>>(assignmentResource(classId, assignmentId), { signal })),

  create: async (classId: string, request: CreateAssignmentRequest): Promise<AssignmentDetailView> =>
    unwrap(await apiClient.post<ApiResponse<AssignmentDetailView>>(base(classId), request)),

  update: async (classId: string, assignmentId: string, request: UpdateAssignmentRequest): Promise<AssignmentDetailView> =>
    unwrap(await apiClient.put<ApiResponse<AssignmentDetailView>>(assignmentResource(classId, assignmentId), request)),

  delete: async (classId: string, assignmentId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<null>>(assignmentResource(classId, assignmentId));
  },

  publish: async (classId: string, assignmentId: string): Promise<AssignmentDetailView> =>
    unwrap(await apiClient.patch<ApiResponse<AssignmentDetailView>>(`${assignmentResource(classId, assignmentId)}/publish`)),

  close: async (classId: string, assignmentId: string): Promise<AssignmentDetailView> =>
    unwrap(await apiClient.patch<ApiResponse<AssignmentDetailView>>(`${assignmentResource(classId, assignmentId)}/close`)),

  publishGrades: async (classId: string, assignmentId: string): Promise<void> => {
    await apiClient.post<ApiResponse<null>>(`${assignmentResource(classId, assignmentId)}/publish-grades`);
  },

  clone: async (classId: string, assignmentId: string, targetClassroomId: string): Promise<AssignmentDetailView> =>
    unwrap(
      await apiClient.post<ApiResponse<AssignmentDetailView>>(
        `${assignmentResource(classId, assignmentId)}/clone`,
        null,
        { params: { targetClassroomId } }
      )
    ),

  addQuestion: async (classId: string, assignmentId: string, request: AddQuestionRequest): Promise<QuestionView> =>
    unwrap(
      await apiClient.post<ApiResponse<QuestionView>>(
        `${assignmentResource(classId, assignmentId)}/questions`,
        request
      )
    ),

  updateQuestion: async (
    classId: string,
    assignmentId: string,
    questionId: string,
    request: AddQuestionRequest
  ): Promise<QuestionView> =>
    unwrap(
      await apiClient.put<ApiResponse<QuestionView>>(
        `${assignmentResource(classId, assignmentId)}/questions/${encodeURIComponent(questionId)}`,
        request
      )
    ),

  deleteQuestion: async (classId: string, assignmentId: string, questionId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<null>>(
      `${assignmentResource(classId, assignmentId)}/questions/${encodeURIComponent(questionId)}`
    );
  },

  reorderQuestions: async (classId: string, assignmentId: string, questionIds: string[]): Promise<void> => {
    await apiClient.put<ApiResponse<null>>(
      `${assignmentResource(classId, assignmentId)}/question-order`,
      questionIds
    );
  },

  updateCodeCaseWeights: async (
    classId: string,
    assignmentId: string,
    questionId: string,
    request: CodeCaseWeightsRequest
  ): Promise<{ weights: number[]; reason: string }> =>
    unwrap(
      await apiClient.patch<ApiResponse<{ weights: number[]; reason: string }>>(
        `${assignmentResource(classId, assignmentId)}/questions/${encodeURIComponent(questionId)}/code-case-weights`,
        request
      )
    ),

  grantAttempts: async (
    classId: string,
    assignmentId: string,
    studentId: string,
    request: AttemptGrantRequest
  ): Promise<void> => {
    await apiClient.post<ApiResponse<null>>(
      `${assignmentResource(classId, assignmentId)}/students/${encodeURIComponent(studentId)}/attempt-grants`,
      request
    );
  },

  // --- Student Assessment Participation ---

  startAttempt: async (classId: string, assignmentId: string): Promise<AttemptView> =>
    unwrap(await apiClient.post<ApiResponse<AttemptView>>(`${assignmentResource(classId, assignmentId)}/attempts`)),

  getActiveAttempt: async (classId: string, assignmentId: string, signal?: AbortSignal): Promise<AttemptView> =>
    unwrap(
      await apiClient.get<ApiResponse<AttemptView>>(`${assignmentResource(classId, assignmentId)}/attempts/active`, {
        signal,
      })
    ),

  saveDraftAnswer: async (
    classId: string,
    assignmentId: string,
    attemptId: string,
    request: SaveDraftAnswerRequest
  ): Promise<AnswerView> =>
    unwrap(
      await apiClient.put<ApiResponse<AnswerView>>(
        `${assignmentResource(classId, assignmentId)}/attempts/${encodeURIComponent(attemptId)}/draft`,
        request
      )
    ),

  runCode: async (
    classId: string,
    assignmentId: string,
    attemptId: string,
    request: RunCodeRequest
  ): Promise<string> =>
    unwrap(
      await apiClient.post<ApiResponse<string>>(
        `${assignmentResource(classId, assignmentId)}/attempts/${encodeURIComponent(attemptId)}/runs`,
        request
      )
    ),

  getJobResult: async (
    classId: string,
    assignmentId: string,
    attemptId: string,
    jobId: string,
    signal?: AbortSignal
  ): Promise<EvaluationResult> =>
    unwrap(
      await apiClient.get<ApiResponse<EvaluationResult>>(
        `${assignmentResource(classId, assignmentId)}/attempts/${encodeURIComponent(attemptId)}/jobs/${encodeURIComponent(jobId)}`,
        { signal }
      )
    ),

  submitAttempt: async (classId: string, assignmentId: string, attemptId: string): Promise<AttemptView> =>
    unwrap(
      await apiClient.post<ApiResponse<AttemptView>>(
        `${assignmentResource(classId, assignmentId)}/attempts/${encodeURIComponent(attemptId)}/submit`
      )
    ),

  getOwnAttempts: async (classId: string, assignmentId: string, signal?: AbortSignal): Promise<AttemptView[]> =>
    unwrap(
      await apiClient.get<ApiResponse<AttemptView[]>>(`${assignmentResource(classId, assignmentId)}/attempts`, { signal })
    ),

  getOwnAttemptDetail: async (
    classId: string,
    assignmentId: string,
    attemptId: string,
    signal?: AbortSignal
  ): Promise<AttemptView> =>
    unwrap(
      await apiClient.get<ApiResponse<AttemptView>>(
        `${assignmentResource(classId, assignmentId)}/attempts/${encodeURIComponent(attemptId)}`,
        { signal }
      )
    ),

  // --- Teacher Submissions & Evaluation Management ---

  getSubmissions: async (classId: string, assignmentId: string, signal?: AbortSignal): Promise<AttemptView[]> =>
    unwrap(
      await apiClient.get<ApiResponse<AttemptView[]>>(`${assignmentResource(classId, assignmentId)}/submissions`, {
        signal,
      })
    ),

  getEvaluationHistory: async (
    classId: string,
    assignmentId: string,
    attemptId: string,
    signal?: AbortSignal
  ): Promise<EvaluationHistoryItem[]> =>
    unwrap(
      await apiClient.get<ApiResponse<EvaluationHistoryItem[]>>(
        `${assignmentResource(classId, assignmentId)}/submissions/${encodeURIComponent(attemptId)}/evaluations`,
        { signal }
      )
    ),

  gradeEssay: async (
    classId: string,
    assignmentId: string,
    attemptId: string,
    request: GradeEssayRequest
  ): Promise<void> => {
    await apiClient.post<ApiResponse<null>>(
      `${assignmentResource(classId, assignmentId)}/submissions/${encodeURIComponent(attemptId)}/grade-essay`,
      request
    );
  },

  overrideGrade: async (
    classId: string,
    assignmentId: string,
    attemptId: string,
    request: OverrideGradeRequest
  ): Promise<void> => {
    await apiClient.post<ApiResponse<null>>(
      `${assignmentResource(classId, assignmentId)}/submissions/${encodeURIComponent(attemptId)}/override-grade`,
      request
    );
  },

  rejudge: async (classId: string, assignmentId: string, request?: RejudgeRequest): Promise<void> => {
    await apiClient.post<ApiResponse<null>>(
      `${assignmentResource(classId, assignmentId)}/rejudge`,
      request || {}
    );
  },

  // --- File Upload for Essay ---

  uploadEssayFile: async (classId: string, file: File): Promise<{ id: string; originalName: string; size: number }> => {
    const formData = new FormData();
    formData.append('file', file);
    return unwrap(
      await apiClient.post<ApiResponse<{ id: string; originalName: string; size: number }>>(
        `/classrooms/${encodeURIComponent(classId)}/files?purpose=ESSAY_SUBMISSION`,
        formData,
        { headers: { 'Content-Type': undefined } }
      )
    );
  },
};

/**
 * Standardized error message resolution matching AlgoLearner ApiErrorResponse
 */
export function assignmentError(error: unknown): string {
  const isAxios =
    isAxiosError<ApiErrorResponse>(error) ||
    (typeof error === 'object' && error !== null && 'response' in error);

  if (isAxios) {
    const axiosErr = error as { response?: { status?: number; data?: ApiErrorResponse } };
    const status = axiosErr.response?.status;
    const serverMessage = axiosErr.response?.data?.message;

    if (status === 401) {
      return 'Your session has expired. Please log in again.';
    }
    if (status === 403) {
      return 'You do not have permission to access or perform actions on this assignment.';
    }
    if (status === 404) {
      return 'The requested assignment or submission could not be found.';
    }
    if (status === 409) {
      return serverMessage || 'Data conflict detected. Please refresh and try again.';
    }
    if (status === 429) {
      return 'Rate limit exceeded: Sample RUN is limited to 5 executions per minute.';
    }
    if (status === 400 || status === 422) {
      const fieldErrors = axiosErr.response?.data?.errors;
      if (fieldErrors && fieldErrors.length > 0) {
        return fieldErrors.map(f => `${f.field}: ${f.message}`).join(', ');
      }
      return serverMessage || 'Invalid request parameters. Please verify input fields.';
    }
    if (status === 503) {
      return 'The code judge or grading service is temporarily unavailable. Please try again later.';
    }
    return serverMessage || `Request failed (HTTP ${status}).`;
  }
  return error instanceof Error ? error.message : 'An unexpected error occurred.';
}
