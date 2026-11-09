package com.algolearner.platform.auth;

import java.time.Instant;
import java.util.UUID;

public interface AuthAuditPort {
    void record(UUID actorId, UUID targetId, AuditActorType actorType, String action, AuditOutcome outcome, Instant occurredAt);
}

