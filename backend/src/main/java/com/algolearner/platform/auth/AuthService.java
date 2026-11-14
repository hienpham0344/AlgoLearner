package com.algolearner.platform.auth;

import static com.algolearner.platform.auth.AuthDtos.*;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.admin.AccountAdministrationService;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("internal-auth")
@Transactional(readOnly = true)
public class AuthService {
    static final String RESET_REQUEST_MESSAGE = "If the account is eligible, a reset code has been sent.";
    private static final Duration OTP_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESET_GRANT_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_WAIT = Duration.ofSeconds(60);
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(15);
    private final AccountRepository accounts;
    private final RegistrationOtpRepository otps;
    private final RegistrationMailer mailer;
    private final PasswordEncoder passwords;
    private final OtpCodeHasher otpHasher;
    private final AuthSessionService sessions;
    private final AccountAdministrationService administration;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AccountRepository accounts, RegistrationOtpRepository otps, RegistrationMailer mailer,
            PasswordEncoder passwords, OtpCodeHasher otpHasher, AuthSessionService sessions,
            AccountAdministrationService administration, Clock clock) {
        this.accounts = accounts;
        this.otps = otps;
        this.mailer = mailer;
        this.passwords = passwords;
        this.otpHasher = otpHasher;
        this.sessions = sessions;
        this.administration = administration;
        this.clock = clock;
    }

    @Transactional
    public Message register(RegisterRequest request) {
        long stageStarted = RegistrationTiming.begin();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();
        if (request.role() == AccountRole.ADMIN || !Boolean.TRUE.equals(request.termsAccepted())
                || !Boolean.TRUE.equals(request.privacyAccepted()) || !request.password().equals(request.confirmPassword()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        validatePassword(request.password(), request.confirmPassword());
        RegistrationTiming.record("validation", stageStarted);
        stageStarted = RegistrationTiming.begin();
        if (accounts.existsByEmailIgnoreCase(email) || accounts.existsByUsernameIgnoreCase(username))
            throw new ApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        RegistrationTiming.record("duplicate_lookup", stageStarted);

        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setEmail(email);
        account.setUsername(username);
        account.setRole(request.role() == null ? AccountRole.STUDENT : request.role());
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        stageStarted = RegistrationTiming.begin();
        account.setPasswordHash(passwords.encode(request.password()));
        RegistrationTiming.record("password_hash", stageStarted);
        stageStarted = RegistrationTiming.begin();
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        RegistrationTiming.record("account_flush", stageStarted);
        sendCode(account, "REGISTER");
        return new Message(email, "Verification code sent");
    }

    @Transactional
    public ResendResult resend(EmailRequest request) {
        if ("RESET_PASSWORD".equals(request.purpose())) {
            forgotPassword(new ForgotPasswordRequest(request.email(), request.usernameOrEmail()));
            return new ResendResult(true, RESET_REQUEST_MESSAGE);
        }
        if (!"REGISTER".equals(request.purpose())) throw new ApiException(ErrorCode.INVALID_REQUEST);
        if (request.email() == null || request.email().isBlank()) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        var account = accounts.findForLoginByEmail(request.email().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        if (account.getStatus() != AccountStatus.PENDING_VERIFICATION) throw new ApiException(ErrorCode.INVALID_OTP);
        sendCode(account, "REGISTER");
        return new ResendResult(true, "Verification code sent");
    }

    @Transactional
    public Message forgotPassword(ForgotPasswordRequest request) {
        String lookup = identity(request.identity());
        findByIdentity(lookup).filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(account -> sendCode(account, "RESET_PASSWORD"));
        return resetRequestMessage(request);
    }

    static Message resetRequestMessage(ForgotPasswordRequest request) {
        return new Message(request.email() == null ? null : request.email().trim().toLowerCase(Locale.ROOT),
                RESET_REQUEST_MESSAGE);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult verify(VerifyOtpRequest request) {
        return verify(request, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult verify(VerifyOtpRequest request, String userAgent) {
        var otp = verifyCode(request, "REGISTER", AccountStatus.PENDING_VERIFICATION);
        var account = otp.getAccount();
        account.setStatus(AccountStatus.ACTIVE);
        return sessions.issue(account, false, userAgent);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ResetVerification verifyReset(VerifyOtpRequest request) {
        var otp = verifyCode(request, "RESET_PASSWORD", AccountStatus.ACTIVE);
        String grant = SecretTokens.create();
        otp.setResetTokenHash(SecretTokens.hash(grant));
        otp.setResetExpiresAt(clock.instant().plus(RESET_GRANT_LIFETIME));
        return new ResetVerification(grant);
    }

    @Transactional
    public ResendResult resetPassword(ResetPasswordRequest request) {
        validatePassword(request.newPassword(), request.confirmPassword());
        var account = findByIdentity(identity(request.identity()))
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        var grant = otps.findByAccountIdAndResetTokenHash(account.getId(), SecretTokens.hash(request.resetToken()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        if (!"RESET_PASSWORD".equals(grant.getPurpose()) || grant.getUsedAt() == null
                || grant.getResetUsedAt() != null || grant.getResetExpiresAt() == null
                || !grant.getResetExpiresAt().isAfter(clock.instant())) throw new ApiException(ErrorCode.INVALID_OTP);
        account.setPasswordHash(passwords.encode(request.newPassword()));
        account.setFailedLoginAttempts(0);
        account.setLoginLockedUntil(null);
        invalidatePasswordCodes(account.getId());
        sessions.revokeOtherSessions(account.getId(), null);
        return new ResendResult(true, "Password updated. Sign in again on all devices.");
    }

    @Transactional
    public ResendResult changePassword(UUID accountId, UUID sessionId, ChangePasswordRequest request) {
        validatePassword(request.newPassword(), request.confirmPassword());
        var account = accounts.findForUpdateById(accountId)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS));
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Current password is incorrect.");
        account.setPasswordHash(passwords.encode(request.newPassword()));
        invalidatePasswordCodes(accountId);
        sessions.revokeOtherSessions(accountId, sessionId);
        return new ResendResult(true, "Password updated. Other sessions have been signed out.");
    }

    private RegistrationOtpEntity verifyCode(VerifyOtpRequest request, String purpose, AccountStatus status) {
        if (!purpose.equals(request.purpose()) || !request.code().matches("[0-9]{6}"))
            throw new ApiException(ErrorCode.INVALID_OTP);
        var account = findByIdentity(identity(request.identity()))
                .filter(a -> a.getStatus() == status).orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        var otp = otps.findFirstByAccountIdAndPurposeOrderBySentAtDesc(account.getId(), purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        if (otp.getUsedAt() != null || !otp.getExpiresAt().isAfter(clock.instant()) || otp.getFailedAttempts() >= 5)
            throw new ApiException(ErrorCode.INVALID_OTP);
        if (!otpHasher.matches(account.getId(), purpose, request.code(), otp.getCodeHash(), passwords)) {
            otp.setFailedAttempts(otp.getFailedAttempts() + 1);
            throw new ApiException(ErrorCode.INVALID_OTP);
        }
        otp.setUsedAt(clock.instant());
        return otp;
    }

    private void invalidatePasswordCodes(UUID accountId) {
        otps.findByAccountId(accountId).stream().filter(o -> "RESET_PASSWORD".equals(o.getPurpose()))
                .forEach(o -> { o.setUsedAt(clock.instant()); o.setResetUsedAt(clock.instant()); });
    }

    private String identity(String value) {
        if (value == null || value.isBlank()) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        return value.trim();
    }

    private java.util.Optional<AccountEntity> findByIdentity(String value) {
        return accounts.findForLoginByEmail(value).or(() -> accounts.findForLoginByUsername(value));
    }

    private void validatePassword(String password, String confirm) {
        if (!password.equals(confirm)) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Passwords do not match.");
        if (password.length() < 8 || !password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).*"))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Use at least 8 characters, including a letter and a number.");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password is too long. Use at most 72 UTF-8 bytes.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(LoginRequest request, String userAgent) {
        String identity = request.usernameOrEmail().trim();
        AccountEntity account = accounts.findForLoginByEmail(identity)
                .or(() -> accounts.findForLoginByUsername(identity))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        Instant now = clock.instant();
        if (account.getLoginLockedUntil() != null && account.getLoginLockedUntil().isAfter(now))
            throw new ApiException(ErrorCode.ACCOUNT_TEMP_LOCKED);
        if (account.getLoginLockedUntil() != null) {
            account.setLoginLockedUntil(null);
            account.setFailedLoginAttempts(0);
        }
        if (account.getStatus() == AccountStatus.SUSPENDED && account.getSuspensionEndsAt() != null
                && !account.getSuspensionEndsAt().isAfter(now)) {
            administration.releaseExpiredSuspension(account.getId());
        }
        if (account.getStatus() == AccountStatus.SUSPENDED && account.getPasswordHash() != null
                && passwords.matches(request.password(), account.getPasswordHash()))
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, account.getSuspensionReason() == null
                    ? ErrorCode.ACCOUNT_SUSPENDED.getMessage() : account.getSuspensionReason());
        if (account.getStatus() != AccountStatus.ACTIVE || account.getPasswordHash() == null
                || !passwords.matches(request.password(), account.getPasswordHash())) {
            int failures = account.getFailedLoginAttempts() + 1;
            account.setFailedLoginAttempts(failures);
            if (failures >= 5) {
                account.setLoginLockedUntil(now.plus(LOGIN_LOCK));
                throw new ApiException(ErrorCode.ACCOUNT_TEMP_LOCKED);
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        account.setFailedLoginAttempts(0);
        return sessions.issue(account, request.rememberMe(), userAgent);
    }

    public AuthUser me(UUID id) {
        AccountEntity account = accounts.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS));
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        return new AuthUser(account.getId(), account.getUsername(), account.getEmail(),
                account.getRole(), account.getStatus().name());
    }

    private void sendCode(AccountEntity account, String purpose) {
        long stageStarted = RegistrationTiming.begin();
        var previous = otps.findFirstByAccountIdAndPurposeOrderBySentAtDesc(account.getId(), purpose);
        if (previous.isPresent() && previous.get().getSentAt().plus(RESEND_WAIT).isAfter(clock.instant()))
            throw new ApiException(ErrorCode.OTP_RATE_LIMITED);
        previous.ifPresent(code -> { code.setUsedAt(clock.instant()); code.setResetUsedAt(clock.instant()); });
        RegistrationTiming.record("otp_lookup", stageStarted);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        Instant now = clock.instant();
        var otp = new RegistrationOtpEntity();
        otp.setId(UUID.randomUUID());
        otp.setAccount(account);
        otp.setPurpose(purpose);
        stageStarted = RegistrationTiming.begin();
        otp.setCodeHash(otpHasher.hash(account.getId(), purpose, code));
        RegistrationTiming.record("otp_hash", stageStarted);
        otp.setSentAt(now);
        otp.setExpiresAt(now.plus(OTP_LIFETIME));
        stageStarted = RegistrationTiming.begin();
        otps.save(otp);
        RegistrationTiming.record("otp_save", stageStarted);
        stageStarted = RegistrationTiming.begin();
        mailer.send(account.getEmail(), code, purpose);
        RegistrationTiming.record("smtp_send", stageStarted);
    }

}
