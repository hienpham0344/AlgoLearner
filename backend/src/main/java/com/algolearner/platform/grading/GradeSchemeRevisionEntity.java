package com.algolearner.platform.grading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "tbl_grade_scheme_revision")
@Immutable
@Getter
@Setter
public class GradeSchemeRevisionEntity {
    public enum Mode { WEIGHTED, RAW }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "gradebook_id", nullable = false)
    private UUID gradebookId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private Mode mode;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
