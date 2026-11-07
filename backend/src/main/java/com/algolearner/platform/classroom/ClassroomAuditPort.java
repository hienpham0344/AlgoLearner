package com.algolearner.platform.classroom;

import java.time.Instant;
import java.util.UUID;

public interface ClassroomAuditPort {
    void record(UUID actorAccountId, String action, UUID targetId, String outcome, Instant occurredAt);
}
