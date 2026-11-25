package com.algolearner.platform.audit;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_account_audit_log")
@Getter @Setter @NoArgsConstructor
public class AccountAuditEntity extends BaseAuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private AccountEntity actor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "target_id", nullable = false)
    private AccountEntity target;
    @Column(nullable = false, length = 32) private String action;
    @Column(length = 500) private String reason;
}
