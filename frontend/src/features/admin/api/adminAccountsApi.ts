import { apiClient } from '@/services/apiClient';
import type { ApiResponse, PageResponse, PaginatedData } from '@/types/api';
import type { UserRole } from '@/features/auth/types';

export interface AdminAccountView {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  status: string;
  loginLockedUntil: string | null;
  suspensionReason: string | null;
  suspensionEndsAt: string | null;
  createdAt: string;
}

export const adminAccountKeys = {
  all: ['admin', 'accounts'] as const,
  list: (page: number) => [...adminAccountKeys.all, page] as const,
};

export const adminAccountsApi = {
  list: async (page = 0): Promise<PaginatedData<AdminAccountView>> => {
    const response = await apiClient.get<PageResponse<AdminAccountView>>('/admin/accounts', { params: { page, size: 20 } });
    return response.data.data;
  },
  suspend: async (id: string, input: { reason: string; endsAt: string | null }): Promise<AdminAccountView> => {
    const response = await apiClient.post<ApiResponse<AdminAccountView>>(`/admin/accounts/${encodeURIComponent(id)}/suspend`, input);
    return response.data.data;
  },
  unsuspend: async (id: string): Promise<AdminAccountView> => {
    const response = await apiClient.post<ApiResponse<AdminAccountView>>(`/admin/accounts/${encodeURIComponent(id)}/unsuspend`);
    return response.data.data;
  },
  reactivate: async (id: string): Promise<AdminAccountView> => {
    const response = await apiClient.post<ApiResponse<AdminAccountView>>(`/admin/accounts/${encodeURIComponent(id)}/reactivate`);
    return response.data.data;
  },
};
