package com.algolearner.platform.practice;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ClassroomPracticeRepository extends JpaRepository<ClassroomPracticeEntity, UUID> {
    Optional<ClassroomPracticeEntity> findByIdAndClassroomId(UUID id, UUID classroomId);
    Page<ClassroomPracticeEntity> findByClassroomId(UUID classroomId, Pageable pageable);
    Page<ClassroomPracticeEntity> findByClassroomIdAndStatus(UUID classroomId, PracticeStatus status, Pageable pageable);
    boolean existsByClassroomId(UUID classroomId);

    /**
     * Same page as {@link #findByClassroomIdAndStatus} minus practices attached to one of {@code excludedLessonIds}.
     * Filtering in SQL keeps page contents and totals consistent. {@code excludedLessonIds} must not be empty.
     */
    @Query("SELECT p FROM ClassroomPracticeEntity p WHERE p.classroom.id = :classroomId AND p.status = :status "
            + "AND (p.lessonId IS NULL OR p.lessonId NOT IN :excludedLessonIds)")
    Page<ClassroomPracticeEntity> findVisible(@Param("classroomId") UUID classroomId, @Param("status") PracticeStatus status,
                                              @Param("excludedLessonIds") Collection<UUID> excludedLessonIds, Pageable pageable);

    /** Row lock serializing testcase versioning against SUBMIT; call inside a read-write transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ClassroomPracticeEntity p WHERE p.id = :id AND p.classroom.id = :classroomId")
    Optional<ClassroomPracticeEntity> lockByIdAndClassroomId(@Param("id") UUID id, @Param("classroomId") UUID classroomId);
}
