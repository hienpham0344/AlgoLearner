package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import java.util.Locale;
import java.util.stream.Stream;

/** All identity writers acquire the same namespace-independent transaction locks. */
public final class AccountIdentityRegistration {
    private AccountIdentityRegistration() { }

    public static void validateUsername(String username) {
        if (username.indexOf('@') >= 0 || username.codePoints().anyMatch(Character::isISOControl))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Username cannot contain @ or control characters.");
    }

    public static void lock(AccountRepository accounts, String email, String username) {
        Stream.of(email, username).map(value -> value.toLowerCase(Locale.ROOT)).distinct().sorted()
                .forEach(accounts::lockIdentity);
    }

    public static boolean conflicts(AccountRepository accounts, String email, String username) {
        return accounts.existsByEmailIgnoreCase(email) || accounts.existsByUsernameIgnoreCase(username)
                || accounts.existsByUsernameIgnoreCase(email) || accounts.existsByEmailIgnoreCase(username);
    }
}
