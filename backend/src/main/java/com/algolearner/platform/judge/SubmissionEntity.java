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
@Table(name = "tbl_judge_submission")
@Getter @Setter @NoArgsConstructor
public class SubmissionEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "problem_id", nullable = false) private JudgeProblemEntity problem;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "submitter_id", nullable = false) private AccountEntity submitter;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "test_case_version_id", nullable = false) private JudgeTestCaseVersionEntity testCaseVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private JudgeLanguage language;
    @Column(name = "source_code", nullable = false) private String sourceCode;
    @Column(name = "source_code_sha256", nullable = false, length = 64) private String sourceCodeSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private JudgeStatus status;
    @Column(nullable = false) private boolean official;
    @Column(name = "time_limit_ms", nullable = false) private int timeLimitMs;
    @Column(name = "memory_limit_mb", nullable = false) private int memoryLimitMb;
    @Enumerated(EnumType.STRING) @Column(name = "feedback_level", nullable = false, length = 30) private JudgeFeedbackLevel feedbackLevel;
    @Column(name = "execution_time_ms") private Long executionTimeMs;
    @Column(name = "memory_used_kb") private Long memoryUsedKb;
    @Column(name = "compiler_output") private String compilerOutput;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "completed_at") private Instant completedAt;
}
