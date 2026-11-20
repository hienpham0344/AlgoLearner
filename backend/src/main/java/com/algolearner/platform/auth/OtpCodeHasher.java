package com.algolearner.platform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Keyed hash for low-entropy six-digit OTPs; password hashes remain BCrypt. */
@Component
@Profile("internal-auth")
public class OtpCodeHasher {
    private static final String PREFIX = "hmac-sha256:v1:";
    private final byte[] key;

    public OtpCodeHasher(@Value("${app.security.otp-hmac-secret:}") String base64Key) {
        try {
            this.key = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Set OTP_HMAC_SECRET to base64 of at least 32 random bytes", ex);
        }
        if (key.length < 32)
            throw new IllegalStateException("Set OTP_HMAC_SECRET to base64 of at least 32 random bytes");
    }

    public String hash(UUID accountId, String purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String input = "algolearner:otp:v1:" + accountId + ":" + purpose + ":" + code;
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }

    public boolean matches(UUID accountId, String purpose, String code, String storedHash,
            PasswordEncoder legacyPasswords) {
        if (storedHash == null) return false;
        if (storedHash.startsWith(PREFIX)) {
            byte[] expected = hash(accountId, purpose, code).getBytes(StandardCharsets.US_ASCII);
            byte[] stored = storedHash.getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, stored);
        }
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"))
            return legacyPasswords.matches(code, storedHash);
        return false;
    }
}
