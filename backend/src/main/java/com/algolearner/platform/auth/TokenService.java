package com.algolearner.platform.auth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TokenService {

    public static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofMinutes(15);
    public static final Duration REFRESH_TOKEN_EXPIRY_NORMAL = Duration.ofDays(7);
    public static final Duration REFRESH_TOKEN_EXPIRY_REMEMBER = Duration.ofDays(30);
    public static final Duration RESET_TOKEN_EXPIRY = Duration.ofMinutes(15);

    private final SecureRandom random = new SecureRandom();

    /**
     * Issue an access token for an account.
     * In mock/dev environments, mock-<username> is accepted by MockJwtDecoder.
     */
    public String generateAccessToken(AccountEntity account) {
        // Form a token that carries account ID and username
        return "mock-" + account.getUsername();
    }

    /**
     * Generate raw opaque refresh token to be returned to the client.
     */
    public String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash refresh token using SHA-256 before database storage.
     */
    public String hashRefreshToken(String rawRefreshToken) {
        return sha256("refresh:" + rawRefreshToken.trim());
    }

    /**
     * Generate password reset grant token.
     */
    public String generateResetToken(UUID accountId, Instant expiresAt) {
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        long epochSec = expiresAt.getEpochSecond();
        String payload = accountId.toString() + ":" + epochSec + ":" + randomHex;
        String signature = sha256("reset-secret:" + payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validate reset grant token and extract account ID.
     */
    public UUID parseAndValidateResetToken(String token, Instant now) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String content = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = content.split(":");
            if (parts.length != 4) {
                return null;
            }
            UUID accountId = UUID.fromString(parts[0]);
            long epochSec = Long.parseLong(parts[1]);
            String randomHex = parts[2];
            String expectedSig = parts[3];

            Instant expiresAt = Instant.ofEpochSecond(epochSec);
            if (expiresAt.isBefore(now)) {
                return null;
            }

            String payload = accountId + ":" + epochSec + ":" + randomHex;
            String calculatedSig = sha256("reset-secret:" + payload);
            if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), calculatedSig.getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            return accountId;
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }
}

