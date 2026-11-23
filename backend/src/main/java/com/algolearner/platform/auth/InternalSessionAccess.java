package com.algolearner.platform.auth;

import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reject access JWTs whose underlying session was revoked or expired. */
@Service
@Profile("internal-auth")
@Transactional(readOnly = true)
public class InternalSessionAccess {
    private final AuthSessionRepository sessions;
    private final Clock clock;

    public InternalSessionAccess(AuthSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    public boolean active(UUID accountId, UUID sessionId) {
        return sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
                sessionId, accountId, clock.instant());
    }
}
