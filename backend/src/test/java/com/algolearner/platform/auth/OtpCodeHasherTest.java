package com.algolearner.platform.auth;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class OtpCodeHasherTest {
    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final OtpCodeHasher hasher = new OtpCodeHasher(TEST_KEY);
    private final BCryptPasswordEncoder legacy = new BCryptPasswordEncoder(4);

    @Test
    void hashBindsCodeToAccountAndPurposeAndRejectsWrongCode() {
        UUID account = UUID.randomUUID();
        String hash = hasher.hash(account, "REGISTER", "123456");
        assertThat(hash).startsWith("hmac-sha256:v1:").hasSize(79);
        assertThat(hasher.matches(account, "REGISTER", "123456", hash, legacy)).isTrue();
        assertThat(hasher.matches(account, "REGISTER", "123457", hash, legacy)).isFalse();
        assertThat(hasher.matches(UUID.randomUUID(), "REGISTER", "123456", hash, legacy)).isFalse();
        assertThat(hasher.matches(account, "RESET_PASSWORD", "123456", hash, legacy)).isFalse();
    }

    @Test
    void acceptsExistingBcryptCodeButNotUnknownHashFormats() {
        UUID account = UUID.randomUUID();
        String oldHash = legacy.encode("123456");
        assertThat(hasher.matches(account, "REGISTER", "123456", oldHash, legacy)).isTrue();
        assertThat(hasher.matches(account, "REGISTER", "000000", oldHash, legacy)).isFalse();
        assertThat(hasher.matches(account, "REGISTER", "123456", "invalid", legacy)).isFalse();
    }

    @Test
    void requiresStrongBase64Key() {
        assertThatThrownBy(() -> new OtpCodeHasher(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OtpCodeHasher("YWJj"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OtpCodeHasher("not-base64"))
                .isInstanceOf(IllegalStateException.class);
    }
}
