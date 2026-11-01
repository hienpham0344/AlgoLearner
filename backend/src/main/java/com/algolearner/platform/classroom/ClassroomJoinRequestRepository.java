package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.UUID; import java.util.List;
public interface ClassroomJoinRequestRepository extends JpaRepository<ClassroomJoinRequestEntity, UUID> {
 Optional<ClassroomJoinRequestEntity> findByClassroomIdAndStudentIdAndStatus(UUID classroomId, UUID studentId, JoinRequestStatus status);
 List<ClassroomJoinRequestEntity> findByClassroomIdAndStatus(UUID classroomId, JoinRequestStatus status);
 boolean existsByClassroomId(UUID classroomId);
}
