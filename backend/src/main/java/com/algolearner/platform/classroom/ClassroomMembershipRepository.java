package com.algolearner.platform.classroom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional; import java.util.UUID; import java.util.List;
public interface ClassroomMembershipRepository extends JpaRepository<ClassroomMembershipEntity, UUID> {
 Optional<ClassroomMembershipEntity> findByClassroomIdAndStudentId(UUID classroomId, UUID studentId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select m from ClassroomMembershipEntity m where m.classroom.id = :classroomId and m.student.id = :studentId")
 Optional<ClassroomMembershipEntity> findByClassroomIdAndStudentIdForUpdate(@Param("classroomId") UUID classroomId, @Param("studentId") UUID studentId);
 boolean existsByClassroomId(UUID classroomId);
 @EntityGraph(attributePaths = {"classroom", "classroom.owner"})
 Page<ClassroomMembershipEntity> findByStudentIdAndStatus(UUID studentId, MembershipStatus status, Pageable pageable);
 @EntityGraph(attributePaths = "student")
 Page<ClassroomMembershipEntity> findByClassroomIdAndStatus(UUID classroomId, MembershipStatus status, Pageable pageable);
 List<ClassroomMembershipEntity> findByStudentIdAndStatus(UUID studentId, MembershipStatus status);
 List<ClassroomMembershipEntity> findByClassroomIdAndStatus(UUID classroomId, MembershipStatus status);
}
