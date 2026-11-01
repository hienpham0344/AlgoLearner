package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.UUID; import java.util.List;
public interface ClassroomRepository extends JpaRepository<ClassroomEntity, UUID> {
 Optional<ClassroomEntity> findByClassCode(String classCode);
 boolean existsByClassCode(String classCode);
 List<ClassroomEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
