package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tbl_quiz_override", uniqueConstraints = @UniqueConstraint(name = "uq_quiz_override_student", columnNames = {"quiz_id", "student_id"}))
@Getter @Setter @NoArgsConstructor
public class QuizOverrideEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "quiz_id", nullable = false)
    private QuizEntity quiz;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false)
    private AccountEntity student;
    @Column(name = "extra_attempts", nullable = false) private int extraAttempts;
    @Column(name = "reset_at_count", nullable = false) private int resetAtCount;
    private Instant deadline;
    @Column(name = "time_limit_seconds") private Integer timeLimitSeconds;
}
