package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tbl_classroom") @Getter @Setter @NoArgsConstructor
public class ClassroomEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_id", nullable = false) private AccountEntity owner;
    @Column(nullable = false, length = 150) private String name;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ClassroomStatus status;
    @Column(name = "class_code", nullable = false, unique = true, length = 20) private String classCode;
    @Column(name = "code_expires_at") private Instant codeExpiresAt;
    @Column(name = "code_locked", nullable = false) private boolean codeLocked;
    @Column(name = "joining_open", nullable = false) private boolean joiningOpen;
    @Enumerated(EnumType.STRING) @Column(name = "join_mode", nullable = false, length = 30) private JoinMode joinMode;
}
