package com.algolearner.platform.assignment;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_assignment_revision")
@Getter @NoArgsConstructor
public class AssignmentRevisionEntity extends BaseAuditableEntity {
    @Id @Column(updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private AssignmentEntity assignment;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false) private String content;
    @Column(nullable = false, updatable = false, length = 30) private String provenance;
    AssignmentRevisionEntity(AssignmentEntity assignment, String content) {
        this.id = UUID.randomUUID(); this.assignment = assignment; this.content = content; this.provenance = "TRUSTED";
    }
}
