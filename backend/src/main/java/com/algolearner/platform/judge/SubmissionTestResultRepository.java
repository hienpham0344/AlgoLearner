package com.algolearner.platform.judge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResultEntity, UUID> {
    List<SubmissionTestResultEntity> findByEvaluationIdOrderBySnapshotOrdinalNumber(UUID evaluationId);
}
