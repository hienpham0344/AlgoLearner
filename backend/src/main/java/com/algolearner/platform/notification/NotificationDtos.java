package com.algolearner.platform.notification;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() { }

    public record InvitationNotificationView(UUID id, UUID invitationId, UUID classroomId,
            String classroomName, String status, Instant expiresAt, Instant createdAt, Instant readAt) { }
}
