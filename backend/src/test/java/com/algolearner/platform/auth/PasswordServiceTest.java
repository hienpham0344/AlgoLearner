package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password123",
            "1234567a",
            "A1b2c3d4",
            "testpass1",
            "my_secure_pass_99!"
    })
    @DisplayName("Valid passwords meeting FR-ACC-005 criteria should pass validation")
    void validPasswordsPass(String password) {
        passwordService.validatePassword(password);
        String hashed = passwordService.hash(password);

        assertThat(hashed).isNotBlank();
        assertThat(passwordService.matches(password, hashed)).isTrue();
        assertThat(passwordService.matches("wrongPassword1", hashed)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1",        // Less than 8 chars
            "pass12",        // Less than 8 chars
            "onlyletters",   // No digits
            "12345678",      // No letters
            "",              // Empty
    })
    @DisplayName("Invalid passwords violating FR-ACC-005 criteria should be rejected")
    void invalidPasswordsRejected(String password) {
        assertThatThrownBy(() -> passwordService.validatePassword(password))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION.getCode()));
    }
}
