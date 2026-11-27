package com.algolearner.platform.judge;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import static com.algolearner.platform.judge.JudgeTypes.JudgeContextType.PUBLIC_PROBLEM;

@Component
@Profile("!mock")
public class JudgeAccessPolicy {
    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;

    public JudgeAccessPolicy(ClassroomRepository classrooms, ClassroomMembershipRepository memberships) {
        this.classrooms = classrooms;
        this.memberships = memberships;
    }

    public void requireExecute(AccountEntity actor, JudgeProblemEntity problem) {
        if (!problem.isActive()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "PROBLEM_INACTIVE", "Problem is not active");
        if (actor.getRole() == AccountRole.ADMIN) {
            throw error(HttpStatus.FORBIDDEN, "ADMIN_CONTEXT_FORBIDDEN", "Admin accounts cannot submit in learner contexts");
        }
        if (problem.getContextType() == PUBLIC_PROBLEM) return;
        var classroom = problem.getClassroomId() == null ? null : classrooms.findById(problem.getClassroomId()).orElse(null);
        if (actor.getRole() == AccountRole.TEACHER && classroom != null
                && classroom.getOwner().getId().equals(actor.getId())) return;
        if (actor.getRole() == AccountRole.STUDENT && memberships
                .findByClassroomIdAndStudentId(problem.getClassroomId(), actor.getId())
                .map(membership -> membership.getStatus() == MembershipStatus.ACTIVE).orElse(false)) return;
        throw error(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", "Problem is outside the account's classroom scope");
    }

    public void requireManage(AccountEntity actor, JudgeProblemEntity problem) {
        if (problem.getContextType() == PUBLIC_PROBLEM) {
            if (actor.getRole() == AccountRole.ADMIN) return;
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", "Only an admin may manage public Judge problems");
        }
        boolean ownsClassroom = actor.getRole() == AccountRole.TEACHER && problem.getClassroomId() != null
                && classrooms.findById(problem.getClassroomId())
                .map(classroom -> classroom.getOwner().getId().equals(actor.getId())).orElse(false);
        if (!ownsClassroom) throw error(HttpStatus.FORBIDDEN, "NOT_CLASS_OWNER", "Teacher does not own this classroom");
    }

    private static AppException error(HttpStatus status, String code, String message) {
        return new JudgeDomainException(status, code, message);
    }

    private static final class JudgeDomainException extends AppException {
        private JudgeDomainException(HttpStatus status, String code, String message) { super(message, status, code); }
    }
}
