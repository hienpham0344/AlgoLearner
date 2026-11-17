package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class OtpService {

    public static final Duration OTP_EXPIRY = Duration.ofMinutes(5);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private final OtpChallengeRepository otpRepository;
    private final AuthMailPort mailPort;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository otpRepository, AuthMailPort mailPort, Clock clock) {
        this.otpRepository = otpRepository;
        this.mailPort = mailPort;
        this.clock = clock;
    }

    /**
     * FR-ACC-004: Generate and send a 6-digit OTP code.
     * Enforces 60-second cooldown from previous request for the same purpose.
     */
    @Transactional
    public void generateAndSendOtp(AccountEntity account, OtpPurpose purpose) {
        Instant now = clock.instant();

        // Check 60s cooldown from last sent OTP of this purpose
        Optional<OtpChallengeEntity> latest = otpRepository.findLatestByAccountAndPurpose(account.getId(), purpose);
        if (latest.isPresent()) {
            Instant cooldownEnd = latest.get().getSentAt().plus(RESEND_COOLDOWN);
            if (cooldownEnd.isAfter(now)) {
                throw new ApiException(ErrorCode.OTP_COOLDOWN_ACTIVE,
                        "Please wait 60 seconds before requesting a new OTP code");
            }
        }

        // A resend invalidates every earlier unconsumed code for the same purpose.
        otpRepository.consumeOutstanding(account.getId(), purpose, now);

        // Generate 6-digit code
        int codeInt = 100_000 + random.nextInt(900_000);
        String rawCode = String.valueOf(codeInt);
        String codeHash = hashCode(account.getId(), rawCode);

        Instant expiresAt = now.plus(OTP_EXPIRY);

        OtpChallengeEntity challenge = OtpChallengeEntity.builder()
                .id(UUID.randomUUID())
                .account(account)
                .purpose(purpose)
                .codeHash(codeHash)
                .failedAttempts(0)
                .sentAt(now)
                .expiresAt(expiresAt)
                .consumedAt(null)
                .build();

        otpRepository.save(challenge);
        mailPort.sendOtp(account.getEmail(), account.getUsername(), rawCode, purpose, expiresAt);
    }

    /**
     * FR-ACC-004: Validate OTP code.
     * Validates expiration (5 min), max attempts (5), and purpose match.
     * Marks challenge as consumed on success.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyOtp(AccountEntity account, String rawCode, OtpPurpose purpose) {
        Instant now = clock.instant();

        OtpChallengeEntity challenge = otpRepository.findActiveByAccountAndPurpose(account.getId(), purpose, now)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_EXPIRED, "No active OTP found or OTP has expired"));

        if (challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new ApiException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                    "Maximum OTP attempts exceeded (5/5). Please request a new OTP code");
        }

        String expectedHash = challenge.getCodeHash();
        String actualHash = hashCode(account.getId(), rawCode);

        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8), actualHash.getBytes(StandardCharsets.UTF_8))) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            otpRepository.save(challenge);

            if (challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                throw new ApiException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                        "Maximum OTP attempts exceeded (5/5). Please request a new OTP code");
            }
            throw new ApiException(ErrorCode.OTP_INVALID,
                    "Invalid OTP code. Remaining attempts: " + (MAX_FAILED_ATTEMPTS - challenge.getFailedAttempts()));
        }

        // Mark as consumed
        challenge.setConsumedAt(now);
        otpRepository.save(challenge);
    }

    private String hashCode(UUID accountId, String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = accountId.toString() + ":" + rawCode.trim();
            byte[] hash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}
