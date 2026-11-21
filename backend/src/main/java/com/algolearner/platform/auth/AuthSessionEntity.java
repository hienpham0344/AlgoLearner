package com.algolearner.platform.auth;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_auth_session")
@Getter @Setter @NoArgsConstructor
public class AuthSessionEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id", nullable = false) private AccountEntity account;
    @Column(name = "refresh_hash", nullable = false, unique = true, length = 64) private String refreshHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "remember_me", nullable = false) private boolean rememberMe;
    @Column(name = "user_agent", length = 255) private String userAgent;
    @Column(name = "revoked_at") private Instant revokedAt;
}
