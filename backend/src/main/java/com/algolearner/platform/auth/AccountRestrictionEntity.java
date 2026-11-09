package com.algolearner.platform.auth;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tbl_account_restriction", indexes = {
    @Index(name = "idx_restrictions_account_kind_end", columnList = "account_id, kind, ends_at"),
    @Index(name = "idx_restrictions_imposed_by", columnList = "imposed_by_account_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRestrictionEntity extends BaseAuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imposed_by_account_id")
    private AccountEntity imposedByAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RestrictionKind kind;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null
                && !startsAt.isAfter(now)
                && (endsAt == null || endsAt.isAfter(now));
    }
}
