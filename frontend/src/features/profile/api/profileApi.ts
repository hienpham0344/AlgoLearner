import axios from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiResponse } from '@/types/api';

export interface Streak {
  currentStreak: number;
  highestStreak: number;
}

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

const publicClient = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
});

export const profileApi = {
  own: async (signal?: AbortSignal) => {
    const response = await apiClient.get<ApiResponse<OwnerProfile>>('/users/me/profile', { signal });
    return response.data.data;
  },
  update: async (input: { fullName: string; shortBio: string }) => {
    const response = await apiClient.patch<ApiResponse<OwnerProfile>>('/users/me/profile', input);
    return response.data.data;
  },
  privacy: async (isPublic: boolean) => {
    const response = await apiClient.patch<ApiResponse<OwnerProfile>>('/users/me/profile/privacy', { isPublic });
    return response.data.data;
  },
  avatar: async (file: File) => {
    const form = new FormData();
    form.append('file', file);
    const response = await apiClient.put<ApiResponse<OwnerProfile>>('/users/me/profile/avatar', form, {
      headers: { 'Content-Type': undefined },
    });
    return response.data.data;
  },
  public: async (username: string, signal?: AbortSignal) => {
    const response = await publicClient.get<ApiResponse<PublicProfile>>(
      `/users/${encodeURIComponent(username)}/profile`,
      { signal },
    );
    return response.data.data;
  },
};
