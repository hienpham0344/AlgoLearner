import { apiClient, logoutSession } from '@/services/apiClient';
import { getApiSource } from '@/services/apiSource';
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

export interface SessionView {
  id: string;
  createdAt: string;
  expiresAt: string;
  rememberMe: boolean;
  current: boolean;
  userAgent: string | null;
}

export const authApi = {
  // Login with credentials
  login: async (payload: LoginRequest): Promise<AuthResponseData> => {
    const response = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/login', payload);
    return response.data.data;
  },

  // Register new account (Student or Teacher)
  register: async (payload: RegisterRequest): Promise<{ email: string; message: string }> => {
    const response = await apiClient.post<ApiResponse<{ email: string; message: string }>>('/auth/register', payload, { timeout: 32_000 });
    return response.data.data;
  },

  // Verify OTP (purpose: REGISTER | RESET_PASSWORD)
  verifyOtp: async (payload: VerifyOtpRequest): Promise<AuthResponseData> => {
    const response = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/verify-otp', payload);
    return response.data.data;
  },

  // Reset verification grants a short-lived, single-use token; it does not sign the user in.
  verifyResetOtp: async (payload: VerifyOtpRequest): Promise<ResetVerificationData> => {
    const response = await apiClient.post<ApiResponse<ResetVerificationData>>('/auth/verify-otp', payload);
    if (!response.data.data?.resetToken) throw new Error('Reset verification did not return a token.');
    return response.data.data;
  },

  // Resend OTP
  resendOtp: async (payload: ResendOtpRequest): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>('/auth/resend-otp', payload);
    return response.data.data;
  },

  // Forgot password (request OTP)
  forgotPassword: async (payload: ForgotPasswordRequest): Promise<{ message: string }> => {
    const response = await apiClient.post<ApiResponse<{ message: string }>>('/auth/forgot-password', payload);
    return response.data.data;
  },

  // Reset password with a server-issued grant from verified OTP
  resetPassword: async (payload: ResetPasswordRequest): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>('/auth/reset-password', payload);
    return response.data.data;
  },

  // Get current user profile from /users/me/profile (matching live backend!)
  getCurrentUser: async (): Promise<AuthUser> => {
    const response = await apiClient.get<ApiResponse<AuthUser>>('/auth/me');
    return response.data.data;
  },

  logout: logoutSession,

  sessions: async (): Promise<SessionView[]> => {
    const response = await apiClient.get<ApiResponse<SessionView[]>>('/auth/sessions');
    return response.data.data;
  },

  revokeSession: async (id: string): Promise<void> => {
    await apiClient.delete(`/auth/sessions/${encodeURIComponent(id)}`);
  },

  logoutAll: async (): Promise<void> => {
    await apiClient.post('/auth/logout-all', undefined, {
      withCredentials: true,
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
  },

  deactivate: async (currentPassword: string): Promise<void> => {
    await apiClient.post('/auth/deactivate', { currentPassword, confirmed: true }, { withCredentials: true });
  },

  changePassword: async (payload: { currentPassword: string; newPassword: string; confirmPassword: string }) => {
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>('/auth/change-password', payload);
    return response.data.data;
  },

  // Quick dev login using backend mock tokens (mock-student, mock-teacher, mock-admin)
  quickLogin: async (role: 'STUDENT' | 'TEACHER' | 'ADMIN'): Promise<AuthResponseData> => {
    if (!import.meta.env.DEV || getApiSource() !== 'mock') throw new Error('Demo sign-in requires an offline backend.');
    return authApi.login({ usernameOrEmail: role === 'STUDENT' ? 'learner@example.test' : role.toLowerCase(), password: 'DemoPass123!', rememberMe: false });
  },
};
