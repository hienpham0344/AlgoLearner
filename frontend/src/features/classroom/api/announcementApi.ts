import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiErrorResponse, ApiResponse, PageResponse, PaginatedData } from '@/types/api';
import type {
  ClassroomAnnouncement,
  CreateAnnouncementInput,
  UpdateAnnouncementInput,
} from '../types';

export interface BackendAnnouncementView {
  id: string;
  classroomId: string;
  authorId: string;
  authorName: string;
  title: string;
  content: string;
  isPinned: boolean;
  pinnedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

const DEFAULT_PAGE_SIZE = 20;

const base = (classId: string) => `/classrooms/${encodeURIComponent(classId)}/announcements`;
const resource = (classId: string, announcementId: string) =>
  `${base(classId)}/${encodeURIComponent(announcementId)}`;

function unwrapApiResponse<T>(payload: ApiResponse<T> | unknown): T {
  if (!payload || typeof payload !== 'object' || !('data' in payload)) {
    throw new Error('Unexpected announcement response format.');
  }
  const envelope = payload as ApiResponse<T>;
  if (envelope.success === false) {
    throw new Error(envelope.message || 'Announcement request failed.');
  }
  return envelope.data;
}

function unwrapPageResponse<T>(payload: PageResponse<T> | unknown): PaginatedData<T> {
  const data = unwrapApiResponse<PaginatedData<T>>(payload);
  if (
    !data ||
    typeof data !== 'object' ||
    !Array.isArray(data.items) ||
    !data.pagination ||
    typeof data.pagination.page !== 'number' ||
    typeof data.pagination.totalPages !== 'number'
  ) {
    throw new Error('Unexpected paginated announcement response format.');
  }
  return data;
}

export function mapAnnouncement(raw: BackendAnnouncementView): ClassroomAnnouncement {
  return {
    id: raw.id,
    classroomId: raw.classroomId,
    authorId: raw.authorId,
    authorName: raw.authorName,
    title: raw.title,
    content: raw.content,
    isPinned: Boolean(raw.isPinned),
    pinnedAt: raw.pinnedAt ?? null,
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  };
}

export const announcementApi = {
  list: async (
    classId: string,
    page = 0,
    size = DEFAULT_PAGE_SIZE,
    signal?: AbortSignal,
  ): Promise<PaginatedData<ClassroomAnnouncement>> => {
    const response = await apiClient.get<PageResponse<BackendAnnouncementView>>(base(classId), {
      params: { page, size },
      signal,
    });
    const paginated = unwrapPageResponse<BackendAnnouncementView>(response.data);
    return {
      items: paginated.items.map(mapAnnouncement),
      pagination: paginated.pagination,
    };
  },

  get: async (
    classId: string,
    announcementId: string,
    signal?: AbortSignal,
  ): Promise<ClassroomAnnouncement> => {
    const response = await apiClient.get<ApiResponse<BackendAnnouncementView>>(
      resource(classId, announcementId),
      { signal },
    );
    return mapAnnouncement(unwrapApiResponse<BackendAnnouncementView>(response.data));
  },

  create: async (
    classId: string,
    input: CreateAnnouncementInput,
  ): Promise<ClassroomAnnouncement> => {
    const response = await apiClient.post<ApiResponse<BackendAnnouncementView>>(base(classId), {
      title: input.title,
      content: input.content,
    });
    return mapAnnouncement(unwrapApiResponse<BackendAnnouncementView>(response.data));
  },

  update: async (
    classId: string,
    announcementId: string,
    input: UpdateAnnouncementInput,
  ): Promise<ClassroomAnnouncement> => {
    const response = await apiClient.put<ApiResponse<BackendAnnouncementView>>(
      resource(classId, announcementId),
      {
        title: input.title,
        content: input.content,
      },
    );
    return mapAnnouncement(unwrapApiResponse<BackendAnnouncementView>(response.data));
  },

  remove: async (classId: string, announcementId: string): Promise<void> => {
    await apiClient.delete(resource(classId, announcementId));
  },

  pin: async (classId: string, announcementId: string): Promise<ClassroomAnnouncement> => {
    const response = await apiClient.patch<ApiResponse<BackendAnnouncementView>>(
      `${resource(classId, announcementId)}/pin`,
    );
    return mapAnnouncement(unwrapApiResponse<BackendAnnouncementView>(response.data));
  },

  unpin: async (classId: string, announcementId: string): Promise<ClassroomAnnouncement> => {
    const response = await apiClient.patch<ApiResponse<BackendAnnouncementView>>(
      `${resource(classId, announcementId)}/unpin`,
    );
    return mapAnnouncement(unwrapApiResponse<BackendAnnouncementView>(response.data));
  },
};

export function announcementError(error: unknown): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    const status = error.response?.status;
    if (status === 401) return 'Your session has expired. Please sign in again.';
    if (status === 403) return 'You do not have permission to access or modify classroom announcements.';
    if (status === 404) return 'Announcement or classroom was not found.';
    if (status === 400 || status === 422) {
      const validationDetails = (error.response?.data?.errors || [])
        .map(item => `${item.field}: ${item.message}`)
        .join(' ');
      return (
        [error.response?.data?.message, validationDetails].filter(Boolean).join(' ') ||
        'Please check the announcement title and content.'
      );
    }
    return (
      error.response?.data?.message ||
      (error.response ? `Announcement request failed (HTTP ${status}).` : 'Cannot connect to the announcements API.')
    );
  }
  return error instanceof Error ? error.message : 'Announcement request failed.';
}
