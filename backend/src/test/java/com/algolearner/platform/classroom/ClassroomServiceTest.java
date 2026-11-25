package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.notification.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClassroomServiceTest {
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final ClassroomJoinRequestRepository requests = mock(ClassroomJoinRequestRepository.class);
    private final ClassroomInvitationRepository invitations = mock(ClassroomInvitationRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ClassroomPairLock pairLock = mock(ClassroomPairLock.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final com.algolearner.platform.practice.ClassroomPracticeRepository practices = mock(com.algolearner.platform.practice.ClassroomPracticeRepository.class);
    private final ClassroomService service = new ClassroomService(classrooms, memberships, requests, invitations, notifications, accounts, clock, pairLock, entityManager, mock(com.algolearner.platform.audit.AuditService.class), new AccountIdentityResolver(accounts), practices);

    @Test
    void invitationCreatesNotificationForInvitedStudent() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(accounts.findByEmailIgnoreCase(student.getEmail())).thenReturn(Optional.of(student));
        var id = service.invite(owner.getId(), classroom.getId(), new ClassroomDtos.Invite(student.getEmail(), clock.instant().plusSeconds(3600)));
        verify(invitations).save(argThat(invitation -> invitation.getId().equals(id) && invitation.getStudent() == student));
        verify(notifications).save(argThat(notification -> notification.getRecipient() == student
                && notification.getInvitation().getId().equals(id)));
    }

    @Test
    void acceptingInvitationMarksItsNotificationRead() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        var invitation = invitation(classroom, student, clock.instant().plusSeconds(3600));
        var notification = new com.algolearner.platform.notification.NotificationEntity();
        notification.setId(UUID.randomUUID()); notification.setRecipient(student); notification.setInvitation(invitation);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(notifications.findByInvitationId(invitation.getId())).thenReturn(Optional.of(notification));
        service.acceptInvitation(student.getId(), invitation.getId());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(notification.getReadAt()).isEqualTo(clock.instant());
    }

    @Test
    void closedClassRejectsInvitationAcceptanceWithoutChangingMembership() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        classroom.setJoiningOpen(false);
        var invitation = invitation(classroom, student, clock.instant().plusSeconds(3600));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvitation(student.getId(), invitation.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("JOINING_CLOSED");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        verifyNoInteractions(memberships);
    }

    @Test
    void decliningInvitationMarksItsNotificationRead() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        var invitation = invitation(classroom, student, clock.instant().plusSeconds(3600));
        var notification = new com.algolearner.platform.notification.NotificationEntity();
        notification.setId(UUID.randomUUID()); notification.setRecipient(student); notification.setInvitation(invitation);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(notifications.findByInvitationId(invitation.getId())).thenReturn(Optional.of(notification));
        service.declineInvitation(student.getId(), invitation.getId());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(notification.getReadAt()).isEqualTo(clock.instant());
    }

    @Test
    void studentCanDeclineInvitationAfterClassIsArchived() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        classroom.setStatus(ClassroomStatus.ARCHIVED);
        var invitation = invitation(classroom, student, clock.instant().plusSeconds(3600));
        var notification = new com.algolearner.platform.notification.NotificationEntity();
        notification.setId(UUID.randomUUID()); notification.setRecipient(student); notification.setInvitation(invitation);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(notifications.findByInvitationId(invitation.getId())).thenReturn(Optional.of(notification));

        service.declineInvitation(student.getId(), invitation.getId());

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(notification.getReadAt()).isEqualTo(clock.instant());
        verifyNoInteractions(memberships);
    }

    @Test
    void archivedClassRejectsInvitationAcceptance() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        classroom.setStatus(ClassroomStatus.ARCHIVED);
        var invitation = invitation(classroom, student, clock.instant().plusSeconds(3600));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvitation(student.getId(), invitation.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("CLASS_ARCHIVED");
        verifyNoInteractions(memberships);
    }

    @Test
    void removingStudentRevokesOnlyTheirStillValidInvitations() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        var membership = new ClassroomMembershipEntity(); membership.setId(UUID.randomUUID()); membership.setClassroom(classroom);
        membership.setStudent(student); membership.setStatus(MembershipStatus.ACTIVE);
        var pending = invitation(classroom, student, clock.instant().plusSeconds(3600));
        var expired = invitation(classroom, student, clock.instant().minusSeconds(1));
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.of(membership));
        when(invitations.findByClassroomIdAndStudentIdAndStatus(classroom.getId(), student.getId(), InvitationStatus.PENDING))
                .thenReturn(List.of(pending, expired));

        service.removeStudent(owner.getId(), classroom.getId(), student.getId());

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        assertThat(pending.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(expired.getStatus()).isEqualTo(InvitationStatus.PENDING);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(invitations.findById(pending.getId())).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.acceptInvitation(student.getId(), pending.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("INVITATION_UNAVAILABLE");
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    void leavingClassRevokesPreviousPendingInvitationsBeforeRejoin() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        var membership = new ClassroomMembershipEntity(); membership.setId(UUID.randomUUID()); membership.setClassroom(classroom);
        membership.setStudent(student); membership.setStatus(MembershipStatus.ACTIVE);
        var pending = invitation(classroom, student, clock.instant().plusSeconds(3600));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.of(membership));
        when(invitations.findByClassroomIdAndStudentIdAndStatus(classroom.getId(), student.getId(), InvitationStatus.PENDING))
                .thenReturn(List.of(pending));

        service.leave(student.getId(), classroom.getId());

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.LEFT);
        assertThat(pending.getStatus()).isEqualTo(InvitationStatus.REVOKED);
    }

    @Test
    void ownerCanListSentInvitationsButOtherTeacherCannot() {
        var owner = account(AccountRole.TEACHER); var other = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT);
        var classroom = classroom(owner); var invitation = invitation(classroom, student, clock.instant().plusSeconds(3600));
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(invitations.findByClassroomId(eq(classroom.getId()), any())).thenReturn(new PageImpl<>(List.of(invitation)));
        var page = service.sentInvitations(owner.getId(), classroom.getId(), PageRequest.of(0, 20));
        assertThat(page.getData().getItems()).hasSize(1);
        assertThat(page.getData().getItems().get(0).studentEmail()).isEqualTo(student.getEmail());
        when(accounts.findById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.sentInvitations(other.getId(), classroom.getId(), PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getHttpStatus().value()).isEqualTo(403);
    }

    @Test
    void studentSeesOwnExpiredAndRevokedInvitationNotifications() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner);
        var expired = invitation(classroom, student, clock.instant().minusSeconds(1));
        var revoked = invitation(classroom, student, clock.instant().plusSeconds(3600)); revoked.setStatus(InvitationStatus.REVOKED);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(invitations.findByStudentId(eq(student.getId()), any())).thenReturn(new PageImpl<>(List.of(expired, revoked)));
        var page = service.myInvitations(student.getId(), PageRequest.of(0, 20));
        assertThat(page.getData().getItems()).extracting(ClassroomDtos.InvitationView::status).containsExactly("EXPIRED", "REVOKED");
    }

    @Test
    void teacherCreatesPrivateActiveClassWithGeneratedCode() {
        var teacher = account(AccountRole.TEACHER); when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        when(classrooms.existsByClassCode(any())).thenReturn(false); when(classrooms.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.create(teacher.getId(), new ClassroomDtos.Create(" Algorithms ", "desc", JoinMode.AUTO_JOIN, null, null));
        assertThat(result.name()).isEqualTo("Algorithms"); assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.ownerId()).isEqualTo(teacher.getId()); assertThat(result.classCode()).hasSize(8);
    }

    @Test
    void nonOwnerCannotChangeJoiningConfiguration() {
        var owner = account(AccountRole.TEACHER); var other = account(AccountRole.TEACHER); var classroom = classroom(owner);
        when(accounts.findById(other.getId())).thenReturn(Optional.of(other));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        assertThatThrownBy(() -> service.configureCode(other.getId(), classroom.getId(), new ClassroomDtos.CodeConfig(null, false, true, JoinMode.AUTO_JOIN)))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getHttpStatus().value()).isEqualTo(403);
    }

    @Test
    void lockedCodeBlocksStudentJoinBeforeMembershipMutation() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner); classroom.setCodeLocked(true);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student)); when(classrooms.findByClassCode("ABCDEFGH")).thenReturn(Optional.of(classroom));
        assertThatThrownBy(() -> service.join(student.getId(), new ClassroomDtos.JoinByCode("abcdefgh")))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("CLASS_CODE_LOCKED");
        verifyNoInteractions(memberships);
    }

    @Test
    void autoJoinActivatesExistingFormerMembershipWithoutCreatingAnotherHistoryRecord() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner);
        var former = new ClassroomMembershipEntity(); former.setId(UUID.randomUUID()); former.setStatus(MembershipStatus.LEFT);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student)); when(classrooms.findByClassCode("ABCDEFGH")).thenReturn(Optional.of(classroom));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.of(former));
        var result = service.join(student.getId(), new ClassroomDtos.JoinByCode("ABCDEFGH"));
        assertThat(result.outcome()).isEqualTo("JOINED"); assertThat(former.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(former.getEndedAt()).isNull(); verify(memberships, never()).save(any());
    }

    @Test
    void requireApprovalCreatesPendingRequestInsteadOfMembership() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner); classroom.setJoinMode(JoinMode.REQUIRE_APPROVAL);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student)); when(classrooms.findByClassCode("ABCDEFGH")).thenReturn(Optional.of(classroom));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.empty());
        when(requests.findByClassroomIdAndStudentIdAndStatus(classroom.getId(), student.getId(), JoinRequestStatus.PENDING_APPROVAL)).thenReturn(Optional.empty());
        var result = service.join(student.getId(), new ClassroomDtos.JoinByCode("ABCDEFGH"));
        assertThat(result.outcome()).isEqualTo("PENDING_APPROVAL"); assertThat(result.requestId()).isNotNull();
        verify(requests).save(any(ClassroomJoinRequestEntity.class)); verify(memberships, never()).save(any());
    }

    @Test
    void regenerateChangesCodeWithoutTouchingMemberships() {
        var teacher = account(AccountRole.TEACHER); var classroom = classroom(teacher);
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom)); when(classrooms.existsByClassCode(any())).thenReturn(false);
        var updated = service.regenerateCode(teacher.getId(), classroom.getId());
        assertThat(updated.classCode()).hasSize(8).isNotEqualTo("ABCDEFGH"); verifyNoInteractions(memberships);
    }

    @Test
    void deleteRejectsClassWithRetainedMembershipHistory() {
        var teacher = account(AccountRole.TEACHER); var classroom = classroom(teacher);
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher)); when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberships.existsByClassroomId(classroom.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.delete(teacher.getId(), classroom.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("CLASS_HAS_RETAINED_DATA");
        verify(classrooms, never()).delete(any());
    }

    @Test
    void deleteRejectsClassWithPracticesInsteadOfFailingOnTheForeignKey() {
        var teacher = account(AccountRole.TEACHER); var classroom = classroom(teacher);
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher)); when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(practices.existsByClassroomId(classroom.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.delete(teacher.getId(), classroom.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("CLASS_HAS_RETAINED_DATA");
        verify(classrooms, never()).delete(any());
    }

    @Test
    void closedClassRejectsJoinEvenWhenCodeIsOtherwiseValid() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner); classroom.setJoiningOpen(false);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student)); when(classrooms.findByClassCode("ABCDEFGH")).thenReturn(Optional.of(classroom));
        assertThatThrownBy(() -> service.join(student.getId(), new ClassroomDtos.JoinByCode("ABCDEFGH")))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("JOINING_CLOSED");
    }

    @Test
    void ownerApprovalActivatesStudentMembershipAndCompletesRequest() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        var request = new ClassroomJoinRequestEntity(); request.setId(UUID.randomUUID()); request.setClassroom(classroom); request.setStudent(student); request.setStatus(JoinRequestStatus.PENDING_APPROVAL);
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner)); when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom)); when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.empty());
        service.approve(owner.getId(), classroom.getId(), request.getId());
        assertThat(request.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
        verify(memberships).save(any(ClassroomMembershipEntity.class));
    }

    @Test
    void closedClassRejectsApprovalOfPendingRequest() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        classroom.setJoiningOpen(false);
        var request = new ClassroomJoinRequestEntity(); request.setId(UUID.randomUUID()); request.setClassroom(classroom);
        request.setStudent(student); request.setStatus(JoinRequestStatus.PENDING_APPROVAL);
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThatThrownBy(() -> service.approve(owner.getId(), classroom.getId(), request.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("JOINING_CLOSED");
        verifyNoInteractions(memberships);
    }

    @Test
    void directAddApprovesExistingPendingRequest() {
        var owner = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT); var classroom = classroom(owner);
        var request = new ClassroomJoinRequestEntity(); request.setId(UUID.randomUUID()); request.setClassroom(classroom);
        request.setStudent(student); request.setStatus(JoinRequestStatus.PENDING_APPROVAL);
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(accounts.findForIdentityResolution(student.getUsername())).thenReturn(List.of(student));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.empty());
        when(requests.findByClassroomIdAndStudentIdAndStatus(classroom.getId(), student.getId(), JoinRequestStatus.PENDING_APPROVAL))
                .thenReturn(Optional.of(request));

        service.directAdd(owner.getId(), classroom.getId(), new ClassroomDtos.DirectAdd(student.getUsername()));

        assertThat(request.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
        verify(memberships).save(any(ClassroomMembershipEntity.class));
    }

    @Test
    void expiredInvitationCannotCreateMembership() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner);
        var invitation = new ClassroomInvitationEntity(); invitation.setId(UUID.randomUUID()); invitation.setClassroom(classroom); invitation.setStudent(student);
        invitation.setStatus(InvitationStatus.PENDING); invitation.setExpiresAt(clock.instant().minusSeconds(1));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student)); when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        assertThatThrownBy(() -> service.acceptInvitation(student.getId(), invitation.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("INVITATION_UNAVAILABLE");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING); verifyNoInteractions(memberships);
    }

    @Test
    void adminTransfersOwnershipAtomicallyToActiveTeacher() {
        var admin = account(AccountRole.ADMIN); var oldOwner = account(AccountRole.TEACHER); var newOwner = account(AccountRole.TEACHER); var classroom = classroom(oldOwner);
        when(accounts.findById(admin.getId())).thenReturn(Optional.of(admin)); when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(accounts.findById(newOwner.getId())).thenReturn(Optional.of(newOwner));
        service.transferOwner(admin.getId(), classroom.getId(), newOwner.getId());
        assertThat(classroom.getOwner()).isSameAs(newOwner);
    }

    @Test
    void inactiveOwnerCannotManageClassroom() {
        var teacher = account(AccountRole.TEACHER); teacher.setStatus(AccountStatus.SUSPENDED);
        var classroom = classroom(teacher);
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        assertThatThrownBy(() -> service.configureCode(teacher.getId(), classroom.getId(), new ClassroomDtos.CodeConfig(null, false, true, JoinMode.AUTO_JOIN)))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getHttpStatus().value()).isEqualTo(403);
        verifyNoInteractions(classrooms);
    }

    @Test
    void inactiveOwnerMakesClassReadOnlyUntilRestored() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner);
        owner.setStatus(AccountStatus.SUSPENDED);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(classrooms.findByClassCode("ABCDEFGH")).thenReturn(Optional.of(classroom));
        assertThatThrownBy(() -> service.join(student.getId(), new ClassroomDtos.JoinByCode("ABCDEFGH")))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("CLASS_READ_ONLY");
        verifyNoInteractions(memberships, requests);

        owner.setStatus(AccountStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.empty());
        assertThat(service.join(student.getId(), new ClassroomDtos.JoinByCode("ABCDEFGH")).outcome()).isEqualTo("JOINED");
        verify(memberships).save(any(ClassroomMembershipEntity.class));
    }

    @Test
    void ownerCanRejectPendingJoinRequestWithoutCreatingMembership() {
        var owner = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = classroom(owner);
        var request = new ClassroomJoinRequestEntity();
        request.setId(UUID.randomUUID());
        request.setClassroom(classroom);
        request.setStudent(student);
        request.setStatus(JoinRequestStatus.PENDING_APPROVAL);
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        service.reject(owner.getId(), classroom.getId(), request.getId());

        assertThat(request.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
        verify(pairLock).lock(classroom.getId(), student.getId());
        verifyNoInteractions(memberships);
    }

    @Test
    void nonOwnerCannotRejectJoinRequest() {
        var owner = account(AccountRole.TEACHER);
        var intruder = account(AccountRole.TEACHER);
        var classroom = classroom(owner);
        when(accounts.findById(intruder.getId())).thenReturn(Optional.of(intruder));

        assertThatThrownBy(() -> service.reject(intruder.getId(), classroom.getId(), UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus().value())
                .isEqualTo(403);
        verifyNoInteractions(requests, memberships);
    }

    @Test
    void rejectingNonPendingJoinRequestThrowsNotFound() {
        var owner = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = classroom(owner);
        var request = new ClassroomJoinRequestEntity();
        request.setId(UUID.randomUUID());
        request.setClassroom(classroom);
        request.setStudent(student);
        request.setStatus(JoinRequestStatus.APPROVED);
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reject(owner.getId(), classroom.getId(), request.getId()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo("JOIN_REQUEST_NOT_FOUND");
        verifyNoInteractions(memberships);
    }

    private AccountEntity account(AccountRole role) { var a = new AccountEntity(); a.setId(UUID.randomUUID()); a.setRole(role); a.setStatus(AccountStatus.ACTIVE); a.setUsername(UUID.randomUUID().toString()); a.setEmail(UUID.randomUUID()+"@example.test"); return a; }
    private ClassroomEntity classroom(AccountEntity owner) { var c=new ClassroomEntity(); c.setId(UUID.randomUUID()); c.setOwner(owner); c.setName("A"); c.setStatus(ClassroomStatus.ACTIVE); c.setClassCode("ABCDEFGH"); c.setJoiningOpen(true); c.setJoinMode(JoinMode.AUTO_JOIN); when(classrooms.findById(c.getId())).thenReturn(Optional.of(c)); return c; }
    private ClassroomInvitationEntity invitation(ClassroomEntity classroom, AccountEntity student, Instant expiry) {
        var invitation = new ClassroomInvitationEntity(); invitation.setId(UUID.randomUUID()); invitation.setClassroom(classroom);
        invitation.setStudent(student); invitation.setStatus(InvitationStatus.PENDING); invitation.setExpiresAt(expiry);
        return invitation;
    }
}
