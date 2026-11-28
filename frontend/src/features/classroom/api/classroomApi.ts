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

export interface InvitationRecord {
  id: string;
  classroomId: string;
  classroomName: string;
  studentId: string;
  studentEmail: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
}

export interface InvitationNotificationRecord {
  id: string;
  type: string;
  title: string;
  message: string | null;
  targetType: string;
  targetId: string | null;
  actionUrl: string | null;
  invitationId: string | null;
  classroomId: string | null;
  classroomName: string | null;
  status: InvitationRecord['status'] | string;
  expiresAt: string | null;
  createdAt: string;
  readAt: string | null;
}

export const classroomKeys = {
  all: ['classrooms'] as const,
  list: (pageNumber: number) => [...classroomKeys.all, 'list', pageNumber] as const,
  detail: (id: string) => [...classroomKeys.all, 'detail', id] as const,
  members: (id: string, pageNumber: number) => [...classroomKeys.all, 'members', id, pageNumber] as const,
  requests: (id: string, pageNumber: number) => [...classroomKeys.all, 'requests', id, pageNumber] as const,
  invitations: (id: string, pageNumber: number) => [...classroomKeys.all, 'invitations', id, pageNumber] as const,
  myInvitations: (pageNumber: number) => [...classroomKeys.all, 'my-invitations', pageNumber] as const,
  notifications: (pageNumber: number) => ['notifications', 'invitations', pageNumber] as const,
};

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
  reject: async (id: string, requestId: string) => { await apiClient.post(`/classrooms/${id}/join-requests/${requestId}/reject`); },
  invite: async (id: string, email: string, expiresAt: string) =>
    unwrap(await apiClient.post<ApiResponse<string>>(`/classrooms/${id}/invitations`, { email, expiresAt })),
  invitations: async (id: string, pageNumber = 0): Promise<PaginatedData<InvitationRecord>> =>
    page(await apiClient.get<PageResponse<InvitationRecord>>(`/classrooms/${id}/invitations`, { params: { page: pageNumber, size: 20 } })),
  myInvitations: async (pageNumber = 0): Promise<PaginatedData<InvitationRecord>> =>
    page(await apiClient.get<PageResponse<InvitationRecord>>('/classrooms/invitations/me', { params: { page: pageNumber, size: 20 } })),
  notifications: async (pageNumber = 0): Promise<PaginatedData<InvitationNotificationRecord>> =>
    page(await apiClient.get<PageResponse<InvitationNotificationRecord>>('/notifications', { params: { page: pageNumber, size: 20 } })),
  markNotificationRead: async (notificationId: string): Promise<void> => {
    await apiClient.post(`/notifications/${encodeURIComponent(notificationId)}/read`);
  },
  revoke: async (id: string, invitationId: string) => { await apiClient.post(`/classrooms/${id}/invitations/${invitationId}/revoke`); },
  acceptInvitation: async (invitationId: string) => { await apiClient.post(`/classrooms/invitations/${invitationId}/accept`); },
  declineInvitation: async (invitationId: string) => { await apiClient.post(`/classrooms/invitations/${invitationId}/decline`); },
  transferOwner: async (id: string, targetTeacherId: string) => { await apiClient.post(`/classrooms/${id}/transfer-owner`, { targetTeacherId }); },
};

export function classroomError(error: unknown): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    if (error.response?.data?.errorCode === 'INVITATION_UNAVAILABLE') return 'Invitation has expired.';
    return error.response?.data?.message || (error.response ? `HTTP ${error.response.status}` : 'Cannot connect to the classroom API.');
  }
  return error instanceof Error ? error.message : 'Classroom request failed.';
}

export function classroomApiUnavailable(error: unknown): boolean {
  return isAxiosError(error) && error.response?.headers?.['x-algolearner-source'] === 'mock'
    && error.response?.data?.errorCode === 'OFFLINE_MOCK';
}
