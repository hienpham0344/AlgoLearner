package com.algolearner.platform.practice;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.time.Instant;

/** One row per (student, practice), written once on the first ACCEPTED submission. Never read by grading. */
@Entity
@Table(name = "tbl_practice_completion")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class PracticeCompletionEntity extends BaseAuditableEntity {
    @EmbeddedId @ToString.Include private PracticeCompletionId id;
    @MapsId("studentId") @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AccountEntity student;
    @MapsId("practiceId") @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "practice_id", nullable = false) private ClassroomPracticeEntity practice;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "first_accepted_submission_id", nullable = false) private PracticeSubmissionEntity firstAcceptedSubmission;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;

    @Override public boolean equals(Object o) { return o instanceof PracticeCompletionEntity other && id != null && id.equals(other.id); }
    @Override public int hashCode() { return getClass().hashCode(); }
}
