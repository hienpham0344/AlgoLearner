import { apiClient } from '@/services/apiClient';
import axios from 'axios';
import type { ApiResponse } from '@/types/api';

export interface Streak { currentStreak: number; highestStreak: number }
export interface OwnerProfile {
  fullName: string | null;
  username: string;
  email: string;
  avatarUrl: string | null;
  shortBio: string | null;
  role: 'STUDENT' | 'TEACHER';
  joinedAt: string;
  isPublic: boolean;
  publicPracticeStatistics: { solved: number };
  streak: Streak;
}
export interface PublicProfile {
  username: string;
  avatarUrl: string | null;
  shortBio: string | null;
  solved: number;
  streak: Streak;
}

const publicClient = axios.create({ baseURL: '/api/v1', timeout: 15000 });

export const profileApi = {
  own: async (signal?: AbortSignal) => (await apiClient.get<ApiResponse<OwnerProfile>>('/users/me/profile', { signal })).data.data,
  update: async (input: { fullName: string; shortBio: string }) => (await apiClient.patch<ApiResponse<OwnerProfile>>('/users/me/profile', input)).data.data,
  privacy: async (isPublic: boolean) => (await apiClient.patch<ApiResponse<OwnerProfile>>('/users/me/profile/privacy', { isPublic })).data.data,
  avatar: async (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return (await apiClient.put<ApiResponse<OwnerProfile>>('/users/me/profile/avatar', form, { headers: { 'Content-Type': undefined } })).data.data;
  },
  public: async (username: string, signal?: AbortSignal) => (await publicClient.get<ApiResponse<PublicProfile>>(`/users/${encodeURIComponent(username)}/profile`, { signal })).data.data,
};
