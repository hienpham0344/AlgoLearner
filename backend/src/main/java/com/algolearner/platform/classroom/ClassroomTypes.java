package com.algolearner.platform.classroom;

import java.time.Instant;
import java.util.UUID;

public final class ClassroomTypes {
    private ClassroomTypes() { }

    public enum ClassroomStatus { ACTIVE, ARCHIVED }
    public enum JoinMode { AUTO_JOIN, REQUIRE_APPROVAL }
    public enum MembershipStatus { ACTIVE, LEFT, REMOVED }
    public enum JoinRequestStatus { PENDING, APPROVED, REJECTED, CANCELLED }
    public enum InvitationStatus { PENDING, ACCEPTED, DECLINED, REVOKED, EXPIRED }

    public record Classroom(UUID id, String name, String description, UUID ownerAccountId,
            ClassroomStatus status, JoinMode joinMode, boolean joiningOpen, String classCode,
            boolean classCodeLocked, Instant classCodeExpiresAt, Instant createdAt, Instant updatedAt) { }

    public record Membership(UUID id, UUID classroomId, UUID studentAccountId, MembershipStatus status,
            Instant joinedAt, Instant endedAt, Instant createdAt, Instant updatedAt) { }

    public record JoinRequest(UUID id, UUID classroomId, UUID studentAccountId, JoinRequestStatus status,
            UUID reviewedByAccountId, Instant reviewedAt, Instant createdAt, Instant updatedAt) { }

    public record Invitation(UUID id, UUID classroomId, UUID inviteeAccountId, UUID invitedByAccountId,
            InvitationStatus status, Instant expiresAt, Instant respondedAt, Instant revokedAt,
            Instant createdAt, Instant updatedAt) { }
}
