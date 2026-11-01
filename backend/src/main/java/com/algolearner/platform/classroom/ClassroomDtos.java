package com.algolearner.platform.classroom;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ClassroomDtos {
    private ClassroomDtos() { }
    public record Create(@NotBlank @Size(max=150) String name, @Size(max=2000) String description,
                         @NotNull JoinMode joinMode, Instant codeExpiresAt, UUID ownerId) { }
    public record Update(@NotBlank @Size(max=150) String name, @Size(max=2000) String description) { }
    public record JoinByCode(@NotBlank @Size(max=20) String classCode) { }
    public record CodeConfig(Instant codeExpiresAt, @NotNull Boolean codeLocked,
                             @NotNull Boolean joiningOpen, @NotNull JoinMode joinMode) { }
    public record Invite(@NotBlank @Email String email, @NotNull @Future Instant expiresAt) { }
    public record DirectAdd(@NotBlank String usernameOrEmail) { }
    public record TransferOwner(@NotNull UUID targetTeacherId) { }
    public record View(UUID id, String name, String description, String status, String classCode,
                       Instant codeExpiresAt, boolean codeLocked, boolean joiningOpen, JoinMode joinMode,
                       UUID ownerId, Instant createdAt, Instant updatedAt) { }
    public record JoinResult(String outcome, UUID classroomId, UUID requestId) { }
    public record Member(UUID studentId, String username, String email, MembershipStatus status, Instant joinedAt) { }
    public record JoinRequest(UUID id, UUID studentId, String username, Instant createdAt) { }
}
