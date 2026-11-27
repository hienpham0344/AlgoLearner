package com.algolearner.platform.judge;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static com.algolearner.platform.judge.JudgeTypes.*;

@Entity
@Table(name = "tbl_rejudge_history")
@Getter @Setter @NoArgsConstructor
public class RejudgeHistoryEntity extends BaseAuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "submission_id", nullable = false) private SubmissionEntity submission;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requested_by", nullable = false) private AccountEntity requestedBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "previous_evaluation_id") private SubmissionEvaluationEntity previousEvaluation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "new_evaluation_id") private SubmissionEvaluationEntity newEvaluation;
    @Enumerated(EnumType.STRING) @Column(name = "old_status", nullable = false, length = 40) private JudgeStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", length = 40) private JudgeStatus newStatus;
    @Enumerated(EnumType.STRING) @Column(name = "operation_status", nullable = false, length = 20) private RejudgeStatus status;
    @Column(length = 1000) private String reason;
    @Column(name = "failure_message") private String failureMessage;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
