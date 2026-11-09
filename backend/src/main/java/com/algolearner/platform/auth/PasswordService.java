package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PasswordService {

    private static final Pattern HAS_LETTER = Pattern.compile(".*[a-zA-Z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");
    private static final int MIN_LENGTH = 8;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    /**
     * FR-ACC-005: Password must be at least 8 chars, contain at least 1 letter and 1 number.
     * Uppercase or special characters are not mandatory.
     */
    public void validatePassword(String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || !HAS_LETTER.matcher(password).matches()
                || !HAS_DIGIT.matcher(password).matches()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "Password must be at least 8 characters long and contain at least one letter and one number");
        }
    }

    public String hash(String rawPassword) {
        validatePassword(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
