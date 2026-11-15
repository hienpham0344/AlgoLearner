package com.algolearner.platform.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PracticeCompletionRepository extends JpaRepository<PracticeCompletionEntity, PracticeCompletionId> {
    @Query("SELECT c FROM PracticeCompletionEntity c WHERE c.id.practiceId = :practiceId AND c.id.studentId IN :studentIds")
    List<PracticeCompletionEntity> findForPractice(@Param("practiceId") UUID practiceId, @Param("studentIds") Collection<UUID> studentIds);

    @Query("SELECT c FROM PracticeCompletionEntity c WHERE c.id.studentId = :studentId AND c.id.practiceId IN :practiceIds")
    List<PracticeCompletionEntity> findForStudent(@Param("studentId") UUID studentId, @Param("practiceIds") Collection<UUID> practiceIds);
}
