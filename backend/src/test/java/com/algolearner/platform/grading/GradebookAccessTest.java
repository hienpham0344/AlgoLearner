package com.algolearner.platform.grading;

import com.algolearner.platform.assignment.AssignmentLessonProgressProvider;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.security.AccountAccess;
import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.quiz.QuizLessonProgressProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GradebookAccessTest {
    @Mock AccountAccess accounts;
    @Mock ClassroomService classrooms;
    @Mock ClassroomMembershipRepository memberships;
    @Mock AssignmentLessonProgressProvider assignments;
    @Mock QuizLessonProgressProvider quizzes;
    @InjectMocks GradebookAccess access;

    private final UUID actor = UUID.randomUUID();
    private final UUID classroomId = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken(actor.toString(), "", List.of());

    @Test
    void studentCanReadOnlyOwnActiveMembership() {
        when(accounts.activeRole(actor)).thenReturn("STUDENT");
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroomId, actor)).thenReturn(Optional.of(membership));

        assertThat(access.requireStudentRead(principal, classroomId, actor)).isEqualTo(actor);
        assertThatThrownBy(() -> access.requireStudentRead(principal, classroomId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        membership.setStatus(MembershipStatus.LEFT);
        assertThatThrownBy(() -> access.requireStudentRead(principal, classroomId, actor))
                .isInstanceOf(ApiException.class);
        membership.setStatus(MembershipStatus.REMOVED);
        assertThatThrownBy(() -> access.requireStudentRead(principal, classroomId, actor))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void roleComesFromAccountAndMissingPrincipalIsRejected() {
        assertThatThrownBy(() -> access.requireTeacherWrite(null, classroomId)).isInstanceOf(ApiException.class);
        when(accounts.activeRole(actor)).thenReturn("ADMIN");
        assertThatThrownBy(() -> access.requireTeacherWrite(principal, classroomId)).isInstanceOf(ApiException.class);
        verifyNoInteractions(classrooms);
    }

    @Test
    void writeRequiresCurrentOwnerAndActiveClassroomAfterLock() {
        when(accounts.activeRole(actor)).thenReturn("TEACHER");
        var owner = new AccountEntity();
        owner.setId(actor);
        var classroom = new ClassroomEntity();
        classroom.setOwner(owner);
        classroom.setStatus(ClassroomStatus.ACTIVE);
        when(classrooms.lockWritable(classroomId)).thenReturn(classroom);

        assertThat(access.requireTeacherWrite(principal, classroomId)).isEqualTo(actor);
        classroom.setStatus(ClassroomStatus.ARCHIVED);
        assertThatThrownBy(() -> access.requireTeacherWrite(principal, classroomId)).isInstanceOf(ApiException.class);
        classroom.setStatus(ClassroomStatus.ACTIVE);
        owner.setId(UUID.randomUUID());
        assertThatThrownBy(() -> access.requireTeacherWrite(principal, classroomId)).isInstanceOf(ApiException.class);
    }

    @Test
    void itemLookupUsesSourceTypeAndClassroom() {
        UUID item = UUID.randomUUID();
        when(assignments.existsInClassroom(item, classroomId)).thenReturn(true);
        access.requireItemInClass(classroomId, SourceType.ASSIGNMENT, item);
        assertThatThrownBy(() -> access.requireItemInClass(classroomId, SourceType.QUIZ, item))
                .isInstanceOf(ApiException.class);
        verify(quizzes).existsInClassroom(item, classroomId);
    }
}
