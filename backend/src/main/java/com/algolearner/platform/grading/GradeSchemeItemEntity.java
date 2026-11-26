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
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "tbl_grade_scheme_item")
@Immutable
@Getter
@Setter
public class GradeSchemeItemEntity {
    public enum SourceType { ASSIGNMENT, QUIZ }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "revision_id", nullable = false)
    private UUID revisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal weight;

    @Column(name = "required", nullable = false)
    private boolean required = true;
}
