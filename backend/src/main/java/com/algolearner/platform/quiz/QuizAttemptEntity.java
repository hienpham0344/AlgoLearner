package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tbl_quiz_attempt", uniqueConstraints = @UniqueConstraint(name = "uq_quiz_attempt_number", columnNames = {"quiz_id", "student_id", "attempt_number"}))
@Getter @Setter @NoArgsConstructor
public class QuizAttemptEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "quiz_id", nullable = false)
    private QuizEntity quiz;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false)
    private AccountEntity student;
    @Column(name = "attempt_number", nullable = false) private int number;
    @Column(nullable = false, columnDefinition = "text") private String snapshot;
    @Column(nullable = false, columnDefinition = "text") private String answers = "{}";
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    private Instant deadline;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(nullable = false) private boolean late;
    @Column(precision = 12, scale = 4) private BigDecimal score;
    @Column(nullable = false) private boolean completed;
}
