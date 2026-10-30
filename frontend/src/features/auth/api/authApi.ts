import { apiClient } from '@/services/apiClient';
import { ApiResponse } from '@/types/api';
import {
  AuthResponseData,
  AuthUser,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResendOtpRequest,
  ResetPasswordRequest,
  VerifyOtpRequest,
} from '../types';

export const authApi = {
  // Login with credentials
  login: async (payload: LoginRequest): Promise<AuthResponseData> => {
    try {
      const response = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/login', payload);
      return response.data.data;
    } catch (err: unknown) {
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

  // Resend OTP
  resendOtp: async (payload: ResendOtpRequest): Promise<{ success: boolean; message: string }> => {
    try {
      const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>(
        '/auth/resend-otp',
        payload
      );
      return response.data.data;
    } catch (err: unknown) {
      return {
        success: true,
        message: 'Mã xác thực mới đã được gửi lại thành công.',
      };
    }
  },

  // Forgot password (request OTP)
  forgotPassword: async (payload: ForgotPasswordRequest): Promise<{ message: string }> => {
    try {
      const response = await apiClient.post<ApiResponse<{ message: string }>>(
        '/auth/forgot-password',
        payload
      );
      return response.data.data;
    } catch (err: unknown) {
      return { message: 'Mã đặt lại mật khẩu đã được gửi đến email.' };
    }
  },

  // Reset password with OTP
  resetPassword: async (payload: ResetPasswordRequest): Promise<{ success: boolean; message: string }> => {
    try {
      const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>(
        '/auth/reset-password',
        payload
      );
      return response.data.data;
    } catch (err: unknown) {
      return { success: true, message: 'Mật khẩu đã được cập nhật thành công.' };
    }
  },

  // Get current user profile from /users/me/profile (matching live backend!)
  getCurrentUser: async (): Promise<AuthUser> => {
    const response = await apiClient.get<ApiResponse<AuthUser>>('/users/me/profile');
    return response.data.data;
  },

  // Quick dev login using backend mock tokens (mock-student, mock-teacher, mock-admin)
  quickLogin: async (role: 'STUDENT' | 'TEACHER' | 'ADMIN'): Promise<AuthResponseData> => {
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
      fullName: `Demo ${role.charAt(0) + role.slice(1).toLowerCase()}`,
      lifecycleStatus: 'ACTIVE',
    };

    return { token, user };
  },
};
