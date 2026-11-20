package com.algolearner.platform.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentRejudgeLogRepository extends JpaRepository<AssignmentRejudgeLogEntity, UUID> {
    List<AssignmentRejudgeLogEntity> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);
}
