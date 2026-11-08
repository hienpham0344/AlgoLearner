package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional; import java.util.UUID; import java.util.List;
public interface ClassroomMembershipRepository extends JpaRepository<ClassroomMembershipEntity, UUID> {
 Optional<ClassroomMembershipEntity> findByClassroomIdAndStudentId(UUID classroomId, UUID studentId);
 boolean existsByClassroomId(UUID classroomId);
 Page<ClassroomMembershipEntity> findByStudentIdAndStatus(UUID studentId, MembershipStatus status, Pageable pageable);
 Page<ClassroomMembershipEntity> findByClassroomIdAndStatus(UUID classroomId, MembershipStatus status, Pageable pageable);
 List<ClassroomMembershipEntity> findByStudentIdAndStatus(UUID studentId, MembershipStatus status);
 List<ClassroomMembershipEntity> findByClassroomIdAndStatus(UUID classroomId, MembershipStatus status);
}
