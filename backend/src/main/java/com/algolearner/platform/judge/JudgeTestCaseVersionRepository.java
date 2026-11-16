package com.algolearner.platform.judge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JudgeTestCaseVersionRepository extends JpaRepository<JudgeTestCaseVersionEntity, UUID> {
    Optional<JudgeTestCaseVersionEntity> findByProblemIdAndActiveTrue(UUID problemId);
    Optional<JudgeTestCaseVersionEntity> findTopByProblemIdOrderByVersionNumberDesc(UUID problemId);
}
