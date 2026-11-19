package com.algolearner.platform.judge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubmissionTestCaseSnapshotRepository extends JpaRepository<SubmissionTestCaseSnapshotEntity, UUID> {
    List<SubmissionTestCaseSnapshotEntity> findByEvaluationIdOrderByOrdinalNumber(UUID evaluationId);
}
