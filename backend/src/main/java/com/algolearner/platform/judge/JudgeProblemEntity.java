package com.algolearner.platform.judge;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.*;

@Entity
@Table(name = "tbl_judge_problem")
@Getter @Setter @NoArgsConstructor
public class JudgeProblemEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_id", nullable = false) private AccountEntity owner;
    @Enumerated(EnumType.STRING) @Column(name = "context_type", nullable = false, length = 30) private JudgeContextType contextType;
    @Column(name = "context_id", nullable = false) private UUID contextId;
    @Column(name = "classroom_id") private UUID classroomId;
    @Column(nullable = false, length = 255) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JudgeProblemFormat format;
    @Column(name = "time_limit_ms", nullable = false) private int timeLimitMs;
    @Column(name = "memory_limit_mb", nullable = false) private int memoryLimitMb;
    @Enumerated(EnumType.STRING) @Column(name = "feedback_level", nullable = false, length = 30) private JudgeFeedbackLevel feedbackLevel;
    @Column(name = "enabled_languages", nullable = false, length = 32) private String enabledLanguages;
    @Column(name = "starter_code_java") private String starterCodeJava;
    @Column(name = "starter_code_python") private String starterCodePython;
    @Column(name = "active", nullable = false) private boolean active;

    boolean supports(JudgeLanguage language) {
        return enabledLanguages != null && java.util.Arrays.asList(enabledLanguages.split(",")).contains(language.name());
    }
}
