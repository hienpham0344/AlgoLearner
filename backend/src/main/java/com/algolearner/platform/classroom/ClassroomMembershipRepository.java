package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.UUID; import java.util.List;
public interface ClassroomMembershipRepository extends JpaRepository<ClassroomMembershipEntity, UUID> {
 Optional<ClassroomMembershipEntity> findByClassroomIdAndStudentId(UUID classroomId, UUID studentId);
 boolean existsByClassroomId(UUID classroomId);
 List<ClassroomMembershipEntity> findByStudentIdAndStatus(UUID studentId, MembershipStatus status);
 List<ClassroomMembershipEntity> findByClassroomIdAndStatus(UUID classroomId, MembershipStatus status);
}
