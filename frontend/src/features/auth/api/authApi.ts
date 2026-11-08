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

export const authApi = {
  // Login with credentials
  login: async (payload: LoginRequest): Promise<AuthResponseData> => {
    try {
      const response = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/login', payload);
      return response.data.data;
    } catch (err: unknown) {
      if (!import.meta.env.DEV || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
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
      const response = await apiClient.post<ApiResponse<{ email: string; message: string }>>(
        '/auth/register',
        payload
      );
      return response.data.data;
    } catch (err: unknown) {
      if (!import.meta.env.DEV || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
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
      if (!import.meta.env.DEV || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
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
      const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>(
        '/auth/resend-otp',
        payload
      );
      return response.data.data;
    } catch (err: unknown) {
      if (!import.meta.env.DEV || (isAxiosError(err) && err.response && err.response.status < 500)) throw err;
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
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>('/auth/reset-password', payload);
    return response.data.data;
  },

  // Get current user profile from /users/me/profile (matching live backend!)
  getCurrentUser: async (): Promise<AuthUser> => {
    const response = await apiClient.get<ApiResponse<AuthUser>>('/users/me/profile');
    return response.data.data;
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
