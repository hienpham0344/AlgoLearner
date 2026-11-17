import { apiClient } from '@/services/apiClient';
import { isAxiosError } from 'axios';
import { ApiResponse } from '@/types/api';
import {
  AuthResponseData,
  AuthUser,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResendOtpRequest,
  ResetPasswordRequest,
  ResetVerificationData,
  VerifyOtpRequest,
} from '../types';

export const authKeys = {
  all: ['auth'] as const,
  sessions: () => [...authKeys.all, 'sessions'] as const,
};

export interface AuthSession {
  id: string;
  deviceLabel: string;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  current: boolean;
}

export const authApi = {
  // Login with credentials
  login: async (payload: LoginRequest): Promise<AuthResponseData> => {
    try {
      const response = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/login', payload);
      return response.data.data;
    } catch (err: unknown) {
      if (!['auth-demo', 'backend-mock'].includes(import.meta.env.MODE)
        || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
      // Dev mock fallback if /auth/login is not yet deployed on backend
      const { usernameOrEmail } = payload;
      if (
        usernameOrEmail === 'student' ||
        usernameOrEmail === 'teacher' ||
        usernameOrEmail === 'admin'
      ) {
        return authApi.quickLogin(usernameOrEmail.toUpperCase() as 'STUDENT' | 'TEACHER' | 'ADMIN');
      }
      throw err;
    }
  },

  // Register new account (Student or Teacher)
  register: async (payload: RegisterRequest): Promise<{ email: string; message: string }> => {
    try {
      const response = await apiClient.post<ApiResponse<AuthUser>>(
        '/auth/register',
        {
          email: payload.email, username: payload.username, password: payload.password,
          role: payload.role, termsAccepted: payload.termsAccepted, privacyAccepted: payload.privacyAccepted,
        }
      );
      return { email: response.data.data.email, message: response.data.message || 'Verification code sent.' };
    } catch (err: unknown) {
      if (import.meta.env.MODE !== 'auth-demo'
        || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
      // Simulate dev success if endpoint not implemented yet
      return {
        email: payload.email,
        message: 'Mã xác thực OTP đã được gửi đến email của bạn.',
      };
    }
  },

  // Verify OTP (purpose: REGISTER | RESET_PASSWORD)
  verifyOtp: async (payload: VerifyOtpRequest): Promise<AuthResponseData> => {
    try {
      const response = await apiClient.post<ApiResponse<AuthResponseData>>(
        '/auth/verify-otp',
        payload
      );
      return response.data.data;
    } catch (err: unknown) {
      if (import.meta.env.MODE !== 'auth-demo'
        || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
      // If mock/dev fallback:
      const mockRole = payload.email.includes('teacher') ? 'TEACHER' : 'STUDENT';
      return {
        token: `mock-${mockRole.toLowerCase()}`,
        user: {
          id: '00000000-0000-0000-0000-000000000001',
          username: payload.email.split('@')[0],
          email: payload.email,
          role: mockRole,
          fullName: 'Người dùng ' + payload.email.split('@')[0],
          lifecycleStatus: 'ACTIVE',
        },
      };
    }
  },

  // Reset verification grants a short-lived, single-use token; it does not sign the user in.
  verifyResetOtp: async (payload: VerifyOtpRequest): Promise<ResetVerificationData> => {
    const response = await apiClient.post<ApiResponse<ResetVerificationData>>('/auth/verify-otp', payload);
    if (!response.data.data?.resetToken) throw new Error('Reset verification did not return a token.');
    return response.data.data;
  },

  // Resend OTP
  resendOtp: async (payload: ResendOtpRequest): Promise<{ success: boolean; message: string }> => {
    try {
      const response = await apiClient.post<ApiResponse<{ message: string }>>(
        '/auth/resend-otp',
        payload
      );
      return { success: response.data.success, message: response.data.message || response.data.data.message };
    } catch (err: unknown) {
      if (import.meta.env.MODE !== 'auth-demo'
        || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
      return {
        success: true,
        message: 'Mã xác thực mới đã được gửi lại thành công.',
      };
    }
  },

  // Forgot password (request OTP)
  forgotPassword: async (payload: ForgotPasswordRequest): Promise<{ message: string }> => {
    const response = await apiClient.post<ApiResponse<{ message: string }>>('/auth/forgot-password', payload);
    return response.data.data;
  },

  // Reset password with a server-issued grant from verified OTP
  resetPassword: async (payload: ResetPasswordRequest): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post<ApiResponse<{ message: string }>>('/auth/reset-password', {
      email: payload.email, resetToken: payload.resetToken, newPassword: payload.newPassword,
    });
    return { success: response.data.success, message: response.data.message || response.data.data.message };
  },

  // Identity endpoint is available even when profile/dashboard persistence is unavailable.
  getCurrentUser: async (): Promise<AuthUser> => {
    const response = await apiClient.get<ApiResponse<AuthUser>>('/auth/me');
    return response.data.data;
  },

  refreshToken: async (refreshToken: string): Promise<AuthResponseData> => {
    const response = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/refresh-token', { refreshToken });
    return response.data.data;
  },

  logout: async (token: string, refreshToken: string): Promise<void> => {
    const request = (accessToken: string) => apiClient.post('/auth/logout', null, {
      headers: { Authorization: `Bearer ${accessToken}`, 'X-Refresh-Token': refreshToken },
    });
    try {
      await request(token);
    } catch (error) {
      if (!isAxiosError(error) || error.response?.status !== 401) throw error;
      const renewed = await authApi.refreshToken(refreshToken);
      await request(renewed.token);
    }
  },

  sessions: async (): Promise<AuthSession[]> => {
    const refreshToken = localStorage.getItem('algolearner_refresh_token');
    const response = await apiClient.get<ApiResponse<AuthSession[]>>('/auth/sessions', {
      headers: refreshToken ? { 'X-Refresh-Token': refreshToken } : {},
    });
    return response.data.data;
  },

  revokeSession: async (id: string): Promise<void> => {
    await apiClient.delete(`/auth/sessions/${encodeURIComponent(id)}`);
  },

  logoutAll: async (): Promise<void> => {
    await apiClient.post('/auth/logout-all');
  },

  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    const refreshToken = localStorage.getItem('algolearner_refresh_token');
    await apiClient.post('/auth/change-password', { currentPassword, newPassword }, {
      headers: refreshToken ? { 'X-Refresh-Token': refreshToken } : {},
    });
  },

  deactivate: async (password: string): Promise<void> => {
    await apiClient.post('/auth/deactivate', { password, confirm: true });
  },

  // Quick dev login using backend mock tokens (mock-student, mock-teacher, mock-admin)
  quickLogin: async (role: 'STUDENT' | 'TEACHER' | 'ADMIN'): Promise<AuthResponseData> => {
    if (!import.meta.env.DEV) throw new Error('Demo sign-in is only available in development.');
    const roleTokenMap: Record<'STUDENT' | 'TEACHER' | 'ADMIN', string> = {
      STUDENT: 'mock-student',
      TEACHER: 'mock-teacher',
      ADMIN: 'mock-admin',
    };

    const token = roleTokenMap[role];
    const user: AuthUser = {
      id: role === 'ADMIN' ? 'admin-id' : role === 'TEACHER' ? 'teacher-id' : 'student-id',
      username: role.toLowerCase(),
      email: `${role.toLowerCase()}@example.test`,
      role,
      fullName: role.charAt(0) + role.slice(1).toLowerCase(),
      lifecycleStatus: 'ACTIVE',
    };

    return { token, user };
  },
};
