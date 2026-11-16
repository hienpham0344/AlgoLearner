package com.algolearner.platform.judge;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.JudgeContextType.CLASS_ASSIGNMENT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JudgeAccessPolicyTest {
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final JudgeAccessPolicy policy = new JudgeAccessPolicy(classrooms, memberships);

    @Test
    void ownershipTransferImmediatelyGivesNewOwnerManageAccess() {
        AccountEntity oldCreator = account(AccountRole.TEACHER);
        AccountEntity newOwner = account(AccountRole.TEACHER);
        JudgeProblemEntity problem = classProblem(oldCreator);
        ClassroomEntity classroom = classroom(problem.getClassroomId(), newOwner);
        when(classrooms.findById(problem.getClassroomId())).thenReturn(Optional.of(classroom));

        policy.requireManage(newOwner, problem);
        policy.requireExecute(newOwner, problem);
    }

    @Test
    void ownershipTransferRevokesOldProblemCreatorAccess() {
        AccountEntity oldCreator = account(AccountRole.TEACHER);
        AccountEntity newOwner = account(AccountRole.TEACHER);
        JudgeProblemEntity problem = classProblem(oldCreator);
        when(classrooms.findById(problem.getClassroomId()))
                .thenReturn(Optional.of(classroom(problem.getClassroomId(), newOwner)));

        assertThatThrownBy(() -> policy.requireManage(oldCreator, problem))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode()).isEqualTo("NOT_CLASS_OWNER");
        assertThatThrownBy(() -> policy.requireExecute(oldCreator, problem)).isInstanceOf(AppException.class);
    }

    private static JudgeProblemEntity classProblem(AccountEntity creator) {
        JudgeProblemEntity problem = new JudgeProblemEntity();
        problem.setOwner(creator);
        problem.setContextType(CLASS_ASSIGNMENT);
        problem.setContextId(UUID.randomUUID());
        problem.setClassroomId(UUID.randomUUID());
        problem.setActive(true);
        return problem;
    }

    private static ClassroomEntity classroom(UUID id, AccountEntity owner) {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(id);
        classroom.setOwner(owner);
        return classroom;
    }

    private static AccountEntity account(AccountRole role) {
        UUID id = UUID.randomUUID();
        return AccountEntity.builder().id(id).username(id.toString()).email(id + "@test.local")
                .role(role).status(AccountStatus.ACTIVE).build();
    }
}
