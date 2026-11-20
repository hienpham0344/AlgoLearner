package com.algolearner.platform.announcement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, UUID> {

    @EntityGraph(attributePaths = {"author", "classroom"})
    @Query("SELECT a FROM AnnouncementEntity a WHERE a.classroom.id = :classroomId ORDER BY a.isPinned DESC, a.pinnedAt DESC, a.createdAt DESC")
    Page<AnnouncementEntity> findAllByClassroomIdSorted(@Param("classroomId") UUID classroomId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "classroom"})
    Optional<AnnouncementEntity> findByIdAndClassroomId(UUID id, UUID classroomId);
}
