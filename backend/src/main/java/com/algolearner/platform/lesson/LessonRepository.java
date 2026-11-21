package com.algolearner.platform.lesson;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<LessonEntity, UUID> {

    List<LessonEntity> findByClassroomIdOrderByDisplayOrderAsc(UUID classroomId);

    List<LessonEntity> findByClassroomIdAndStatusInOrderByDisplayOrderAsc(UUID classroomId, List<LessonStatus> statuses);

    Optional<LessonEntity> findByIdAndClassroomId(UUID id, UUID classroomId);

    long countByClassroomId(UUID classroomId);
}
