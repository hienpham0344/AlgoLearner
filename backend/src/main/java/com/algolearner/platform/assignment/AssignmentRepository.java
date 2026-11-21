package com.algolearner.platform.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    List<AssignmentEntity> findByClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    List<AssignmentEntity> findByClassroomIdAndStatusInOrderByCreatedAtDesc(UUID classroomId, List<AssignmentStatus> statuses);

    Optional<AssignmentEntity> findByIdAndClassroomId(UUID id, UUID classroomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssignmentEntity a where a.id = :id and a.classroom.id = :classroomId")
    Optional<AssignmentEntity> findForUpdate(@Param("id") UUID id, @Param("classroomId") UUID classroomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssignmentEntity a where a.id = :id")
    Optional<AssignmentEntity> findForUpdateById(@Param("id") UUID id);

    @Query(value="""
        SELECT a.id FROM tbl_assignment a WHERE a.grade_publish_mode='AFTER_DEADLINE' AND a.deadline<=:now
        AND EXISTS(SELECT 1 FROM tbl_assignment_attempt t WHERE t.assignment_id=a.id AND t.status='GRADED'
          AND t.current_evaluation_id IS NOT NULL AND t.current_evaluation_id IS DISTINCT FROM t.published_evaluation_id)
        ORDER BY a.deadline LIMIT 100
        """,nativeQuery=true)
    List<UUID> findDuePublicationIds(@Param("now") java.time.Instant now);

    @Query(value="""
        SELECT a.id FROM tbl_assignment a
        JOIN tbl_classroom c ON c.id=a.classroom_id
        JOIN tbl_account owner ON owner.id=c.owner_id
        WHERE a.status='SCHEDULED' AND a.start_time<=:now AND c.status='ACTIVE' AND owner.status='ACTIVE'
        ORDER BY a.start_time,a.id LIMIT 100
        """,nativeQuery=true)
    List<UUID> findDueScheduledIds(@Param("now") java.time.Instant now);

    long countByClassroomId(UUID classroomId);
}
