package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import org.junit.jupiter.api.Test;
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
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final ClassroomService service = new ClassroomService(classrooms, memberships, requests, invitations, accounts, clock);

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
    void expiredInvitationCannotCreateMembership() {
        var student = account(AccountRole.STUDENT); var owner = account(AccountRole.TEACHER); var classroom = classroom(owner);
        var invitation = new ClassroomInvitationEntity(); invitation.setId(UUID.randomUUID()); invitation.setClassroom(classroom); invitation.setStudent(student);
        invitation.setStatus(InvitationStatus.PENDING); invitation.setExpiresAt(clock.instant().minusSeconds(1));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student)); when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        assertThatThrownBy(() -> service.acceptInvitation(student.getId(), invitation.getId()))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException) e).getErrorCode()).isEqualTo("INVITATION_UNAVAILABLE");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED); verifyNoInteractions(memberships);
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

    private AccountEntity account(AccountRole role) { var a = new AccountEntity(); a.setId(UUID.randomUUID()); a.setRole(role); a.setStatus(AccountStatus.ACTIVE); a.setUsername(UUID.randomUUID().toString()); a.setEmail(UUID.randomUUID()+"@example.test"); return a; }
    private ClassroomEntity classroom(AccountEntity owner) { var c=new ClassroomEntity(); c.setId(UUID.randomUUID()); c.setOwner(owner); c.setName("A"); c.setStatus(ClassroomStatus.ACTIVE); c.setClassCode("ABCDEFGH"); c.setJoiningOpen(true); c.setJoinMode(JoinMode.AUTO_JOIN); return c; }
}
