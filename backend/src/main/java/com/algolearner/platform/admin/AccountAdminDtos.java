package com.algolearner.platform.admin;

import com.algolearner.platform.auth.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AccountAdminDtos {
    private AccountAdminDtos() { }

    public record SuspendRequest(@NotBlank @Size(max = 500) String reason, Instant endsAt) { }
    public record AccountView(UUID id, String username, String email, AccountRole role,
            String status, String suspensionReason, Instant suspensionEndsAt,
            Instant loginLockedUntil, Instant createdAt) { }
}
