package com.algolearner.platform.assignment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentQuestionRepository extends JpaRepository<AssignmentQuestionEntity, UUID> {

    @org.springframework.data.jpa.repository.Query("select q from AssignmentQuestionEntity q where q.assignment.id=:assignmentId and q.retired=false order by q.displayOrder")
    List<AssignmentQuestionEntity> findByAssignmentIdOrderByDisplayOrderAsc(@org.springframework.data.repository.query.Param("assignmentId") UUID assignmentId);

    Optional<AssignmentQuestionEntity> findByIdAndAssignmentId(UUID id, UUID assignmentId);

    @org.springframework.data.jpa.repository.Query("select count(q) from AssignmentQuestionEntity q where q.assignment.id=:assignmentId and q.retired=false")
    long countByAssignmentId(@org.springframework.data.repository.query.Param("assignmentId") UUID assignmentId);
}
