package com.algolearner.platform.judge;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tbl_judge_test_case_version")
@Getter @Setter @NoArgsConstructor
public class JudgeTestCaseVersionEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "problem_id", nullable = false) private JudgeProblemEntity problem;
    @Column(name = "version_no", nullable = false) private int versionNumber;
    @Column(nullable = false) private boolean active;
    @Column(name = "checksum_sha256", nullable = false, length = 64) private String checksumSha256;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) private AccountEntity createdBy;
}
