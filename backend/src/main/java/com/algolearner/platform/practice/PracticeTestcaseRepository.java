package com.algolearner.platform.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeTestcaseRepository extends JpaRepository<PracticeTestcaseEntity, UUID> {
    List<PracticeTestcaseEntity> findByPracticeIdAndVersionOrderByOrderIndexAsc(UUID practiceId, int version);
    List<PracticeTestcaseEntity> findByPracticeIdAndVersionAndSampleTrueOrderByOrderIndexAsc(UUID practiceId, int version);
    Optional<PracticeTestcaseEntity> findByIdAndPracticeIdAndVersion(UUID id, UUID practiceId, int version);
    long countByPracticeIdAndVersionAndSample(UUID practiceId, int version, boolean sample);
    /** Removes every version; only used when deleting a practice that never had a submission. */
    void deleteByPracticeId(UUID practiceId);
}
