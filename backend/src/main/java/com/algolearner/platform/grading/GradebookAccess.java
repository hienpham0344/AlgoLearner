package com.algolearner.platform.grading;

import com.algolearner.platform.assignment.AssignmentLessonProgressProvider;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomService;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.security.AccountAccess;
import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.quiz.QuizLessonProgressProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookAccess {
    private final AccountAccess accounts;
    private final ClassroomService classrooms;
    private final ClassroomMembershipRepository memberships;
    private final AssignmentLessonProgressProvider assignments;
    private final QuizLessonProgressProvider quizzes;

    @Transactional(readOnly = true)
    public UUID requireTeacherRead(Authentication principal, UUID classroomId) {
        UUID actor = requireRole(principal, "TEACHER");
        if (!actor.equals(classrooms.get(actor, classroomId).ownerId())) {
            throw forbidden();
        }
        return actor;
    }

    /** The calling service must hold this transaction through the gradebook write. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID requireTeacherWrite(Authentication principal, UUID classroomId) {
        UUID actor = requireRole(principal, "TEACHER");
        var classroom = classrooms.lockWritable(classroomId);
        if (!actor.equals(classroom.getOwner().getId()) || classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw forbidden();
        }
        return actor;
    }

    /** Quyết định tạm: Student đã rời lớp bị từ chối, chờ nhóm xác nhận có cho xem lại điểm cũ không. */
    @Transactional(readOnly = true)
    public UUID requireStudentRead(Authentication principal, UUID classroomId, UUID studentId) {
        UUID actor = requireRole(principal, "STUDENT");
        if (!actor.equals(studentId)) {
            throw forbidden();
        }
        requireActiveStudentInClass(classroomId, actor);
        return actor;
    }

    // These scope checks are used after authorization of the classroom.
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireActiveStudentInClass(UUID classroomId, UUID studentId) {
        if (memberships.findByClassroomIdAndStudentId(classroomId, studentId)
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE).isEmpty()) {
            throw forbidden();
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireItemInClass(UUID classroomId, SourceType sourceType, UUID sourceId) {
        boolean exists = switch (sourceType) {
            case ASSIGNMENT -> assignments.existsInClassroom(sourceId, classroomId);
            case QUIZ -> quizzes.existsInClassroom(sourceId, classroomId);
        };
        if (!exists) {
            throw forbidden();
        }
    }

    private UUID requireRole(Authentication principal, String role) {
        if (principal == null || !principal.isAuthenticated() || principal instanceof AnonymousAuthenticationToken) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        UUID actor;
        try {
            actor = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        if (!role.equals(accounts.activeRole(actor))) {
            throw forbidden();
        }
        return actor;
    }

    private static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN_OPERATION);
    }
}
