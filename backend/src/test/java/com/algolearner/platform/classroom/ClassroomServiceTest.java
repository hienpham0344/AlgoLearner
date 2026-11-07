package com.algolearner.platform.classroom;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.security.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static com.algolearner.platform.classroom.ClassroomDtos.*;
import static com.algolearner.platform.classroom.ClassroomTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClassroomServiceTest {
    final UUID teacher=UUID.randomUUID(), teacher2=UUID.randomUUID(), student=UUID.randomUUID(), admin=UUID.randomUUID();
    final Clock clock=Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"),ZoneOffset.UTC);
    final ClassroomRepository repository=mock(ClassroomRepository.class);
    final AccountRepository accounts=mock(AccountRepository.class);
    final ClassroomNotificationPort notifications=mock(ClassroomNotificationPort.class);
    final ClassroomAuditPort audit=mock(ClassroomAuditPort.class);
    final ClassCodeGenerator codes=mock(ClassCodeGenerator.class);
    final ClassroomService service=new ClassroomService(repository,accounts,notifications,audit,codes,clock);

    @BeforeEach void setup() {
        when(accounts.accounts()).thenReturn(List.of(
                account(teacher,"teacher","teacher@example.test","TEACHER"),
                account(teacher2,"teacher2","teacher2@example.test","TEACHER"),
                account(student,"student","student@example.test","STUDENT"),
                account(admin,"admin","admin@example.test","ADMIN")));
        when(accounts.restrictions()).thenReturn(List.of());
        when(codes.nextCode()).thenReturn("ABCDEFGH");
        when(repository.findByCode(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(x -> x.getArgument(0));
        when(repository.saveMembership(any())).thenAnswer(x -> x.getArgument(0));
        when(repository.saveJoinRequest(any())).thenAnswer(x -> x.getArgument(0));
        when(repository.saveInvitation(any())).thenAnswer(x -> x.getArgument(0));
    }

    @Test void teacherCreatesPrivateClassAndBecomesOwner() {
        var response=service.create(teacher,new CreateClassroomRequest(" Algorithms ",null,null,null,null));
        assertThat(response.ownerAccountId()).isEqualTo(teacher);
        assertThat(response.status()).isEqualTo(ClassroomStatus.ACTIVE);
        assertThat(response.joinMode()).isEqualTo(JoinMode.AUTO_JOIN);
        verify(repository).save(argThat(x -> x.classCode().equals("ABCDEFGH") && x.name().equals("Algorithms")));
    }

    @Test void teacherCannotUpdateAnotherOwnersClass() {
        UUID id=UUID.randomUUID(); when(repository.findById(id)).thenReturn(Optional.of(classroom(id,teacher2,true,JoinMode.AUTO_JOIN)));
        assertThatThrownBy(() -> service.update(teacher,id,new UpdateClassroomRequest("Attack",null)))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("FORBIDDEN_OPERATION");
    }

    @Test void closedClassRejectsJoinWithoutCreatingData() {
        Classroom classroom=classroom(UUID.randomUUID(),teacher,false,JoinMode.AUTO_JOIN);
        when(repository.findByCode("ABCDEFGH")).thenReturn(Optional.of(classroom));
        assertThatThrownBy(() -> service.join(student,new JoinClassRequest("abcdefgh")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("CLASSROOM_JOIN_DENIED");
        verify(repository,never()).saveMembership(any());
    }

    @Test void autoJoinCreatesMembershipWhileApprovalModeCreatesRequest() {
        Classroom automatic=classroom(UUID.randomUUID(),teacher,true,JoinMode.AUTO_JOIN);
        when(repository.findByCode("AUTOJOIN")).thenReturn(Optional.of(automatic));
        assertThat(service.join(student,new JoinClassRequest("autojoin")).result()).isEqualTo("JOINED");
        verify(repository).saveMembership(argThat(x -> x.status()==MembershipStatus.ACTIVE));

        Classroom approval=classroom(UUID.randomUUID(),teacher,true,JoinMode.REQUIRE_APPROVAL);
        when(repository.findByCode("APPROVE1")).thenReturn(Optional.of(approval));
        assertThat(service.join(student,new JoinClassRequest("approve1")).result()).isEqualTo("PENDING_APPROVAL");
        verify(repository).saveJoinRequest(argThat(x -> x.status()==JoinRequestStatus.PENDING));
    }

    @Test void invitationIsOnlyCreatedForRegisteredActiveStudentAndPublishesNotification() {
        UUID id=UUID.randomUUID(); when(repository.findById(id)).thenReturn(Optional.of(classroom(id,teacher,true,JoinMode.AUTO_JOIN)));
        var result=service.invite(teacher,id,new CreateInvitationRequest("student@example.test",clock.instant().plusSeconds(60)));
        assertThat(result.inviteeAccountId()).isEqualTo(student);
        verify(notifications).invitationCreated(eq(result.id()),eq(id),eq(student),eq(clock.instant()));
        assertThatThrownBy(() -> service.invite(teacher,id,new CreateInvitationRequest("missing@example.test",null)))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("VALIDATION_FAILED");
    }

    @Test void hardDeleteIsBlockedWhenPreservableDataExists() {
        UUID id=UUID.randomUUID(); when(repository.findById(id)).thenReturn(Optional.of(classroom(id,teacher,true,JoinMode.AUTO_JOIN)));
        when(repository.hasPreservableData(id)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(teacher,id)).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo("CLASSROOM_CONFLICT");
        verify(repository,never()).delete(id);
    }

    private AccountRepository.Account account(UUID id,String username,String email,String role) {
        return new AccountRepository.Account(id,username,email,role,"ACTIVE",clock.instant());
    }
    private Classroom classroom(UUID id,UUID owner,boolean open,JoinMode mode) {
        return new Classroom(id,"Class",null,owner,ClassroomStatus.ACTIVE,mode,open,"ABCDEFGH",false,null,
                clock.instant(),clock.instant());
    }
}
