package com.algolearner.platform.practice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ClassroomPracticeRepository extends JpaRepository<ClassroomPracticeEntity, UUID> {
    Optional<ClassroomPracticeEntity> findByIdAndClassroomId(UUID id, UUID classroomId);
    Page<ClassroomPracticeEntity> findByClassroomId(UUID classroomId, Pageable pageable);
    Page<ClassroomPracticeEntity> findByClassroomIdAndStatus(UUID classroomId, PracticeStatus status, Pageable pageable);
}
