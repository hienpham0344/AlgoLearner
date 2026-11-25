package com.algolearner.platform.notification;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    interface ClassroomContext {
        UUID getNotificationId();
        UUID getClassroomId();
        String getClassroomName();
    }

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT n.id AS notificationId, c.id AS classroomId, c.name AS classroomName
            FROM tbl_notification n
            LEFT JOIN tbl_classroom_invitation i ON i.id=n.classroom_invitation_id
            LEFT JOIN tbl_announcement a ON a.id=n.announcement_id
            LEFT JOIN tbl_assignment s ON s.id=n.assignment_id
            LEFT JOIN tbl_classroom c ON c.id=COALESCE(i.classroom_id,a.classroom_id,s.classroom_id)
            WHERE n.id IN (:ids)
            """, nativeQuery = true)
    java.util.List<ClassroomContext> findClassroomContexts(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);

    Optional<NotificationEntity> findByInvitationId(UUID invitationId);
    @EntityGraph(attributePaths = {"invitation", "invitation.classroom"})
    Page<NotificationEntity> findByRecipientId(UUID recipientId, Pageable pageable);
}
