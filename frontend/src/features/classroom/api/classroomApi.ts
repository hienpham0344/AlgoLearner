import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiErrorResponse, PageResponse, PaginatedData, ApiResponse } from '@/types/api';
import type { JoinMode, MembershipStatus } from '../types';

export interface ClassroomRecord {
  id: string;
  name: string;
  description: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  classCode: string | null;
  codeExpiresAt: string | null;
  codeLocked: boolean;
  joiningOpen: boolean;
  joinMode: JoinMode;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface ClassroomMemberRecord {
  studentId: string;
  username: string;
  email: string;
  status: MembershipStatus;
  joinedAt: string;
}

export interface JoinRequestRecord {
  id: string;
  studentId: string;
  username: string;
  createdAt: string;
}

export interface JoinResultRecord {
  outcome: 'JOINED' | 'PENDING_APPROVAL';
  classroomId: string;
  requestId: string | null;
}

const unwrap = <T>(response: { data: ApiResponse<T> }): T => response.data.data;
const page = <T>(response: { data: PageResponse<T> }): PaginatedData<T> => response.data.data;

export const classroomApi = {
  list: async (pageNumber = 0): Promise<PaginatedData<ClassroomRecord>> =>
    page(await apiClient.get<PageResponse<ClassroomRecord>>('/classrooms', { params: { page: pageNumber, size: 20 } })),
  get: async (id: string) => unwrap(await apiClient.get<ApiResponse<ClassroomRecord>>(`/classrooms/${id}`)),
  create: async (input: { name: string; description: string; joinMode: JoinMode; codeExpiresAt: string | null; ownerId?: string }) =>
    unwrap(await apiClient.post<ApiResponse<ClassroomRecord>>('/classrooms', input)),
  update: async (id: string, input: { name: string; description: string }) =>
    unwrap(await apiClient.patch<ApiResponse<ClassroomRecord>>(`/classrooms/${id}`, input)),
  archive: async (id: string) => { await apiClient.post(`/classrooms/${id}/archive`); },
  remove: async (id: string) => { await apiClient.delete(`/classrooms/${id}`); },
  configure: async (id: string, input: { codeExpiresAt: string | null; codeLocked: boolean; joiningOpen: boolean; joinMode: JoinMode }) =>
    unwrap(await apiClient.patch<ApiResponse<ClassroomRecord>>(`/classrooms/${id}/join-configuration`, input)),
  regenerateCode: async (id: string) => unwrap(await apiClient.post<ApiResponse<ClassroomRecord>>(`/classrooms/${id}/class-code/regenerate`)),
  join: async (classCode: string) => unwrap(await apiClient.post<ApiResponse<JoinResultRecord>>('/classrooms/join', { classCode })),
  leave: async (id: string) => { await apiClient.post(`/classrooms/${id}/leave`); },
  members: async (id: string, pageNumber = 0) =>
    page(await apiClient.get<PageResponse<ClassroomMemberRecord>>(`/classrooms/${id}/members`, { params: { page: pageNumber, size: 20 } })),
  directAdd: async (id: string, usernameOrEmail: string) => { await apiClient.post(`/classrooms/${id}/members/direct`, { usernameOrEmail }); },
  removeMember: async (id: string, studentId: string) => { await apiClient.delete(`/classrooms/${id}/members/${studentId}`); },
  requests: async (id: string, pageNumber = 0) =>
    page(await apiClient.get<PageResponse<JoinRequestRecord>>(`/classrooms/${id}/join-requests`, { params: { page: pageNumber, size: 20 } })),
  approve: async (id: string, requestId: string) => { await apiClient.post(`/classrooms/${id}/join-requests/${requestId}/approve`); },
  invite: async (id: string, email: string, expiresAt: string) =>
    unwrap(await apiClient.post<ApiResponse<string>>(`/classrooms/${id}/invitations`, { email, expiresAt })),
  revoke: async (id: string, invitationId: string) => { await apiClient.post(`/classrooms/${id}/invitations/${invitationId}/revoke`); },
  acceptInvitation: async (invitationId: string) => { await apiClient.post(`/classrooms/invitations/${invitationId}/accept`); },
  declineInvitation: async (invitationId: string) => { await apiClient.post(`/classrooms/invitations/${invitationId}/decline`); },
  transferOwner: async (id: string, targetTeacherId: string) => { await apiClient.post(`/classrooms/${id}/transfer-owner`, { targetTeacherId }); },
};

export function classroomError(error: unknown): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.message || (error.response ? `HTTP ${error.response.status}` : 'Cannot connect to the classroom API.');
  }
  return error instanceof Error ? error.message : 'Classroom request failed.';
}

export function classroomApiUnavailable(error: unknown): boolean {
  if (!isAxiosError(error)) return false;
  if (!error.response) return true;
  if (error.response.status === 404) return true;
  // Vite returns a plain-text proxy error when its backend target is offline.
  return import.meta.env.DEV && error.response.status >= 500 && typeof error.response.data === 'string';
}
