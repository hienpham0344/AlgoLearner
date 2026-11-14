package com.algolearner.platform.notification;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    Optional<NotificationEntity> findByInvitationId(UUID invitationId);
    @EntityGraph(attributePaths = {"invitation", "invitation.classroom"})
    Page<NotificationEntity> findByRecipientId(UUID recipientId, Pageable pageable);
}
