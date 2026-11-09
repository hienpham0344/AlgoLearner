package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.algolearner.platform.auth.AuthDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OtpChallengeRepository otpRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private AccountRestrictionRepository restrictionRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private OtpService otpService;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthAuditPort auditPort;

    private Clock clock;
    private Instant now;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-10T12:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        authService = new AuthService(
                accountRepository,
                otpRepository,
                sessionRepository,
                restrictionRepository,
                passwordService,
                otpService,
                tokenService,
                auditPort,
                clock
        );
    }

    @Test
    @DisplayName("FR-ACC-001 AC-01 & AC-02: Public registration creates account in PENDING_VERIFICATION with default STUDENT role")
    void registerStudentSuccess() {
        RegisterRequest request = new RegisterRequest(
                "student@example.com",
                "student1",
                "Password123",
                null, // No role -> defaults to STUDENT
                true,
                true
        );

        when(accountRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(accountRepository.existsByUsernameIgnoreCase("student1")).thenReturn(false);
        when(passwordService.hash("Password123")).thenReturn("hashed_pass");
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthUserResponse response = authService.register(request);

        assertThat(response.username()).isEqualTo("student1");
        assertThat(response.email()).isEqualTo("student@example.com");
        assertThat(response.role()).isEqualTo(AccountRole.STUDENT);
        assertThat(response.lifecycleStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION.name());

        verify(otpService).generateAndSendOtp(any(AccountEntity.class), eq(OtpPurpose.REGISTER));
        verify(auditPort).record(any(), any(), eq(AuditActorType.USER), eq("REGISTER"), eq(AuditOutcome.SUCCESS), eq(now));
    }

    @Test
    @DisplayName("FR-ACC-001 AC-05: Public registration with ADMIN role must be rejected")
    void registerAdminRejected() {
        RegisterRequest request = new RegisterRequest(
                "admin@example.com",
                "admin_user",
                "Password123",
                AccountRole.ADMIN,
                true,
                true
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN_OPERATION.getCode()));
    }

    @Test
    @DisplayName("FR-ACC-001 AC-04: Registration without accepting terms must be rejected")
    void registerWithoutTermsRejected() {
        RegisterRequest request = new RegisterRequest(
                "student@example.com",
                "student1",
                "Password123",
                AccountRole.STUDENT,
                false, // Terms not accepted
                true
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.TERMS_NOT_ACCEPTED.getCode()));
    }

    @Test
    @DisplayName("FR-ACC-001 AC-03: Duplicate email must be rejected")
    void registerDuplicateEmailRejected() {
        RegisterRequest request = new RegisterRequest(
                "student@example.com",
                "student1",
                "Password123",
                AccountRole.STUDENT,
                true,
                true
        );

        when(accountRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS.getCode()));
    }

    @Test
    @DisplayName("FR-ACC-003 AC-02: Valid REGISTER OTP activates account and transitions status to ACTIVE")
    void verifyRegisterOtpSuccess() {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("student1")
                .email("student@example.com")
                .role(AccountRole.STUDENT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();

        when(accountRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(account));
        when(tokenService.generateRawRefreshToken()).thenReturn("raw_refresh_token");
        when(tokenService.hashRefreshToken("raw_refresh_token")).thenReturn("hashed_refresh_token");
        when(tokenService.generateAccessToken(account)).thenReturn("mock-student1");

        VerifyOtpRequest request = new VerifyOtpRequest("student@example.com", "123456", OtpPurpose.REGISTER);
        Object response = authService.verifyOtp(request);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getEmailVerifiedAt()).isEqualTo(now);
        verify(accountRepository).save(account);

        assertThat(response).isInstanceOf(AuthResponse.class);
        AuthResponse authResponse = (AuthResponse) response;
        assertThat(authResponse.token()).isEqualTo("mock-student1");
        assertThat(authResponse.user().lifecycleStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("FR-AUTH-001 AC-01: Login with valid credentials creates session and issues tokens")
    void loginSuccess() {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("student1")
                .email("student@example.com")
                .passwordHash("hashed_pass")
                .role(AccountRole.STUDENT)
                .status(AccountStatus.ACTIVE)
                .failedLoginCount(0)
                .build();

        when(accountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("student1", "student1"))
                .thenReturn(Optional.of(account));
        when(restrictionRepository.findActiveRestrictions(account.getId(), now)).thenReturn(Collections.emptyList());
        when(passwordService.matches("Password123", "hashed_pass")).thenReturn(true);
        when(tokenService.generateRawRefreshToken()).thenReturn("raw_refresh");
        when(tokenService.hashRefreshToken("raw_refresh")).thenReturn("hashed_refresh");
        when(tokenService.generateAccessToken(account)).thenReturn("mock-student1");

        LoginRequest request = new LoginRequest("student1", "Password123", false);
        AuthResponse response = authService.login(request, "Chrome/Linux");

        assertThat(response.token()).isEqualTo("mock-student1");
        assertThat(response.refreshToken()).isEqualTo("raw_refresh");
        assertThat(account.getFailedLoginCount()).isEqualTo(0);

        verify(sessionRepository).save(any(AuthSessionEntity.class));
        verify(auditPort).record(eq(account.getId()), eq(account.getId()), eq(AuditActorType.USER), eq("LOGIN"), eq(AuditOutcome.SUCCESS), eq(now));
    }

    @Test
    @DisplayName("FR-AUTH-002 AC-01: 5 consecutive failed logins trigger 15-minute AUTO_LOCK")
    void loginFailedLockAfterFiveAttempts() {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("student1")
                .email("student@example.com")
                .passwordHash("hashed_pass")
                .role(AccountRole.STUDENT)
                .status(AccountStatus.ACTIVE)
                .failedLoginCount(4) // 4th previous failure
                .build();

        when(accountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("student1", "student1"))
                .thenReturn(Optional.of(account));
        when(restrictionRepository.findActiveRestrictions(account.getId(), now)).thenReturn(Collections.emptyList());
        when(passwordService.matches("WrongPass1", "hashed_pass")).thenReturn(false);

        LoginRequest request = new LoginRequest("student1", "WrongPass1", false);

        assertThatThrownBy(() -> authService.login(request, "Chrome"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode()));

        ArgumentCaptor<AccountRestrictionEntity> restrictionCaptor = ArgumentCaptor.forClass(AccountRestrictionEntity.class);
        verify(restrictionRepository).save(restrictionCaptor.capture());

        AccountRestrictionEntity lock = restrictionCaptor.getValue();
        assertThat(lock.getKind()).isEqualTo(RestrictionKind.AUTO_LOCK);
        assertThat(lock.getStartsAt()).isEqualTo(now);
        assertThat(lock.getEndsAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("FR-AUTH-001 AC-04: Login rejected when account is PENDING_VERIFICATION")
    void loginPendingVerificationRejected() {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("student1")
                .email("student@example.com")
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();

        when(accountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("student1", "student1"))
                .thenReturn(Optional.of(account));
        when(restrictionRepository.findActiveRestrictions(account.getId(), now)).thenReturn(Collections.emptyList());

        LoginRequest request = new LoginRequest("student1", "Password123", false);

        assertThatThrownBy(() -> authService.login(request, "Chrome"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_PENDING_VERIFICATION.getCode()));
    }

    @Test
    @DisplayName("FR-AUTH-003 AC-03: Reset password revokes ALL active sessions")
    void resetPasswordRevokesAllSessions() {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("student1")
                .email("student@example.com")
                .passwordHash("old_hash")
                .build();

        when(accountRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(account));
        when(tokenService.parseAndValidateResetToken("valid_reset_token", now)).thenReturn(account.getId());
        when(passwordService.hash("NewPassword123")).thenReturn("new_hash");

        ResetPasswordRequest request = new ResetPasswordRequest("student@example.com", "valid_reset_token", "NewPassword123");
        MessageResponse response = authService.resetPassword(request);

        assertThat(response.message()).contains("Password has been reset");
        assertThat(account.getPasswordHash()).isEqualTo("new_hash");
        verify(sessionRepository).revokeAllByAccountId(account.getId(), now);
    }

    @Test
    @DisplayName("FR-AUTH-004 AC-03 & AC-04: Change password keeps current session and revokes others")
    void changePasswordKeepsCurrentSession() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = AccountEntity.builder()
                .id(accountId)
                .passwordHash("current_hash")
                .build();

        AuthSessionEntity currentSession = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .refreshTokenHash("current_token_hash")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordService.matches("OldPassword123", "current_hash")).thenReturn(true);
        when(passwordService.hash("NewPassword123")).thenReturn("new_hash");
        when(tokenService.hashRefreshToken("current_refresh_raw")).thenReturn("current_token_hash");
        when(sessionRepository.findByRefreshTokenHash("current_token_hash")).thenReturn(Optional.of(currentSession));

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword123");
        authService.changePassword(accountId, request, "current_refresh_raw");

        verify(sessionRepository).revokeOtherSessions(accountId, currentSession.getId(), now);
    }

    @Test
    @DisplayName("FR-AUTH-006 AC-06: Revoking a session belonging to another user must be forbidden")
    void revokeOtherUserSessionForbidden() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AccountEntity otherAccount = AccountEntity.builder().id(otherUserId).build();
        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(sessionId)
                .account(otherAccount)
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.revokeSession(userId, sessionId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN_OPERATION.getCode()));
    }

    @Test
    @DisplayName("FR-ACC-006 AC-06 & AC-07: Self-deactivation marks account DEACTIVATED and revokes all sessions")
    void selfDeactivateSuccess() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = AccountEntity.builder()
                .id(accountId)
                .passwordHash("hashed_pass")
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordService.matches("Password123", "hashed_pass")).thenReturn(true);

        DeactivateAccountRequest request = new DeactivateAccountRequest("Password123", true);
        authService.deactivateAccount(accountId, request);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        verify(accountRepository).save(account);
        verify(sessionRepository).revokeAllByAccountId(accountId, now);
        verify(auditPort).record(eq(accountId), eq(accountId), eq(AuditActorType.USER), eq("ACCOUNT_DEACTIVATED"), eq(AuditOutcome.SUCCESS), eq(now));
    }
}

