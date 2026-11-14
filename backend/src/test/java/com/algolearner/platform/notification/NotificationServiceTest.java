package com.algolearner.platform.notification;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomInvitationEntity;
import com.algolearner.platform.classroom.InvitationStatus;
import com.algolearner.platform.common.exception.AppException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private final NotificationService service = new NotificationService(notifications, accounts, clock);

    @Test
    void studentSeesOwnRevokedInvitationInInbox() {
        var student = account(AccountRole.STUDENT);
        var classroom = new ClassroomEntity(); classroom.setId(UUID.randomUUID()); classroom.setName("Algorithms");
        var invitation = new ClassroomInvitationEntity(); invitation.setId(UUID.randomUUID());
        invitation.setClassroom(classroom); invitation.setStudent(student); invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setExpiresAt(clock.instant().plusSeconds(3600));
        var notification = new NotificationEntity(); notification.setId(UUID.randomUUID());
        notification.setRecipient(student); notification.setInvitation(invitation);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(notifications.findByRecipientId(eq(student.getId()), any())).thenReturn(new PageImpl<>(List.of(notification)));

        var result = service.myInvitations(student.getId(), PageRequest.of(0, 20));

        assertThat(result.getData().getItems()).hasSize(1);
        assertThat(result.getData().getItems().get(0).status()).isEqualTo("REVOKED");
        assertThat(result.getData().getItems().get(0).classroomName()).isEqualTo("Algorithms");
    }

    @Test
    void teacherCannotReadStudentInbox() {
        var teacher = account(AccountRole.TEACHER);
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        assertThatThrownBy(() -> service.myInvitations(teacher.getId(), PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getHttpStatus().value()).isEqualTo(403);
        verifyNoInteractions(notifications);
    }

    @Test
    void recipientCanMarkNotificationReadIdempotently() {
        var student = account(AccountRole.STUDENT);
        var notification = new NotificationEntity(); notification.setId(UUID.randomUUID()); notification.setRecipient(student);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));

        service.markRead(student.getId(), notification.getId());
        Instant firstRead = notification.getReadAt();
        service.markRead(student.getId(), notification.getId());

        assertThat(firstRead).isEqualTo(clock.instant());
        assertThat(notification.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void otherStudentCannotMarkNotificationRead() {
        var recipient = account(AccountRole.STUDENT); var other = account(AccountRole.STUDENT);
        var notification = new NotificationEntity(); notification.setId(UUID.randomUUID()); notification.setRecipient(recipient);
        when(accounts.findById(other.getId())).thenReturn(Optional.of(other));
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markRead(other.getId(), notification.getId()))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getHttpStatus().value()).isEqualTo(404);
        assertThat(notification.getReadAt()).isNull();
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity(); account.setId(UUID.randomUUID());
        account.setRole(role); account.setStatus(AccountStatus.ACTIVE); return account;
    }
}
