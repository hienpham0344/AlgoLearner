package com.algolearner.platform.auth;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_registration_otp")
@Getter @Setter @NoArgsConstructor
public class RegistrationOtpEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id", nullable = false) private AccountEntity account;
    @Column(name = "code_hash", nullable = false, length = 100) private String codeHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "sent_at", nullable = false) private Instant sentAt;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "used_at") private Instant usedAt;
    @Column(nullable = false, length = 24) private String purpose = "REGISTER";
    @Column(name = "reset_token_hash", length = 64, unique = true) private String resetTokenHash;
    @Column(name = "reset_expires_at") private Instant resetExpiresAt;
    @Column(name = "reset_used_at") private Instant resetUsedAt;
}
