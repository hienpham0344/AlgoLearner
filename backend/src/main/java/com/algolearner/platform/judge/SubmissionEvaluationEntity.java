package com.algolearner.platform.judge;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.*;

@Entity
@Table(name = "tbl_submission_evaluation")
@Getter @Setter @NoArgsConstructor
public class SubmissionEvaluationEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "submission_id", nullable = false) private SubmissionEntity submission;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "test_case_version_id", nullable = false) private JudgeTestCaseVersionEntity testCaseVersion;
    @Column(name = "attempt_no", nullable = false) private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 20) private EvaluationTrigger triggerType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requested_by", nullable = false) private AccountEntity requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private JudgeStatus status;
    @Column(name = "current_official", nullable = false) private boolean currentOfficial;
    @Column(name = "judge_provider", length = 100) private String judgeProvider;
    @Column(name = "execution_time_ms") private Long executionTimeMs;
    @Column(name = "memory_used_kb") private Long memoryUsedKb;
    @Column(name = "compiler_output") private String compilerOutput;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
