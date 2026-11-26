package com.algolearner.platform.course;

import com.algolearner.platform.common.security.AccountAccess;
import com.algolearner.platform.course.PublicCourseRepository.CourseRow;
import com.algolearner.platform.course.PublicCourseRepository.EnrollmentRow;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Role and publication-policy checks shared by learner, contribution and admin flows (AC-04, FR-PCRS-013). */
@Component
@Profile("!mock")
@RequiredArgsConstructor
public class CourseAccessPolicy {
    public enum Access { FULL, PREVIEW, LOCKED, NONE }

    private final AccountAccess accounts;
    private final PublicCourseRepository courses;

    /** Locked active enrollment with full content access. Not transactional itself: it must not mark the caller's transaction. */
    public EnrollmentRow learningEnrollment(UUID actor, CourseRow course) {
        EnrollmentRow enrollment = courses.enrollment(actor, course.id(), true).filter(EnrollmentRow::active).orElse(null);
        Access access = access(course, enrollment);
        if (access == Access.NONE) throw CourseException.notFound();
        if (access == Access.LOCKED) throw CourseException.locked();
        if (access == Access.PREVIEW) throw CourseException.notEnrolled();
        return enrollment;
    }

    /** Learner content access for a course and the caller's active enrollment (may be null). */
    public static Access access(CourseRow course, EnrollmentRow enrollment) {
        boolean enrolled = enrollment != null && enrollment.active();
        return switch (course.status()) {
            case PUBLISHED -> enrolled ? Access.FULL : Access.PREVIEW;
            case UNPUBLISHED -> !enrolled ? Access.NONE
                    : course.unpublishPolicy() == CourseDtos.UnpublishPolicy.LOCK_ALL ? Access.LOCKED : Access.FULL;
            case ARCHIVED -> enrolled ? Access.FULL : Access.NONE;
            case DRAFT -> Access.NONE;
        };
    }

    public String learner(UUID actor) {
        String role = accounts.activeRole(actor);
        if (!Set.of("STUDENT", "TEACHER").contains(role)) throw CourseException.forbidden("Learning access denied");
        return role;
    }

    public void teacher(UUID actor) { require(actor, "TEACHER"); }

    public void admin(UUID actor) { require(actor, "ADMIN"); }

    private void require(UUID actor, String role) {
        if (!accounts.activeRole(actor).equals(role)) throw CourseException.forbidden("Course access denied");
    }

    public static void pageSize(Pageable pageable) {
        if (pageable.getPageSize() > 100) throw CourseException.invalid("Page size exceeds limit");
    }
}
