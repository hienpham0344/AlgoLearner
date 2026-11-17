package com.algolearner.platform.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ClassroomInvitationRepository extends JpaRepository<ClassroomInvitationEntity, UUID> {
 boolean existsByClassroomId(UUID classroomId);
}
