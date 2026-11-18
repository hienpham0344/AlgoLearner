package com.algolearner.platform.assignment;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tbl_assignment_question")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentQuestionEntity extends BaseAuditableEntity {

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "code_content_snapshot", columnDefinition = "jsonb")
    private String codeContentSnapshot;

    @Column(name = "question_weight", nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal questionWeight = BigDecimal.ONE;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "code_case_weights", columnDefinition = "jsonb")
    private List<BigDecimal> codeCaseWeights;

    @Column(name = "retired", nullable = false)
    @Builder.Default private boolean retired=false;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private AssignmentEntity assignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "points", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal points = BigDecimal.valueOf(10.0);

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_policy", length = 30)
    @Builder.Default
    private GradingPolicy gradingPolicy = GradingPolicy.ALL_OR_NOTHING;

    @Enumerated(EnumType.STRING)
    @Column(name = "essay_format", length = 30)
    private EssayFormat essayFormat;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "code_template", columnDefinition = "TEXT")
    private String codeTemplate;

    @Column(name = "code_language", length = 30)
    private String codeLanguage;

    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssignmentQuestionOptionEntity> options = new ArrayList<>();
}
