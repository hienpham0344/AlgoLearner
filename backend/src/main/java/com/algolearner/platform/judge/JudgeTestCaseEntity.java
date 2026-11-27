package com.algolearner.platform.judge;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.TestCaseVisibility;

@Entity
@Table(name = "tbl_judge_test_case")
@Getter @Setter @NoArgsConstructor
public class JudgeTestCaseEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "version_id", nullable = false) private JudgeTestCaseVersionEntity version;
    @Column(name = "ordinal", nullable = false) private int ordinalNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TestCaseVisibility visibility;
    @Column(name = "input_data", nullable = false) private String inputData;
    @Column(name = "expected_output", nullable = false) private String expectedOutput;
    @Column(nullable = false, precision = 8, scale = 4) private BigDecimal weight;
}
