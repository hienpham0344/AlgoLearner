package com.algolearner.platform.judge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.TestCaseVisibility;

public interface JudgeTestCaseRepository extends JpaRepository<JudgeTestCaseEntity, UUID> {
    List<JudgeTestCaseEntity> findByVersionIdOrderByOrdinalNumber(UUID versionId);
    List<JudgeTestCaseEntity> findByVersionIdAndVisibilityOrderByOrdinalNumber(UUID versionId, TestCaseVisibility visibility);
    boolean existsByVersionId(UUID versionId);
}
