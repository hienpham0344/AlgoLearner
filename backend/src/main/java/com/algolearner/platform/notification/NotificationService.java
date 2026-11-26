package com.algolearner.platform.notification;

import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.common.exception.AppException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final AccountRepository accounts;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, AccountRepository accounts, Clock clock) {
        this.notifications = notifications; this.accounts = accounts; this.clock = clock;
    }

    public PageResponse<NotificationDtos.InvitationNotificationView> myInvitations(UUID accountId, Pageable pageable) {
        requireStudent(accountId);
        var page = notifications.findByRecipientId(accountId, PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 100), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        var contexts = page.isEmpty() ? java.util.Map.<UUID, NotificationRepository.ClassroomContext>of()
                : notifications.findClassroomContexts(page.getContent().stream().map(NotificationEntity::getId).toList())
                        .stream().collect(java.util.stream.Collectors.toMap(NotificationRepository.ClassroomContext::getNotificationId, item -> item));
        return PageResponse.fromPage(page.map(notification -> {
            var invitation = notification.getInvitation();
            var context = contexts.get(notification.getId());
            var classroom = invitation != null ? invitation.getClassroom() : null;
            UUID classroomId = context != null ? context.getClassroomId() : classroom == null ? null : classroom.getId();
            String classroomName = context != null ? context.getClassroomName() : classroom == null ? null : classroom.getName();
            String targetType = invitation != null ? "CLASS_INVITATION"
                    : notification.getAnnouncementId() != null ? "ANNOUNCEMENT"
                    : notification.getAssignmentId() != null ? "ASSIGNMENT"
                    : notification.getGradebookResultId() != null ? "GRADEBOOK" : "SYSTEM";
            UUID targetId = invitation != null ? invitation.getId()
                    : notification.getAnnouncementId() != null ? notification.getAnnouncementId()
                    : notification.getAssignmentId() != null ? notification.getAssignmentId() : notification.getGradebookResultId();
            String actionUrl = classroomId == null ? null : "/classrooms/" + classroomId
                    + ("ANNOUNCEMENT".equals(targetType) ? "/announcements/" + targetId
                    : "ASSIGNMENT".equals(targetType) ? "/assignments/" + targetId
                    : "GRADEBOOK".equals(targetType) ? "/gradebook" : "");
            String title = notification.getTitle() != null ? notification.getTitle()
                    : invitation != null ? "Lời mời tham gia " + classroomName : notification.getType();
            return new NotificationDtos.InvitationNotificationView(notification.getId(),
                    invitation != null ? invitation.getId() : null,
                    classroomId,
                    classroomName,
                    invitation != null ? invitation.effectiveStatus(clock.instant()).name() : notification.getType(),
                    invitation != null ? invitation.getExpiresAt() : null,
                    notification.getCreatedAt(), notification.getReadAt(), notification.getType(), title,
                    notification.getMessage(), targetType, targetId, actionUrl);
        }));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void notifyGradebookFinalChange(UUID student, UUID classroom, UUID result) {
        notifications.insertGradebookFinalChange(UUID.randomUUID(), student, classroom, result,
                "/classrooms/" + classroom + "/gradebook");
    }

    @Transactional
    public NotificationEntity notifyStudent(com.algolearner.platform.auth.AccountEntity recipient, String type,
                                            String title, String message, String linkUrl,
                                            UUID announcementId, UUID assignmentId) {
        var entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setRecipient(recipient);
        entity.setType(type);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setLinkUrl(linkUrl);
        entity.setAnnouncementId(announcementId);
        entity.setAssignmentId(assignmentId);
        return notifications.save(entity);
    }

    @Transactional
    public void markRead(UUID accountId, UUID notificationId) {
        requireStudent(accountId);
        var notification = notifications.findById(notificationId)
                .filter(item -> item.getRecipient().getId().equals(accountId))
                .orElseThrow(() -> new NotificationAccessException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getReadAt() == null) notification.setReadAt(clock.instant());
    }

    private void requireStudent(UUID accountId) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new NotificationAccessException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE || account.getRole() != AccountRole.STUDENT)
            throw new NotificationAccessException(HttpStatus.FORBIDDEN, "Only active Students may view invitations");
    }

    private static final class NotificationAccessException extends AppException {
        private NotificationAccessException(HttpStatus status, String message) {
            super(message, status, switch (status) {
                case UNAUTHORIZED -> "UNAUTHORIZED_ACCESS";
                case NOT_FOUND -> "NOTIFICATION_NOT_FOUND";
                default -> "FORBIDDEN_OPERATION";
            });
        }
    }
}
