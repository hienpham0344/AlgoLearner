package com.algolearner.platform.grading;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface GradebookRepository extends JpaRepository<GradebookEntity, UUID> {
    Optional<GradebookEntity> findByClassroomId(UUID classroomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GradebookEntity> findLockedByClassroomId(UUID classroomId);
}
