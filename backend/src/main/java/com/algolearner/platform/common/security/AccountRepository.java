package com.algolearner.platform.common.security;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface AccountRepository extends AccountAccessRepository {
    record Account(UUID id, String username, String role, String status, Instant createdAt) { }
    record Restriction(UUID accountId, String kind, Instant startsAt, Instant endsAt, Instant revokedAt) {
        public boolean active(Instant now) {
            return revokedAt == null && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
        }
    }
    List<Account> accounts();
    List<Restriction> restrictions();

    @Override
    default java.util.Optional<String> activeRole(UUID id, Instant now) {
        return accounts().stream().filter(a -> a.id().equals(id) && a.status().equals("ACTIVE"))
            .filter(a -> restrictions().stream().noneMatch(r -> r.accountId().equals(id) && r.active(now)))
            .map(Account::role).findFirst();
    }
}
