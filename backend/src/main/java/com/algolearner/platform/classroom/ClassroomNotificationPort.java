package com.algolearner.platform.classroom;

import java.time.Instant;
import java.util.UUID;

public interface ClassroomNotificationPort {
    void invitationCreated(UUID invitationId, UUID classroomId, UUID studentAccountId, Instant occurredAt);
}
