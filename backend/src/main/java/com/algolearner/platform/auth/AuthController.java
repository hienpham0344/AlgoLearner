package com.algolearner.platform.auth;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.algolearner.platform.auth.AuthDtos.*;

@RestController
@org.springframework.context.annotation.Profile("!mock")
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication & Accounts", description = "SRS 4.1: Registration, Login, OTP, Passwords, and Sessions")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register new Student or Teacher account (FR-ACC-001)")
    public ResponseEntity<ApiResponse<AuthUserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthUserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account registered successfully. An OTP has been sent to your email", response));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP code for REGISTER or RESET_PASSWORD (FR-ACC-003, FR-ACC-004)")
    public ResponseEntity<ApiResponse<Object>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        Object response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP code with 60s cooldown (FR-ACC-004)")
    public ResponseEntity<ApiResponse<MessageResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        MessageResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with username/email and password (FR-AUTH-001, FR-AUTH-002)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String deviceLabel = servletRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, deviceLabel);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset OTP (FR-AUTH-003)")
    public ResponseEntity<ApiResponse<MessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        MessageResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password reset with verified token (FR-AUTH-003)")
    public ResponseEntity<ApiResponse<MessageResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        MessageResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh JWT access token with refresh token (FR-AUTH-005)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change password for authenticated user (FR-AUTH-004)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MessageResponse>> changePassword(
            Authentication authentication,
            @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID accountId = id(authentication);
        MessageResponse response = authService.changePassword(accountId, request, currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List active login sessions for current user (FR-AUTH-006)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<SessionResponse>>> listSessions(
            Authentication authentication,
            @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken
    ) {
        UUID accountId = id(authentication);
        List<SessionResponse> sessions = authService.listSessions(accountId, currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout current session (FR-AUTH-006)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MessageResponse>> logout(
            Authentication authentication,
            @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken
    ) {
        UUID accountId = id(authentication);
        MessageResponse response = authService.logout(accountId, currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout all sessions from all devices (FR-AUTH-006)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MessageResponse>> logoutAll(Authentication authentication) {
        UUID accountId = id(authentication);
        MessageResponse response = authService.logoutAll(accountId);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revoke a specific session (FR-AUTH-006)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MessageResponse>> revokeSession(
            Authentication authentication,
            @PathVariable UUID sessionId
    ) {
        UUID accountId = id(authentication);
        MessageResponse response = authService.revokeSession(accountId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @PostMapping("/deactivate")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary = "Self-deactivate user account (FR-ACC-006)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MessageResponse>> deactivate(
            Authentication authentication,
            @Valid @RequestBody DeactivateAccountRequest request
    ) {
        UUID accountId = id(authentication);
        MessageResponse response = authService.deactivateAccount(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current authenticated user info", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuthUserResponse>> me(Authentication authentication) {
        UUID accountId = id(authentication);
        AuthUserResponse response = authService.getCurrentUser(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}

