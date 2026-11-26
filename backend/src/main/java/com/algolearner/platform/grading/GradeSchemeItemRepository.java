package com.algolearner.platform.grading;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeSchemeItemRepository extends JpaRepository<GradeSchemeItemEntity, UUID> {
    List<GradeSchemeItemEntity> findAllByRevisionId(UUID revisionId);
}
