package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.util.UUID;

/** Append-only audit record: automatic attempt scores are never overwritten. */
@Entity @Immutable
@Table(name = "tbl_quiz_grade_override")
@Getter @Setter @NoArgsConstructor
public class QuizGradeOverrideEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "quiz_id", nullable = false)
    private QuizEntity quiz;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false)
    private AccountEntity student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id", nullable = false)
    private AccountEntity actor;
    @Column(nullable = false) private int revision;
    @Column(name = "original_score", nullable = false, precision = 12, scale = 4) private BigDecimal originalScore;
    @Column(name = "final_score", nullable = false, precision = 12, scale = 4) private BigDecimal finalScore;
    @Column(nullable = false, length = 2000) private String reason;
}
