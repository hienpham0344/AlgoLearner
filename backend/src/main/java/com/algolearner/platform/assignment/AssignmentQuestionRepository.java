package com.algolearner.platform.assignment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentQuestionRepository extends JpaRepository<AssignmentQuestionEntity, UUID> {

    List<AssignmentQuestionEntity> findByAssignmentIdOrderByDisplayOrderAsc(UUID assignmentId);

    Optional<AssignmentQuestionEntity> findByIdAndAssignmentId(UUID id, UUID assignmentId);

    long countByAssignmentId(UUID assignmentId);
}
