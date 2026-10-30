export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export type AccountLifecycleStatus =
  | 'PENDING_VERIFICATION'
  | 'ACTIVE'
  | 'DEACTIVATED'
  | 'TEMPORARILY_LOCKED'
  | 'SUSPENDED';

export type OtpPurpose = 'REGISTER' | 'RESET_PASSWORD';

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  fullName?: string;
  avatarUrl?: string | null;
  lifecycleStatus?: AccountLifecycleStatus;
  termsAcceptedAt?: string;
  privacyAcceptedAt?: string;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
  rememberMe?: boolean;
}

export interface RegisterRequest {
  email: string;
  username: string;
  password: string;
  confirmPassword?: string;
  role: 'STUDENT' | 'TEACHER';
  termsAccepted: boolean;
  privacyAccepted: boolean;
}

export interface VerifyOtpRequest {
  email: string;
  code: string;
  purpose: OtpPurpose;
}

export interface ResendOtpRequest {
  email: string;
  purpose: OtpPurpose;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  email: string;
  code: string;
  newPassword: string;
  confirmPassword?: string;
}

export interface AuthResponseData {
  token: string;
  user: AuthUser;
  expiresIn?: number;
}
