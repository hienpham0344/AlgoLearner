package com.algolearner.platform.grading;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradebookRepository extends JpaRepository<GradebookEntity, UUID> {
    Optional<GradebookEntity> findByClassroomId(UUID classroomId);
}
