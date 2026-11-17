package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.algolearner.platform.auth.AuthDtos.*;

@Service
@org.springframework.context.annotation.Profile("!mock")
@Transactional(readOnly = true)
public class AuthService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration AUTO_LOCK_DURATION = Duration.ofMinutes(15);

    private final AccountRepository accountRepository;
    private final OtpChallengeRepository otpRepository;
    private final AuthSessionRepository sessionRepository;
    private final AccountRestrictionRepository restrictionRepository;
    private final PasswordService passwordService;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final AuthAuditPort auditPort;
    private final Clock clock;

    public AuthService(
            AccountRepository accountRepository,
            OtpChallengeRepository otpRepository,
            AuthSessionRepository sessionRepository,
            AccountRestrictionRepository restrictionRepository,
            PasswordService passwordService,
            OtpService otpService,
            TokenService tokenService,
            AuthAuditPort auditPort,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.otpRepository = otpRepository;
        this.sessionRepository = sessionRepository;
        this.restrictionRepository = restrictionRepository;
        this.passwordService = passwordService;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * FR-ACC-001: Register public student/teacher account.
     * Starts in PENDING_VERIFICATION state; sends REGISTER OTP.
     */
    @Transactional
    public AuthUserResponse register(RegisterRequest request) {
        Instant now = clock.instant();

        // AC-05: Reject public registration with ADMIN role
        if (request.role() == AccountRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Public registration cannot assign ADMIN role");
        }

        // AC-04: Mandatory acceptance of terms and privacy policy
        if (!Boolean.TRUE.equals(request.termsAccepted()) || !Boolean.TRUE.equals(request.privacyAccepted())) {
            throw new ApiException(ErrorCode.TERMS_NOT_ACCEPTED,
                    "You must accept Terms of Service and Privacy Policy to register");
        }

        // AC-03: Email and username must be unique
        String email = request.email().strip().toLowerCase();
        String username = request.username().strip();

        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered");
        }
        if (accountRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(ErrorCode.USERNAME_ALREADY_EXISTS, "Username is already taken");
        }

        // Validate and hash password (FR-ACC-005)
        passwordService.validatePassword(request.password());
        String passwordHash = passwordService.hash(request.password());

        // AC-02: Default role is STUDENT if not provided
        AccountRole assignedRole = request.role() != null ? request.role() : AccountRole.STUDENT;

        // AC-01: Account created in PENDING_VERIFICATION
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .role(assignedRole)
                .status(AccountStatus.PENDING_VERIFICATION)
                .failedLoginCount(0)
                .termsAcceptedAt(now)
                .privacyAcceptedAt(now)
                .build();

        AccountEntity saved = accountRepository.save(account);

        // FR-ACC-003: Dispatch REGISTER OTP
        otpService.generateAndSendOtp(saved, OtpPurpose.REGISTER);

        auditPort.record(saved.getId(), saved.getId(), AuditActorType.USER, "REGISTER", AuditOutcome.SUCCESS, now);

        return toUserResponse(saved);
    }

    /**
     * FR-ACC-003 & FR-ACC-004: Verify OTP.
     * When REGISTER: activates account and returns AuthResponse.
     * When RESET_PASSWORD: returns single-use reset grant token.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Object verifyOtp(VerifyOtpRequest request) {
        Instant now = clock.instant();
        String email = request.email().strip().toLowerCase();

        AccountEntity account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found with email: " + email));

        otpService.verifyOtp(account, request.code(), request.purpose());

        if (request.purpose() == OtpPurpose.REGISTER) {
            // Activate account
            account.setStatus(AccountStatus.ACTIVE);
            account.setEmailVerifiedAt(now);
            accountRepository.save(account);

            auditPort.record(account.getId(), account.getId(), AuditActorType.USER, "VERIFY_REGISTER_OTP", AuditOutcome.SUCCESS, now);

            // Create initial session for seamless post-verification login
            return createSessionAndTokenResponse(account, false, "Web Browser", now);
        } else {
            // RESET_PASSWORD grant
            Instant resetExpiresAt = now.plus(TokenService.RESET_TOKEN_EXPIRY);
            String resetToken = tokenService.generateResetToken(account.getId(), resetExpiresAt);
            account.setResetTokenHash(tokenService.hashResetToken(resetToken));
            account.setResetTokenExpiresAt(resetExpiresAt);
            accountRepository.save(account);

            auditPort.record(account.getId(), account.getId(), AuditActorType.USER, "VERIFY_RESET_OTP", AuditOutcome.SUCCESS, now);

            return new ResetVerificationResponse(resetToken);
        }
    }

    /**
     * FR-ACC-004: Resend OTP code.
     */
    @Transactional
    public MessageResponse resendOtp(ResendOtpRequest request) {
        String email = request.email().strip().toLowerCase();
        AccountEntity account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found with email: " + email));

        otpService.generateAndSendOtp(account, request.purpose());
        return new MessageResponse("A new OTP code has been sent to your email");
    }

    /**
     * FR-AUTH-001 & FR-AUTH-002: Login with username/email and password.
     * Enforces failed attempt lock (15 minutes after 5 failures).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request, String deviceLabel) {
        Instant now = clock.instant();
        String identifier = request.usernameOrEmail().strip();

        AccountEntity account = accountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid username/email or password"));

        // Check active restrictions
        assertNotRestricted(account, now);

        // Check lifecycle status
        if (account.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.ACCOUNT_PENDING_VERIFICATION,
                    "Account is not activated yet. Please verify your email with OTP");
        }
        if (account.getStatus() == AccountStatus.DEACTIVATED) {
            throw new ApiException(ErrorCode.ACCOUNT_DEACTIVATED, "Account has been deactivated");
        }

        // Verify password
        if (!passwordService.matches(request.password(), account.getPasswordHash())) {
            int attempts = account.getFailedLoginCount() + 1;
            account.setFailedLoginCount(attempts);

            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                // Apply 15-minute auto lock
                AccountRestrictionEntity autoLock = AccountRestrictionEntity.builder()
                        .id(UUID.randomUUID())
                        .account(account)
                        .kind(RestrictionKind.AUTO_LOCK)
                        .reason("5 consecutive failed login attempts")
                        .startsAt(now)
                        .endsAt(now.plus(AUTO_LOCK_DURATION))
                        .build();
                restrictionRepository.save(autoLock);
                account.setFailedLoginCount(0); // reset counter after locking
                accountRepository.save(account);

                auditPort.record(null, account.getId(), AuditActorType.SYSTEM, "AUTO_LOCK", AuditOutcome.SUCCESS, now);

                throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                        "Too many consecutive failed login attempts. Account locked for 15 minutes");
            }

            accountRepository.save(account);
            auditPort.record(account.getId(), account.getId(), AuditActorType.USER, "LOGIN_FAILED", AuditOutcome.FAILED, now);

            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    "Invalid username/email or password. Attempts remaining: " + (MAX_LOGIN_ATTEMPTS - attempts));
        }

        // Successful login: reset failed login count
        if (account.getFailedLoginCount() > 0) {
            account.setFailedLoginCount(0);
            accountRepository.save(account);
        }

        auditPort.record(account.getId(), account.getId(), AuditActorType.USER, "LOGIN", AuditOutcome.SUCCESS, now);

        boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
        return createSessionAndTokenResponse(account, rememberMe, deviceLabel != null ? deviceLabel : "Web Browser", now);
    }

    /**
     * FR-AUTH-003: Request password reset OTP.
     */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().strip().toLowerCase();
        Optional<AccountEntity> accountOpt = accountRepository.findByEmailIgnoreCase(email);

        if (accountOpt.isPresent()) {
            AccountEntity account = accountOpt.get();
            if (account.getStatus() != AccountStatus.DEACTIVATED) {
                otpService.generateAndSendOtp(account, OtpPurpose.RESET_PASSWORD);
            }
        }
        return new MessageResponse("If an active account exists with this email, a reset OTP has been sent");
    }

    /**
     * FR-AUTH-003: Complete password reset.
     * Revokes ALL existing sessions of the account.
     */
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        Instant now = clock.instant();
        String email = request.email().strip().toLowerCase();

        AccountEntity account = accountRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        UUID tokenAccountId = tokenService.parseAndValidateResetToken(request.resetToken(), now);
        String suppliedHash = tokenService.hashResetToken(request.resetToken());
        if (tokenAccountId == null || !tokenAccountId.equals(account.getId())
                || account.getResetTokenHash() == null
                || account.getResetTokenExpiresAt() == null
                || !account.getResetTokenExpiresAt().isAfter(now)
                || !java.security.MessageDigest.isEqual(
                    suppliedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    account.getResetTokenHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new ApiException(ErrorCode.INVALID_RESET_TOKEN, "Password reset token is invalid or expired");
        }

        passwordService.validatePassword(request.newPassword());
        account.setPasswordHash(passwordService.hash(request.newPassword()));
        account.setResetTokenHash(null);
        account.setResetTokenExpiresAt(null);
        accountRepository.save(account);

        // AC-03: Revoke all existing sessions
        sessionRepository.revokeAllByAccountId(account.getId(), now);

        auditPort.record(account.getId(), account.getId(), AuditActorType.USER, "PASSWORD_RESET", AuditOutcome.SUCCESS, now);

        return new MessageResponse("Password has been reset successfully. Please log in with your new password");
    }

    /**
     * FR-AUTH-004: Change password for authenticated user.
     * Keeps current session alive; revokes all other sessions.
     */
    @Transactional
    public MessageResponse changePassword(UUID accountId, ChangePasswordRequest request, String currentRawRefreshToken) {
        Instant now = clock.instant();

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        if (!passwordService.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Current password does not match");
        }

        passwordService.validatePassword(request.newPassword());
        account.setPasswordHash(passwordService.hash(request.newPassword()));
        accountRepository.save(account);

        // Keep current session; revoke all other sessions
        if (currentRawRefreshToken != null && !currentRawRefreshToken.isBlank()) {
            String currentHash = tokenService.hashRefreshToken(currentRawRefreshToken);
            sessionRepository.findByRefreshTokenHash(currentHash).ifPresent(currentSession ->
                    sessionRepository.revokeOtherSessions(accountId, currentSession.getId(), now));
        } else {
            sessionRepository.revokeAllByAccountId(accountId, now);
        }

        auditPort.record(accountId, accountId, AuditActorType.USER, "PASSWORD_CHANGED", AuditOutcome.SUCCESS, now);

        return new MessageResponse("Password changed successfully. Other sessions have been revoked");
    }

    /**
     * FR-AUTH-005: Refresh access token.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Instant now = clock.instant();
        String tokenHash = tokenService.hashRefreshToken(request.refreshToken());

        AuthSessionEntity session = sessionRepository.findByRefreshTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_REVOKED, "Refresh token not found or revoked"));

        if (session.getRevokedAt() != null) {
            throw new ApiException(ErrorCode.SESSION_REVOKED, "Session has been revoked");
        }
        if (session.getExpiresAt().isBefore(now)) {
            session.setRevokedAt(now);
            sessionRepository.save(session);
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token has expired");
        }

        AccountEntity account = session.getAccount();
        assertNotRestricted(account, now);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Account is not active");
        }

        session.setLastSeenAt(now);
        sessionRepository.save(session);

        String newAccessToken = tokenService.generateAccessToken(account, session.getId());
        return new AuthResponse(
                newAccessToken,
                request.refreshToken(),
                toUserResponse(account),
                TokenService.ACCESS_TOKEN_EXPIRY.toSeconds()
        );
    }

    /**
     * FR-AUTH-006: List sessions for user.
     */
    public List<SessionResponse> listSessions(UUID accountId, String currentRawRefreshToken) {
        Instant now = clock.instant();
        String currentHash = currentRawRefreshToken != null ? tokenService.hashRefreshToken(currentRawRefreshToken) : null;

        return sessionRepository.findByAccountIdAndRevokedAtIsNullAndExpiresAtAfter(accountId, now).stream()
                .map(s -> new SessionResponse(
                        s.getId(),
                        s.getDeviceLabel(),
                        s.getCreatedAt(),
                        s.getLastSeenAt(),
                        s.getExpiresAt(),
                        currentHash != null && currentHash.equals(s.getRefreshTokenHash())
                ))
                .toList();
    }

    /**
     * FR-AUTH-006: Logout current session.
     */
    @Transactional
    public MessageResponse logout(UUID accountId, String rawRefreshToken) {
        Instant now = clock.instant();
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
            sessionRepository.findByRefreshTokenHash(tokenHash).ifPresent(s -> {
                if (s.getAccount().getId().equals(accountId)) {
                    s.setRevokedAt(now);
                    sessionRepository.save(s);
                }
            });
        }
        auditPort.record(accountId, accountId, AuditActorType.USER, "LOGOUT", AuditOutcome.SUCCESS, now);
        return new MessageResponse("Logged out successfully");
    }

    /**
     * FR-AUTH-006: Logout all sessions.
     */
    @Transactional
    public MessageResponse logoutAll(UUID accountId) {
        Instant now = clock.instant();
        sessionRepository.revokeAllByAccountId(accountId, now);
        auditPort.record(accountId, accountId, AuditActorType.USER, "LOGOUT_ALL", AuditOutcome.SUCCESS, now);
        return new MessageResponse("Logged out from all devices successfully");
    }

    /**
     * FR-AUTH-006: Revoke a specific session.
     */
    @Transactional
    public MessageResponse revokeSession(UUID accountId, UUID sessionId) {
        Instant now = clock.instant();
        AuthSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND, "Session not found"));

        if (!session.getAccount().getId().equals(accountId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Cannot revoke a session belonging to another user");
        }

        session.setRevokedAt(now);
        sessionRepository.save(session);

        auditPort.record(accountId, accountId, AuditActorType.USER, "REVOKE_SESSION", AuditOutcome.SUCCESS, now);
        return new MessageResponse("Session revoked successfully");
    }

    /**
     * FR-ACC-006: Self deactivate account.
     */
    @Transactional
    public MessageResponse deactivateAccount(UUID accountId, DeactivateAccountRequest request) {
        Instant now = clock.instant();

        if (!Boolean.TRUE.equals(request.confirm())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Confirmation is required to deactivate account");
        }

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        if (!passwordService.matches(request.password(), account.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Incorrect password");
        }

        account.setStatus(AccountStatus.DEACTIVATED);
        accountRepository.save(account);

        sessionRepository.revokeAllByAccountId(accountId, now);

        auditPort.record(accountId, accountId, AuditActorType.USER, "ACCOUNT_DEACTIVATED", AuditOutcome.SUCCESS, now);
        return new MessageResponse("Account has been deactivated. Contact an administrator if you wish to reactivate");
    }

    /**
     * FR-ACC-006: Admin suspend account.
     */
    @Transactional
    public MessageResponse suspendAccount(UUID adminId, UUID targetAccountId, SuspendAccountRequest request) {
        Instant now = clock.instant();

        AccountEntity target = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        AccountEntity admin = accountRepository.findById(adminId).orElse(null);

        AccountRestrictionEntity suspension = AccountRestrictionEntity.builder()
                .id(UUID.randomUUID())
                .account(target)
                .imposedByAccount(admin)
                .kind(RestrictionKind.SUSPENSION)
                .reason(request.reason().strip())
                .startsAt(now)
                .endsAt(request.endsAt())
                .build();

        restrictionRepository.save(suspension);

        // Revoke all sessions of suspended user
        sessionRepository.revokeAllByAccountId(targetAccountId, now);

        auditPort.record(adminId, targetAccountId, AuditActorType.USER, "ACCOUNT_SUSPENDED", AuditOutcome.SUCCESS, now);
        return new MessageResponse("Account suspended successfully");
    }

    /**
     * FR-ACC-006: Admin unsuspend account.
     */
    @Transactional
    public MessageResponse unsuspendAccount(UUID adminId, UUID targetAccountId) {
        Instant now = clock.instant();

        List<AccountRestrictionEntity> activeSuspensions = restrictionRepository
                .findActiveRestrictionsByKind(targetAccountId, RestrictionKind.SUSPENSION, now);

        for (AccountRestrictionEntity suspension : activeSuspensions) {
            suspension.setRevokedAt(now);
            restrictionRepository.save(suspension);
        }

        auditPort.record(adminId, targetAccountId, AuditActorType.USER, "ACCOUNT_UNSUSPENDED", AuditOutcome.SUCCESS, now);
        return new MessageResponse("Account suspension lifted successfully");
    }

    /**
     * FR-ACC-006: Admin reactivate deactivated account.
     */
    @Transactional
    public MessageResponse reactivateAccount(UUID adminId, UUID targetAccountId) {
        Instant now = clock.instant();

        AccountEntity target = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        if (target.getStatus() != AccountStatus.DEACTIVATED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Account is not in deactivated state");
        }

        target.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(target);

        auditPort.record(adminId, targetAccountId, AuditActorType.USER, "ACCOUNT_REACTIVATED", AuditOutcome.SUCCESS, now);
        return new MessageResponse("Account reactivated successfully");
    }

    public AuthUserResponse getCurrentUser(UUID accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));
        return toUserResponse(account);
    }

    private void assertNotRestricted(AccountEntity account, Instant now) {
        List<AccountRestrictionEntity> activeRestrictions = restrictionRepository.findActiveRestrictions(account.getId(), now);
        for (AccountRestrictionEntity r : activeRestrictions) {
            if (r.getKind() == RestrictionKind.AUTO_LOCK) {
                throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                        "Account is temporarily locked due to multiple failed login attempts. Please try again later");
            }
            if (r.getKind() == RestrictionKind.ADMIN_LOCK) {
                throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Account locked by administrator");
            }
            if (r.getKind() == RestrictionKind.SUSPENSION) {
                String reason = r.getReason() != null ? r.getReason() : "No reason specified";
                throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "Account suspended: " + reason);
            }
        }
    }

    private AuthResponse createSessionAndTokenResponse(AccountEntity account, boolean rememberMe, String deviceLabel, Instant now) {
        String rawRefreshToken = tokenService.generateRawRefreshToken();
        String hashedRefreshToken = tokenService.hashRefreshToken(rawRefreshToken);

        Duration refreshDuration = rememberMe ? TokenService.REFRESH_TOKEN_EXPIRY_REMEMBER : TokenService.REFRESH_TOKEN_EXPIRY_NORMAL;
        Instant refreshExpiresAt = now.plus(refreshDuration);

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .account(account)
                .refreshTokenHash(hashedRefreshToken)
                .rememberMe(rememberMe)
                .deviceLabel(deviceLabel)
                .expiresAt(refreshExpiresAt)
                .lastSeenAt(now)
                .build();

        sessionRepository.save(session);

        String accessToken = tokenService.generateAccessToken(account, session.getId());

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                toUserResponse(account),
                TokenService.ACCESS_TOKEN_EXPIRY.toSeconds()
        );
    }

    private AuthUserResponse toUserResponse(AccountEntity account) {
        return new AuthUserResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getRole(),
                account.getStatus().name(),
                account.getTermsAcceptedAt(),
                account.getPrivacyAcceptedAt()
        );
    }
}

