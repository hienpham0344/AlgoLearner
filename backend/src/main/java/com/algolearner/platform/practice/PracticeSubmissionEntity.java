package com.algolearner.platform.practice;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tbl_practice_submission")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class PracticeSubmissionEntity extends BaseAuditableEntity {
    @Id @ToString.Include @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "practice_id", nullable = false) private ClassroomPracticeEntity practice;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AccountEntity student;
    /** The judged snapshot: testcase rows of (practice, testcaseVersion) are kept for the life of the submission. */
    @Column(name = "testcase_version", nullable = false) private int testcaseVersion;
    @Column(nullable = false, columnDefinition = "TEXT") private String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PracticeLanguage language;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PracticeVerdict verdict;
    @Column(name = "tests_passed", nullable = false) private int testsPassed;
    @Column(name = "total_tests", nullable = false) private int totalTests;
    @Column(name = "runtime_ms") private Integer runtimeMs;
    @Column(name = "memory_kb") private Integer memoryKb;
    /** Raw judge output; internal, never serialized to students. */
    @Column(name = "judge_details", columnDefinition = "TEXT") private String judgeDetails;
    @Enumerated(EnumType.STRING) @Column(name = "rejudge_status", length = 20) private RejudgeStatus rejudgeStatus;
    @Column(name = "submitted_at", nullable = false) private Instant submittedAt;

    @Override public boolean equals(Object o) { return o instanceof PracticeSubmissionEntity other && id != null && id.equals(other.id); }
    @Override public int hashCode() { return getClass().hashCode(); }
}
