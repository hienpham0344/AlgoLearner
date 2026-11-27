package com.algolearner.platform.grading;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeSchemeRevisionRepository extends JpaRepository<GradeSchemeRevisionEntity, UUID> {
    Optional<GradeSchemeRevisionEntity> findByIdAndGradebookId(UUID id, UUID gradebookId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM tbl_grade_scheme_revision r
                JOIN tbl_gradebook g ON g.id = r.gradebook_id
                WHERE g.classroom_id = :classroomId
            )
            """, nativeQuery = true)
    boolean existsForClassroom(@Param("classroomId") UUID classroomId);
}
