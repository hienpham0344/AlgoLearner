package com.algolearner.platform.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentQuestionOptionRepository extends JpaRepository<AssignmentQuestionOptionEntity, UUID> {
    List<AssignmentQuestionOptionEntity> findByQuestionIdOrderByDisplayOrderAsc(UUID questionId);
}
