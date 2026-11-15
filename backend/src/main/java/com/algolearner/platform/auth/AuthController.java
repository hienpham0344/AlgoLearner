package com.algolearner.platform.auth;

import static com.algolearner.platform.auth.AuthDtos.*;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.admin.AccountAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.List;
import java.security.Principal;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("internal-auth")
public class AuthController {
    private final AuthService service;
    private final AuthSessionService sessions;
    private final AccountAdministrationService administration;
    public AuthController(AuthService service, AuthSessionService sessions,
            AccountAdministrationService administration) {
        this.service = service; this.sessions = sessions; this.administration = administration;
    }

    @Operation(summary = "Register a Student or Teacher and email a verification code")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Pending account created and email accepted by SMTP"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid registration data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or username already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Email delivery is unavailable")
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Message>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.register(request)));
    }

    @Operation(summary = "Resend a registration or password-reset verification code")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Generic response for password reset; registration code accepted by SMTP"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Registration resend wait has not elapsed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Registration email delivery is unavailable")
    })
    @PostMapping("/resend-otp")
    public ApiResponse<ResendResult> resend(@Valid @RequestBody EmailRequest request) {
        try {
            return ApiResponse.success(service.resend(request));
        } catch (ApiException ex) {
            if (!"RESET_PASSWORD".equals(request.purpose()) || !concealedResetFailure(ex)) throw ex;
            return ApiResponse.success(new ResendResult(true, AuthService.RESET_REQUEST_MESSAGE));
        }
    }

    @Operation(summary = "Verify a registration or password-reset code")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account activated and JWT issued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Code is invalid, expired, or already used")
    })
    @PostMapping("/verify-otp")
    public ApiResponse<?> verify(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if ("RESET_PASSWORD".equals(request.purpose())) return ApiResponse.success(service.verifyReset(request));
        var result = service.verify(request, http.getHeader("User-Agent"));
        cookie(result, http, response);
        return ApiResponse.success(result);
    }

    @Operation(summary = "Sign in with email or username")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JWT issued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials or inactive account"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Correct password for a suspended account; suspension reason returned")
    })
    @PostMapping("/login")
    public ApiResponse<AuthResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        var result = service.login(request, http.getHeader("User-Agent"));
        cookie(result, http, response);
        return ApiResponse.success(result);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset OTP; response does not reveal account status or mail delivery")
    public ApiResponse<Message> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            return ApiResponse.success(service.forgotPassword(request));
        } catch (ApiException ex) {
            if (!concealedResetFailure(ex)) throw ex;
            return ApiResponse.success(AuthService.resetRequestMessage(request));
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Consume a verified reset grant and revoke all sessions")
    public ApiResponse<ResendResult> reset(@Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(service.resetPassword(request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password using current password; revoke other sessions")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<ResendResult> change(@Valid @RequestBody ChangePasswordRequest request, JwtAuthenticationToken auth) {
        return ApiResponse.success(service.changePassword(UUID.fromString(auth.getName()),
                UUID.fromString(auth.getToken().getClaimAsString("sid")), request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Rotate the HttpOnly refresh cookie and issue a 15-minute access token")
    public ApiResponse<AuthResult> refresh(@CookieValue(name = "algolearner_refresh", required = false) String token,
            HttpServletRequest http, HttpServletResponse response) {
        requireSameOriginHeader(http);
        var result = sessions.refresh(token);
        cookie(result, http, response);
        return ApiResponse.success(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the bearer session when supplied, otherwise the refresh-cookie session; expire the cookie")
    public ApiResponse<ResendResult> logout(@CookieValue(name = "algolearner_refresh", required = false) String token,
            HttpServletRequest http, HttpServletResponse response, Principal principal) {
        requireSameOriginHeader(http);
        if (principal instanceof JwtAuthenticationToken auth) {
            sessions.logoutCurrent(accountId(auth), sessionId(auth));
        } else {
            sessions.logout(token);
        }
        expireCookie(http, response);
        return ApiResponse.success(new ResendResult(true, "Signed out"));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List active sessions belonging to the authenticated account")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<SessionView>> sessions(JwtAuthenticationToken auth) {
        return ApiResponse.success(sessions.list(accountId(auth), sessionId(auth)));
    }

    @GetMapping("/me")
    @Operation(summary = "Read the current authenticated account")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<AuthUser> me(JwtAuthenticationToken auth) {
        return ApiResponse.success(service.me(accountId(auth)));
    }

    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke another session belonging to this account")
    @SecurityRequirement(name = "bearerAuth")
    public void revokeSession(JwtAuthenticationToken auth, @PathVariable UUID id) {
        sessions.revoke(accountId(auth), sessionId(auth), id);
    }

    @PostMapping("/logout-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke every session and expire the refresh cookie")
    @SecurityRequirement(name = "bearerAuth")
    public void logoutAll(JwtAuthenticationToken auth, HttpServletRequest http, HttpServletResponse response) {
        sessions.logoutAll(accountId(auth));
        expireCookie(http, response);
    }

    @PostMapping("/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate the current Student or Teacher account")
    @SecurityRequirement(name = "bearerAuth")
    public void deactivate(JwtAuthenticationToken auth, @Valid @RequestBody DeactivateRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        administration.deactivate(accountId(auth), request.currentPassword(), Boolean.TRUE.equals(request.confirmed()));
        expireCookie(http, response);
    }

    private void cookie(AuthResult result, HttpServletRequest http, HttpServletResponse response) {
        var cookie = ResponseCookie.from("algolearner_refresh", result.refreshToken()).path("/api/v1/auth")
                .httpOnly(true).secure(http.isSecure()).sameSite("Strict");
        if (result.rememberMe()) cookie.maxAge(result.refreshExpiresIn());
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Set-Cookie", cookie.build().toString());
    }

    private UUID accountId(JwtAuthenticationToken auth) { return UUID.fromString(auth.getName()); }
    private UUID sessionId(JwtAuthenticationToken auth) { return UUID.fromString(auth.getToken().getClaimAsString("sid")); }

    private boolean concealedResetFailure(ApiException ex) {
        return ErrorCode.OTP_RATE_LIMITED.name().equals(ex.getErrorCode())
                || ErrorCode.MAIL_NOT_CONFIGURED.name().equals(ex.getErrorCode())
                || ErrorCode.MAIL_DELIVERY_FAILED.name().equals(ex.getErrorCode());
    }

    private void expireCookie(HttpServletRequest http, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Set-Cookie", ResponseCookie.from("algolearner_refresh", "").path("/api/v1/auth")
                .httpOnly(true).secure(http.isSecure()).sameSite("Strict").maxAge(0).build().toString());
    }

    private void requireSameOriginHeader(HttpServletRequest request) {
        // Cross-origin forms cannot set this header. Do not allow credentialed CORS for untrusted origins.
        if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With")))
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
    }
}
