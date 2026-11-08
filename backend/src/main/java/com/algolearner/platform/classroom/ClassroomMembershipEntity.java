package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tbl_classroom_membership") @Getter @Setter @NoArgsConstructor
public class ClassroomMembershipEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "classroom_id", nullable = false) private ClassroomEntity classroom;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AccountEntity student;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MembershipStatus status;
    @Column(name = "joined_at", nullable = false) private Instant joinedAt;
    @Column(name = "ended_at") private Instant endedAt;
}
