package com.algolearner.platform.classroom;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import static com.algolearner.platform.classroom.ClassroomTypes.*;

public final class ClassroomDtos {
    private ClassroomDtos() { }

    public record CreateClassroomRequest(@NotBlank @Size(max=150) String name,
            @Size(max=2000) String description, JoinMode joinMode, Boolean joiningOpen,
            Instant classCodeExpiresAt) { }
    public record AdminCreateClassroomRequest(@NotNull UUID ownerAccountId,
            @NotBlank @Size(max=150) String name, @Size(max=2000) String description,
            JoinMode joinMode, Boolean joiningOpen, Instant classCodeExpiresAt) { }
    public record UpdateClassroomRequest(@Size(max=150) String name, @Size(max=2000) String description) { }
    public record JoinSettingsRequest(Boolean joiningOpen, JoinMode joinMode) { }
    public record JoinCodeSettingsRequest(Boolean locked, Boolean neverExpires, Instant expiresAt) { }
    public record JoinClassRequest(@NotBlank String classCode) { }
    public record AddMemberRequest(@NotNull UUID studentAccountId) { }
    public record CreateInvitationRequest(@NotBlank @Email String email, Instant expiresAt) { }
    public record TransferOwnershipRequest(@NotNull UUID newOwnerAccountId) { }

    public record ClassroomResponse(UUID id, String name, String description, UUID ownerAccountId,
            ClassroomStatus status, JoinMode joinMode, boolean joiningOpen, boolean readOnly,
            Instant createdAt, Instant updatedAt) { }
    public record JoinCodeResponse(String classCode, boolean locked, Instant expiresAt) { }
    public record MembershipResponse(UUID id, UUID classroomId, UUID studentAccountId,
            MembershipStatus status, Instant joinedAt, Instant endedAt) { }
    public record JoinRequestResponse(UUID id, UUID classroomId, UUID studentAccountId,
            JoinRequestStatus status, UUID reviewedByAccountId, Instant reviewedAt, Instant createdAt) { }
    public record InvitationResponse(UUID id, UUID classroomId, UUID inviteeAccountId,
            InvitationStatus status, Instant expiresAt, Instant respondedAt, Instant revokedAt) { }
    public record JoinResult(UUID classroomId, String result, MembershipResponse membership,
            UUID joinRequestId) { }
}
