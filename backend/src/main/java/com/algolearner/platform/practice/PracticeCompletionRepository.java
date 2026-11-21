package com.algolearner.platform.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PracticeCompletionRepository extends JpaRepository<PracticeCompletionEntity, PracticeCompletionId> {
    @Query("SELECT c FROM PracticeCompletionEntity c WHERE c.id.practiceId = :practiceId AND c.id.studentId IN :studentIds")
    List<PracticeCompletionEntity> findForPractice(@Param("practiceId") UUID practiceId, @Param("studentIds") Collection<UUID> studentIds);

    @Query("SELECT c FROM PracticeCompletionEntity c WHERE c.id.studentId = :studentId AND c.id.practiceId IN :practiceIds")
    List<PracticeCompletionEntity> findForStudent(@Param("studentId") UUID studentId, @Param("practiceIds") Collection<UUID> practiceIds);

    /**
     * Atomic "first ACCEPTED wins": concurrent inserts for the same (student, practice) cannot fail with a
     * primary-key violation, the loser simply inserts nothing. Returns 1 when this call created the completion.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO tbl_practice_completion (student_id, practice_id, first_accepted_submission_id, completed_at, created_at, updated_at) "
            + "VALUES (:studentId, :practiceId, :submissionId, :now, :now, :now) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("studentId") UUID studentId, @Param("practiceId") UUID practiceId,
                       @Param("submissionId") UUID submissionId, @Param("now") Instant now);
}
