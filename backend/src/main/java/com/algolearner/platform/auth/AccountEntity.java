package com.algolearner.platform.auth;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "tbl_account")
@Getter @Setter @NoArgsConstructor
public class AccountEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(nullable = false, unique = true, length = 50) private String username;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountStatus status;
}
