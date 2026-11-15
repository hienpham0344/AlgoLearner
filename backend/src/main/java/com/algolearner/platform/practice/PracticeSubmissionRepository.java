package com.algolearner.platform.practice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSubmissionRepository extends JpaRepository<PracticeSubmissionEntity, UUID> {
    Page<PracticeSubmissionEntity> findByPracticeIdAndStudentId(UUID practiceId, UUID studentId, Pageable pageable);
    Page<PracticeSubmissionEntity> findByPracticeId(UUID practiceId, Pageable pageable);
    Optional<PracticeSubmissionEntity> findByIdAndPracticeId(UUID id, UUID practiceId);
    boolean existsByPracticeIdAndTestcaseVersion(UUID practiceId, int testcaseVersion);

    /** Flags submissions judged against an older testcase version. Verdicts and completions are left untouched. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PracticeSubmissionEntity s SET s.rejudgeStatus = :status WHERE s.practice.id = :practiceId AND s.testcaseVersion < :version")
    int markForRejudge(@Param("practiceId") UUID practiceId, @Param("version") int version, @Param("status") RejudgeStatus status);
}
