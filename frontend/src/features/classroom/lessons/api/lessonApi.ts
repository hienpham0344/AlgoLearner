import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiResponse, ApiErrorResponse, PaginatedData } from '@/types/api';
import type { Lesson, LessonInput, LessonActivity, LessonAttachment, LessonCapabilities, StudentLessonProgress } from '../types';

const base = (classId: string) => `/classrooms/${encodeURIComponent(classId)}/lessons`;
const resource = (classId: string, lessonId: string) => `${base(classId)}/${encodeURIComponent(lessonId)}`;
const unwrap = <T>({ data }: { data: ApiResponse<T> }): T => {
  if (!data.success) throw new Error(data.message || 'The lesson request failed.');
  return data.data;
};

export const lessonKeys = {
  root: (userId: string, classId: string) => ['lessons', userId, classId] as const,
  list: (userId: string, classId: string, page: number) => [...lessonKeys.root(userId, classId), 'list', page] as const,
  detail: (userId: string, classId: string, lessonId: string) => [...lessonKeys.root(userId, classId), 'detail', lessonId] as const,
  capabilities: (userId: string, classId: string) => [...lessonKeys.root(userId, classId), 'capabilities'] as const,
  activities: (userId: string, classId: string) => [...lessonKeys.root(userId, classId), 'activities'] as const,
  progress: (userId: string, classId: string, lessonId: string, page: number) => [...lessonKeys.root(userId, classId), 'progress', lessonId, page] as const,
};

// Proposed #12 contract. This adapter is the single integration point until BE confirms it.
export const lessonApi = {
  list: async (classId: string, page = 0, signal?: AbortSignal) => unwrap(await apiClient.get<ApiResponse<PaginatedData<Lesson>>>(base(classId), { params: { page, size: 20, sort: 'createdAt,asc' }, signal })),
  get: async (classId: string, lessonId: string, signal?: AbortSignal) => unwrap(await apiClient.get<ApiResponse<Lesson>>(resource(classId, lessonId), { signal })),
  create: async (classId: string, input: LessonInput) => unwrap(await apiClient.post<ApiResponse<Lesson>>(base(classId), input)),
  update: async (classId: string, lessonId: string, input: LessonInput) => unwrap(await apiClient.put<ApiResponse<Lesson>>(resource(classId, lessonId), input)),
  transition: async (classId: string, lessonId: string, action: 'publish' | 'draft' | 'archive' | 'restore') => unwrap(await apiClient.post<ApiResponse<Lesson>>(`${resource(classId, lessonId)}/${action}`)),
  remove: async (classId: string, lessonId: string) => { await apiClient.delete<ApiResponse<null>> (resource(classId, lessonId)); },
  complete: async (classId: string, lessonId: string) => unwrap(await apiClient.post<ApiResponse<Lesson>>(`${resource(classId, lessonId)}/completion`)),
  capabilities: async (classId: string) => unwrap(await apiClient.get<ApiResponse<LessonCapabilities>>(`/classrooms/${encodeURIComponent(classId)}/lesson-capabilities`)),
  activities: async (classId: string) => unwrap(await apiClient.get<ApiResponse<LessonActivity[]>>(`/classrooms/${encodeURIComponent(classId)}/lesson-activities`)),
  progress: async (classId: string, lessonId: string, page = 0) => unwrap(await apiClient.get<ApiResponse<PaginatedData<StudentLessonProgress>>>(`${resource(classId, lessonId)}/progress`, { params: { page, size: 20 } })),
  upload: async (classId: string, lessonId: string, file: File) => {
    const form = new FormData(); form.append('file', file);
    return unwrap(await apiClient.post<ApiResponse<LessonAttachment>>(`${resource(classId, lessonId)}/attachments`, form, { headers: { 'Content-Type': undefined } }));
  },
  removeAttachment: async (classId: string, lessonId: string, attachmentId: string) => {
    await apiClient.delete<ApiResponse<null>>(`${resource(classId, lessonId)}/attachments/${encodeURIComponent(attachmentId)}`);
  },
  // Binary responses are intentionally outside the JSON envelope; errors retain ApiErrorResponse.
  download: async (classId: string, lessonId: string, attachmentId: string): Promise<Blob> => {
    try {
      return (await apiClient.get<Blob>(`${resource(classId, lessonId)}/attachments/${encodeURIComponent(attachmentId)}/download`, { responseType: 'blob' })).data;
    } catch (error) {
      if (isAxiosError<ApiErrorResponse | Blob>(error) && error.response?.data instanceof Blob) {
        try { error.response.data = JSON.parse(await error.response.data.text()) as ApiErrorResponse; } catch { /* Non-JSON storage error: keep the HTTP status. */ }
      }
      throw error;
    }
  },
};

export function lessonError(error: unknown): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    const status = error.response?.status;
    if (status === 401) return 'Your session has expired. Please sign in again.';
    if (status === 403) return 'You do not have permission to access or change this lesson.';
    if (status === 404 || status === 503) return 'This lesson or service is currently unavailable.';
    if (status === 409) return error.response?.data?.message || 'The lesson changed or has related learning data. Refresh and try again; archive it to preserve history.';
    if (status === 400 || status === 422) return [error.response?.data?.message, ...(error.response?.data?.errors || []).map(field => `${field.field}: ${field.message}`)].filter(Boolean).join(' ') || 'Check the lesson fields and try again.';
    return error.response?.data?.message || (error.response ? `The request failed (HTTP ${status}). Please try again.` : 'Cannot connect. Check your connection and try again.');
  }
  return error instanceof Error ? error.message : 'The lesson request failed.';
}
