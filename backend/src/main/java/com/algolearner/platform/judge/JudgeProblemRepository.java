package com.algolearner.platform.judge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.JudgeContextType;

public interface JudgeProblemRepository extends JpaRepository<JudgeProblemEntity, UUID> {
    Optional<JudgeProblemEntity> findByContextTypeAndContextId(JudgeContextType contextType, UUID contextId);
}
