package com.algolearner.platform.auth;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!mock")
public class TokenService {

    public static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofMinutes(15);
    public static final Duration REFRESH_TOKEN_EXPIRY_NORMAL = Duration.ofDays(7);
    public static final Duration REFRESH_TOKEN_EXPIRY_REMEMBER = Duration.ofDays(30);
    public static final Duration RESET_TOKEN_EXPIRY = Duration.ofMinutes(15);

    private final SecureRandom random = new SecureRandom();
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;

    public TokenService(JwtEncoder encoder, Clock clock,
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.audience}") String audience) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
    }

    /** Issue a signed, session-bound access token. */
    public String generateAccessToken(AccountEntity account, UUID sessionId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).subject(account.getId().toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_TOKEN_EXPIRY))
                .id(UUID.randomUUID().toString()).claim("sid", sessionId.toString()).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
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

    public String hashResetToken(String rawResetToken) {
        return sha256("reset:" + rawResetToken.trim());
    }

    /**
     * Generate password reset grant token.
     */
    public String generateResetToken(UUID accountId, Instant expiresAt) {
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        long epochSec = expiresAt.getEpochSecond();
        String payload = accountId.toString() + ":" + epochSec + ":" + randomHex;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /** Parse the opaque grant. AuthService also checks its stored hash and expiry. */
    public UUID parseAndValidateResetToken(String token, Instant now) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String content = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = content.split(":");
            if (parts.length != 3) {
                return null;
            }
            UUID accountId = UUID.fromString(parts[0]);
            long epochSec = Long.parseLong(parts[1]);
            String randomHex = parts[2];
            if (!randomHex.matches("[0-9a-f]{64}")) {
                return null;
            }

            Instant expiresAt = Instant.ofEpochSecond(epochSec);
            if (expiresAt.isBefore(now)) {
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

