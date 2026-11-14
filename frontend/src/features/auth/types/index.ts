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

export type AuthIdentity = { email: string; usernameOrEmail?: never } | { usernameOrEmail: string; email?: never };

export type VerifyOtpRequest = AuthIdentity & {
  code: string;
  purpose: OtpPurpose;
};

export type ResendOtpRequest = AuthIdentity & {
  purpose: OtpPurpose;
};

export type ForgotPasswordRequest = AuthIdentity;

export type ResetPasswordRequest = AuthIdentity & {
  resetToken: string;
  newPassword: string;
  confirmPassword?: string;
};

export interface ResetVerificationData {
  resetToken: string;
}

export interface AuthResponseData {
  token: string;
  user: AuthUser;
  expiresIn?: number;
}
