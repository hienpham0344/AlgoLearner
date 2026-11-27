package com.algolearner.platform.judge;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.JudgeStatus;

@Entity
@Table(name = "tbl_submission_test_result")
@Getter @Setter @NoArgsConstructor
public class SubmissionTestResultEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "evaluation_id", nullable = false) private SubmissionEvaluationEntity evaluation;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "snapshot_id", nullable = false) private SubmissionTestCaseSnapshotEntity snapshot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private JudgeStatus status;
    @Column(nullable = false) private boolean passed;
    @Column(name = "actual_output") private String actualOutput;
    @Column(name = "stderr_output") private String stderrOutput;
    @Column(name = "execution_time_ms") private Long executionTimeMs;
    @Column(name = "memory_used_kb") private Long memoryUsedKb;
}
