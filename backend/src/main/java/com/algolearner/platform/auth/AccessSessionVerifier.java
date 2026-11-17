package com.algolearner.platform.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Profile("!mock")
public class AccessSessionVerifier {
    private final AuthSessionRepository sessions;
    private final Clock clock;

    public AccessSessionVerifier(AuthSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean active(UUID accountId, String sessionClaim) {
        if (sessionClaim == null) return false;
        try {
            return sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
                    UUID.fromString(sessionClaim), accountId, clock.instant());
        } catch (IllegalArgumentException invalidId) {
            return false;
        }
    }
}
