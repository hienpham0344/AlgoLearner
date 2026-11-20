package com.algolearner.platform.auth;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "tbl_account")
@Getter @Setter @NoArgsConstructor
public class AccountEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(nullable = false, unique = true, length = 50) private String username;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", length = 100) private String passwordHash;
    @Column(name = "failed_login_attempts", nullable = false) private int failedLoginAttempts;
    @Column(name = "login_locked_until") private Instant loginLockedUntil;
    @Column(name = "suspension_reason", length = 500) private String suspensionReason;
    @Column(name = "suspension_ends_at") private Instant suspensionEndsAt;
    @Column(name = "full_name", length = 150) private String fullName;
    @Column(name = "short_bio", length = 200) private String shortBio;
    @Column(name = "avatar_key", length = 80) private String avatarKey;
    @Column(name = "profile_public", nullable = false) private boolean profilePublic;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountStatus status;
}
