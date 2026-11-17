package com.algolearner.platform.auth;

import com.algolearner.platform.common.security.AccountAccessRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!mock")
public class JpaAccountAccessRepository implements AccountAccessRepository {
    private final AccountRepository accounts;
    private final AccountRestrictionRepository restrictions;

    public JpaAccountAccessRepository(AccountRepository accounts, AccountRestrictionRepository restrictions) {
        this.accounts = accounts;
        this.restrictions = restrictions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> activeRole(UUID accountId, Instant now) {
        return accounts.findById(accountId)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .filter(account -> restrictions.findActiveRestrictions(accountId, now).isEmpty())
                .map(account -> account.getRole().name());
    }
}
