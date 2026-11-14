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
    public record EmailRequest(@Email String email, String usernameOrEmail, @NotBlank String purpose) {
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
    }
    public record VerifyOtpRequest(@Email String email, String usernameOrEmail,
            @NotBlank String code, @NotBlank String purpose) {
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
    }
    public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password, boolean rememberMe) { }
    public record ForgotPasswordRequest(@Email String email, String usernameOrEmail) {
        public ForgotPasswordRequest(String email) { this(email, null); }
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
    }
    public record ResetPasswordRequest(@Email String email, String usernameOrEmail, @NotBlank String resetToken,
            @NotBlank @Size(min = 8, max = 72) String newPassword, @NotBlank String confirmPassword) {
        public String identity() { return usernameOrEmail == null || usernameOrEmail.isBlank() ? email : usernameOrEmail; }
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
