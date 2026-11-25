package com.algolearner.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() { }

    public record RegisterRequest(@NotBlank @Email String email,
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 72, message = "Use between 8 and 72 characters.")
            @Pattern(regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*",
                    message = "Use at least one letter and one number.") String password,
            @NotBlank String confirmPassword,
            AccountRole role,
            @NotNull Boolean termsAccepted,
            @NotNull Boolean privacyAccepted) { }
    private static IdentityType namespace(String usernameOrEmail, IdentityType identityType) {
        return usernameOrEmail == null || usernameOrEmail.isBlank() ? IdentityType.EMAIL
                : identityType == null ? IdentityType.AUTO : identityType;
    }
    public record EmailRequest(@Email String email, String usernameOrEmail, @NotBlank String purpose,
            IdentityType identityType) {
        public EmailRequest(String email, String usernameOrEmail, String purpose) { this(email, usernameOrEmail, purpose, null); }
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
        public IdentityType namespace() { return AuthDtos.namespace(usernameOrEmail, identityType); }
    }
    public record VerifyOtpRequest(@Email String email, String usernameOrEmail,
            @NotBlank String code, @NotBlank String purpose, IdentityType identityType) {
        public VerifyOtpRequest(String email, String usernameOrEmail, String code, String purpose) { this(email, usernameOrEmail, code, purpose, null); }
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
        public IdentityType namespace() { return AuthDtos.namespace(usernameOrEmail, identityType); }
    }
    public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password, boolean rememberMe,
            IdentityType identityType) {
        public LoginRequest(String usernameOrEmail, String password, boolean rememberMe) { this(usernameOrEmail, password, rememberMe, null); }
    }
    public record ForgotPasswordRequest(@Email String email, String usernameOrEmail, IdentityType identityType) {
        public ForgotPasswordRequest(String email) { this(email, null, null); }
        public ForgotPasswordRequest(String email, String usernameOrEmail) { this(email, usernameOrEmail, null); }
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
        public IdentityType namespace() { return AuthDtos.namespace(usernameOrEmail, identityType); }
    }
    public record ResetPasswordRequest(@Email String email, String usernameOrEmail, @NotBlank String resetToken,
            @NotBlank @Size(min = 8, max = 72) String newPassword, @NotBlank String confirmPassword,
            IdentityType identityType) {
        public ResetPasswordRequest(String email, String usernameOrEmail, String resetToken, String newPassword, String confirmPassword) {
            this(email, usernameOrEmail, resetToken, newPassword, confirmPassword, null);
        }
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
        public IdentityType namespace() { return AuthDtos.namespace(usernameOrEmail, identityType); }
    }
    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword, @NotBlank String confirmPassword) { }
    public record ResetVerification(String resetToken) { }
    public record Message(String email, String message) { }
    public record ResendResult(boolean success, String message) { }
    public record AuthUser(UUID id, String username, String email, AccountRole role, String lifecycleStatus) { }
    public record AuthResult(String token, AuthUser user, long expiresIn,
            @com.fasterxml.jackson.annotation.JsonIgnore String refreshToken,
            @com.fasterxml.jackson.annotation.JsonIgnore long refreshExpiresIn,
            @com.fasterxml.jackson.annotation.JsonIgnore boolean rememberMe) { }
    public record SessionView(UUID id, Instant createdAt, Instant expiresAt,
            boolean rememberMe, boolean current, String userAgent) { }
    public record DeactivateRequest(@NotBlank String currentPassword, @NotNull Boolean confirmed) { }
}
