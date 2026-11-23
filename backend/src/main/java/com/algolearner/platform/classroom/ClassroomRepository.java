package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional; import java.util.UUID; import java.util.List;
public interface ClassroomRepository extends JpaRepository<ClassroomEntity, UUID> {
 @org.springframework.data.jpa.repository.Query(value = "select exists (select 1 from tbl_quiz where classroom_id = :classroomId)", nativeQuery = true)
 boolean hasQuizData(@org.springframework.data.repository.query.Param("classroomId") UUID classroomId);
 Optional<ClassroomEntity> findByClassCode(String classCode);
 boolean existsByClassCode(String classCode);
 Page<ClassroomEntity> findByOwnerId(UUID ownerId, Pageable pageable);
 List<ClassroomEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
