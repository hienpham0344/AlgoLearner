package com.algolearner.platform.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    @Schema(description = "Account registration request payload")
    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid email address")
            @Schema(example = "student@example.com")
            String email,

            @NotBlank(message = "Username is required")
            @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,50}$", message = "Username must be 3-50 alphanumeric characters")
            @Schema(example = "student_algo")
            String username,

            @NotBlank(message = "Password is required")
            @Schema(example = "Password123")
            String password,

            @Schema(example = "STUDENT", description = "Target role: STUDENT or TEACHER only")
            AccountRole role,

            @NotNull(message = "termsAccepted is required")
            @Schema(example = "true")
            Boolean termsAccepted,

            @NotNull(message = "privacyAccepted is required")
            @Schema(example = "true")
            Boolean privacyAccepted
    ) {}

    @Schema(description = "Account login request payload")
    public record LoginRequest(
            @NotBlank(message = "Username or email is required")
            @Schema(example = "student@example.com")
            String usernameOrEmail,

            @NotBlank(message = "Password is required")
            @Schema(example = "Password123")
            String password,

            @Schema(example = "false", description = "Extends refresh token lifetime from 7 days to 30 days")
            Boolean rememberMe
    ) {}

    @Schema(description = "Verify OTP code payload")
    public record VerifyOtpRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid email address")
            @Schema(example = "student@example.com")
            String email,

            @NotBlank(message = "OTP code is required")
            @Pattern(regexp = "^\\d{6}$", message = "OTP code must be exactly 6 digits")
            @Schema(example = "123456")
            String code,

            @NotNull(message = "OTP purpose is required")
            @Schema(example = "REGISTER")
            OtpPurpose purpose
    ) {}

    @Schema(description = "Resend OTP request payload")
    public record ResendOtpRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid email address")
            @Schema(example = "student@example.com")
            String email,

            @NotNull(message = "OTP purpose is required")
            @Schema(example = "REGISTER")
            OtpPurpose purpose
    ) {}

    @Schema(description = "Forgot password request payload")
    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid email address")
            @Schema(example = "student@example.com")
            String email
    ) {}

    @Schema(description = "Reset password request payload")
    public record ResetPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid email address")
            @Schema(example = "student@example.com")
            String email,

            @NotBlank(message = "Reset token is required")
            @Schema(example = "reset-token-xyz")
            String resetToken,

            @NotBlank(message = "New password is required")
            @Schema(example = "NewPassword123")
            String newPassword
    ) {}

    @Schema(description = "Change password request payload (authenticated)")
    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            @Schema(example = "OldPassword123")
            String currentPassword,

            @NotBlank(message = "New password is required")
            @Schema(example = "NewPassword123")
            String newPassword
    ) {}

    @Schema(description = "Refresh token request payload")
    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token is required")
            @Schema(example = "refresh-token-xyz")
            String refreshToken
    ) {}

    @Schema(description = "Account deactivation request payload")
    public record DeactivateAccountRequest(
            @NotBlank(message = "Password is required to deactivate account")
            @Schema(example = "Password123")
            String password,

            @NotNull(message = "Confirmation flag is required")
            @Schema(example = "true")
            Boolean confirm
    ) {}

    @Schema(description = "Admin suspend account request payload")
    public record SuspendAccountRequest(
            @NotBlank(message = "Reason is required for account suspension")
            @Schema(example = "Violating platform code of conduct")
            String reason,

            @Schema(description = "Optional end time; null means indefinite suspension")
            Instant endsAt
    ) {}

    @Schema(description = "Authenticated user detail response")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthUserResponse(
            UUID id,
            String username,
            String email,
            AccountRole role,
            String lifecycleStatus,
            Instant termsAcceptedAt,
            Instant privacyAcceptedAt
    ) {}

    @Schema(description = "Authentication token response")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResponse(
            String token,
            String refreshToken,
            AuthUserResponse user,
            long expiresIn
    ) {}

    @Schema(description = "Password reset verification response")
    public record ResetVerificationResponse(
            String resetToken
    ) {}

    @Schema(description = "Active user device session response")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionResponse(
            UUID id,
            String deviceLabel,
            Instant createdAt,
            Instant lastSeenAt,
            Instant expiresAt,
            boolean current
    ) {}

    @Schema(description = "Simple operation status message")
    public record MessageResponse(
            String message
    ) {}
}

