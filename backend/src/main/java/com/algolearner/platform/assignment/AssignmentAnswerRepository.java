package com.algolearner.platform.assignment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentAnswerRepository extends JpaRepository<AssignmentAnswerEntity, UUID> {

    @EntityGraph(attributePaths = "question")
    List<AssignmentAnswerEntity> findByAttemptId(UUID attemptId);

    @EntityGraph(attributePaths = "question")
    Optional<AssignmentAnswerEntity> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);
}
