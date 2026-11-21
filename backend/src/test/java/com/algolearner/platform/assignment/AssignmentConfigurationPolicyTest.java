package com.algolearner.platform.assignment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.lesson.LessonLinkedItemRepository;
import com.algolearner.platform.notification.NotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentConfigurationPolicyTest {
    @Test
    void rejectsLatePenaltyBeforePersistence() {
        var teacher = new AccountEntity(); teacher.setId(UUID.randomUUID());
        teacher.setRole(AccountRole.TEACHER); teacher.setStatus(AccountStatus.ACTIVE);
        var classroom = new ClassroomEntity(); classroom.setId(UUID.randomUUID()); classroom.setOwner(teacher);
        var classes = mock(ClassroomRepository.class);
        var accounts = mock(AccountRepository.class);
        var assignments = mock(AssignmentRepository.class);
        when(assignments.save(any())).thenAnswer(call -> call.getArgument(0));
        when(classes.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        var service = new AssignmentService(assignments, mock(AssignmentQuestionRepository.class),
                mock(AssignmentQuestionOptionRepository.class), mock(AssignmentAttemptRepository.class),
                mock(AssignmentAnswerRepository.class), mock(AssignmentRejudgeLogRepository.class), classes,
                mock(ClassroomMembershipRepository.class), accounts, mock(NotificationService.class),
                mock(LessonLinkedItemRepository.class), Clock.systemUTC());
        var request = new AssignmentDtos.CreateAssignmentRequest("Penalty not allowed", null, AssignmentType.GRADED,
                null, Instant.now().plusSeconds(3600), true, BigDecimal.TEN, null, null,
                0, null, null, null, GradePublishMode.MANUAL, null, null);
        assertThrows(AppException.class, () -> service.create(teacher.getId(), classroom.getId(), request));
        verifyNoInteractions(assignments);
    }
    @Test
    void validatesSchedulesAndUnlimitedAttemptsWithoutSideEffects() {
        var start = Instant.parse("2026-11-18T00:00:00Z");
        assertDoesNotThrow(() -> AssignmentConfigurationPolicy.validate(start, start.plusSeconds(60), 1, 0, BigDecimal.ZERO, GradePublishMode.MANUAL));
        assertThrows(AppException.class, () -> AssignmentConfigurationPolicy.validate(start, start, 1, 0, BigDecimal.ZERO, GradePublishMode.MANUAL));
        assertThrows(AppException.class, () -> AssignmentConfigurationPolicy.validate(null, null, 0, 0, BigDecimal.ZERO, GradePublishMode.MANUAL));
        assertThrows(AppException.class, () -> AssignmentConfigurationPolicy.validate(null, null, 1, -1, BigDecimal.ZERO, GradePublishMode.MANUAL));
        assertThrows(AppException.class, () -> AssignmentConfigurationPolicy.validate(null, null, 1, 0, BigDecimal.ZERO, GradePublishMode.AFTER_DEADLINE));
    }

}
