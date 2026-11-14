package com.algolearner.platform.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    List<AssignmentEntity> findByClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    List<AssignmentEntity> findByClassroomIdAndStatusInOrderByCreatedAtDesc(UUID classroomId, List<AssignmentStatus> statuses);

    Optional<AssignmentEntity> findByIdAndClassroomId(UUID id, UUID classroomId);

    long countByClassroomId(UUID classroomId);
}
