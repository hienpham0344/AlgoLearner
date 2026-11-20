package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;
public interface ClassroomInvitationRepository extends JpaRepository<ClassroomInvitationEntity, UUID> {
 boolean existsByClassroomId(UUID classroomId);
 List<ClassroomInvitationEntity> findByClassroomIdAndStudentIdAndStatus(UUID classroomId, UUID studentId, InvitationStatus status);
 @EntityGraph(attributePaths = {"classroom", "student"})
 Page<ClassroomInvitationEntity> findByClassroomId(UUID classroomId, Pageable pageable);
 @EntityGraph(attributePaths = {"classroom", "student"})
 Page<ClassroomInvitationEntity> findByStudentId(UUID studentId, Pageable pageable);
}
