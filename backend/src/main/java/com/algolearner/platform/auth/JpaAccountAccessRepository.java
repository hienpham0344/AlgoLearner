package com.algolearner.platform.auth;

import com.algolearner.platform.common.security.AccountRepository;
import com.algolearner.platform.admin.AccountAdministrationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("internal-auth")
public class JpaAccountAccessRepository implements AccountRepository {
    private final com.algolearner.platform.auth.AccountRepository accounts;
    private final AccountAdministrationService administration;
    private final Clock clock;

    public JpaAccountAccessRepository(com.algolearner.platform.auth.AccountRepository accounts,
            AccountAdministrationService administration, Clock clock) {
        this.accounts = accounts;
        this.administration = administration;
        this.clock = clock;
    }

    @Override public List<Account> accounts() {
        return accounts.findAll().stream()
                .map(a -> new Account(a.getId(), a.getUsername(), a.getRole().name(),
                        a.getStatus().name(), a.getCreatedAt())).toList();
    }

    @Override public Optional<Account> account(UUID id) {
        var found = accounts.findById(id);
        if (found.isPresent() && found.get().getStatus() == AccountStatus.SUSPENDED
                && found.get().getSuspensionEndsAt() != null
                && !found.get().getSuspensionEndsAt().isAfter(clock.instant())) {
            administration.releaseExpiredSuspension(id);
            found = accounts.findById(id);
        }
        return found.map(a -> new Account(a.getId(), a.getUsername(), a.getRole().name(),
                a.getStatus().name(), a.getCreatedAt()));
    }

    @Override public List<Restriction> restrictions() { return List.of(); }
}
