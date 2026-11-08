package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional; import java.util.UUID; import java.util.List;
public interface ClassroomJoinRequestRepository extends JpaRepository<ClassroomJoinRequestEntity, UUID> {
 Optional<ClassroomJoinRequestEntity> findByClassroomIdAndStudentIdAndStatus(UUID classroomId, UUID studentId, JoinRequestStatus status);
 List<ClassroomJoinRequestEntity> findByClassroomIdAndStatus(UUID classroomId, JoinRequestStatus status);
 Page<ClassroomJoinRequestEntity> findByClassroomIdAndStatus(UUID classroomId, JoinRequestStatus status, Pageable pageable);
 boolean existsByClassroomId(UUID classroomId);
}
