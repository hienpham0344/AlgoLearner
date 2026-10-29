package com.algolearner.platform.common.security;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface AccountRepository {
    record Account(UUID id, String username, String role, String status, Instant createdAt) { }
    record Restriction(UUID accountId, String kind, Instant startsAt, Instant endsAt, Instant revokedAt) {
        public boolean active(Instant now) {
            return revokedAt == null && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
        }
    }
    List<Account> accounts();
    List<Restriction> restrictions();
}
