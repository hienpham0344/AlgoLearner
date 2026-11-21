package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.common.exception.ErrorCode;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!mock")
@Transactional(readOnly = true)
public class AccountIdentityResolver {
    private final AccountRepository accounts;

    public AccountIdentityResolver(AccountRepository accounts) { this.accounts = accounts; }

    @Transactional // Pessimistic identity lookup requires a writable transaction.
    public Optional<AccountEntity> resolve(String identity, IdentityType type) {
        if (identity == null || identity.isBlank()) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        String value = identity.trim();
        if (type == IdentityType.EMAIL) return accounts.findForLoginByEmail(value);
        if (type == IdentityType.USERNAME) return accounts.findForLoginByUsername(value);
        // Lock candidates in one stable order, including both namespaces before selecting an account.
        var candidates = accounts.findForIdentityResolution(value);
        if (candidates.stream().map(AccountEntity::getId).distinct().count() > 1)
            throw new AmbiguousIdentityException();
        return candidates.stream().findFirst();
    }

    public static final class AmbiguousIdentityException extends AppException {
        public AmbiguousIdentityException() {
            super("Choose email or username to identify the account.", HttpStatus.CONFLICT, "AMBIGUOUS_IDENTITY");
        }
    }
}
