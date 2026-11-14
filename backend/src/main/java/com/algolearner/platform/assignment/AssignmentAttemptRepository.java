package com.algolearner.platform.assignment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentAttemptRepository extends JpaRepository<AssignmentAttemptEntity, UUID> {

    @EntityGraph(attributePaths = {"answers", "student"})
    List<AssignmentAttemptEntity> findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(UUID assignmentId, UUID studentId);

    @EntityGraph(attributePaths = {"answers"})
    Optional<AssignmentAttemptEntity> findFirstByAssignmentIdAndStudentIdAndStatus(UUID assignmentId, UUID studentId, AttemptStatus status);

    @EntityGraph(attributePaths = {"student"})
    List<AssignmentAttemptEntity> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    long countByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    boolean existsByAssignmentId(UUID assignmentId);
}
