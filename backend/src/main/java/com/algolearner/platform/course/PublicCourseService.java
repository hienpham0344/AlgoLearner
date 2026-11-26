package com.algolearner.platform.course;

import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.course.CourseAccessPolicy.Access;
import com.algolearner.platform.course.CourseDtos.CatalogItem;
import com.algolearner.platform.course.CourseDtos.Detail;
import com.algolearner.platform.course.CourseDtos.EnrollmentHistory;
import com.algolearner.platform.course.CourseDtos.Lesson;
import com.algolearner.platform.course.CourseDtos.LessonOutline;
import com.algolearner.platform.course.CourseDtos.Level;
import com.algolearner.platform.course.CourseDtos.Progress;
import com.algolearner.platform.course.CourseDtos.Publication;
import com.algolearner.platform.course.CourseDtos.QuizOutline;
import com.algolearner.platform.course.PublicCourseRepository.CatalogFilter;
import com.algolearner.platform.course.PublicCourseRepository.CourseRow;
import com.algolearner.platform.course.PublicCourseRepository.EnrollmentRow;
import com.algolearner.platform.course.PublicCourseRepository.LessonRow;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Learner side of SRS 4.11: catalog, preview, enrollment, lessons and own progress (FR-PCRS-001..006, 009). */
@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCourseService {
    private final PublicCourseRepository courses;
    private final CourseAccessPolicy policy;
    private final CourseProgressService progress;
    private final Clock clock;

    public PageResponse<CatalogItem> catalog(UUID actor, String query, Level level, String topic, Pageable pageable) {
        policy.learner(actor);
        CourseAccessPolicy.pageSize(pageable);
        var filter = new CatalogFilter(CourseContentRules.blankToNull(query), level, CourseContentRules.blankToNull(topic),
                Set.of(Publication.PUBLISHED), null);
        if (filter.query() != null && filter.query().length() > 255) throw CourseException.invalid("Search text is too long");
        List<CourseRow> rows = courses.courses(filter, pageable.getPageNumber(), pageable.getPageSize());
        var statuses = courses.enrollmentStatuses(actor, rows.stream().map(CourseRow::id).toList());
        var items = rows.stream().map(row -> new CatalogItem(row.summary(), statuses.get(row.id()))).toList();
        return PageResponse.fromPage(new PageImpl<>(items, pageable, courses.countCourses(filter)));
    }

    public Detail detail(UUID actor, UUID courseId) {
        String role = policy.learner(actor);
        CourseRow course = courses.course(courseId, false).orElseThrow(CourseException::notFound);
        EnrollmentRow enrollment = activeEnrollment(actor, courseId);
        Access access = CourseAccessPolicy.access(course, enrollment);
        boolean author = role.equals("TEACHER") && course.authorId().equals(actor);
        if (access == Access.NONE && !author) throw CourseException.notFound();
        Set<UUID> doneLessons = enrollment == null ? Set.of() : courses.completedLessonIds(enrollment.id());
        Set<UUID> doneQuizzes = enrollment == null ? Set.of() : courses.completedQuizIds(enrollment.id());
        var lessons = courses.lessons(courseId).stream().map(l -> new LessonOutline(l.id(), l.title(), l.displayOrder(),
                l.estimatedMinutes(), l.prerequisiteNote(), l.preview(), doneLessons.contains(l.id()))).toList();
        var quizzes = courses.quizzes(courseId).stream().map(q -> outline(q, doneQuizzes.contains(q.id()))).toList();
        Progress own = enrollment == null ? null : progress.view(course, enrollment);
        return new Detail(course.summary(), lessons, quizzes, own, access == Access.FULL);
    }

    @Transactional
    public Lesson lesson(UUID actor, UUID courseId, UUID lessonId) {
        policy.learner(actor);
        CourseRow course = courses.course(courseId, false).orElseThrow(CourseException::notFound);
        EnrollmentRow enrollment = courses.enrollment(actor, courseId, true).filter(EnrollmentRow::active).orElse(null);
        Access access = CourseAccessPolicy.access(course, enrollment);
        if (access == Access.NONE) throw CourseException.notFound();
        if (access == Access.LOCKED) throw CourseException.locked();
        LessonRow lesson = courses.lesson(courseId, lessonId)
                .orElseThrow(() -> CourseException.notFound("COURSE_UNIT_NOT_FOUND", "Course lesson not found"));
        if (access == Access.PREVIEW && !lesson.preview()) throw CourseException.notEnrolled();
        boolean completed = false;
        if (enrollment != null) {
            if (enrollment.startedAt() == null) progress.recompute(course, enrollment, true, false, now());
            completed = courses.completedLessonIds(enrollment.id()).contains(lessonId);
        }
        return new Lesson(lesson.id(), courseId, lesson.title(), lesson.displayOrder(), lesson.estimatedMinutes(),
                lesson.prerequisiteNote(), lesson.preview(), lesson.blocks(), completed);
    }

    @Transactional
    public Progress enroll(UUID actor, UUID courseId) {
        policy.learner(actor);
        CourseRow course = courses.course(courseId, true).orElseThrow(CourseException::notFound);
        var existing = courses.enrollment(actor, courseId, true);
        if (existing.isPresent() && existing.get().active()) return progress.view(course, existing.get());
        if (course.status() == Publication.DRAFT) throw CourseException.notFound();
        if (course.status() != Publication.PUBLISHED) {
            throw CourseException.conflict("COURSE_NOT_ENROLLABLE", "Course is not open for new enrollment");
        }
        Instant now = now();
        UUID enrollmentId;
        if (existing.isPresent()) {
            enrollmentId = existing.get().id();
            courses.setUnenrolled(enrollmentId, null, now);
        } else {
            enrollmentId = courses.insertEnrollment(actor, courseId, now);
        }
        courses.event(enrollmentId, "ENROLLED", null, now);
        return progress.view(course, courses.enrollment(actor, courseId, false).orElseThrow());
    }

    @Transactional
    public void unenroll(UUID actor, UUID courseId) {
        policy.learner(actor);
        courses.course(courseId, false).orElseThrow(CourseException::notFound);
        EnrollmentRow enrollment = courses.enrollment(actor, courseId, true).filter(EnrollmentRow::active)
                .orElseThrow(() -> CourseException.notFound("COURSE_ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        if (enrollment.startedAt() != null || courses.hasLearningActivity(enrollment.id())) {
            throw CourseException.conflict("COURSE_ALREADY_STARTED", "A started course cannot be unenrolled");
        }
        Instant now = now();
        courses.setUnenrolled(enrollment.id(), now, now);
        courses.event(enrollment.id(), "UNENROLLED", null, now);
    }

    @Transactional
    public Progress completeLesson(UUID actor, UUID courseId, UUID lessonId) {
        policy.learner(actor);
        CourseRow course = courses.course(courseId, false).orElseThrow(CourseException::notFound);
        EnrollmentRow enrollment = policy.learningEnrollment(actor, course);
        courses.lesson(courseId, lessonId).orElseThrow(() -> CourseException.notFound("COURSE_UNIT_NOT_FOUND", "Course lesson not found"));
        Instant now = now();
        if (courses.completeLesson(enrollment.id(), lessonId, now)) {
            courses.event(enrollment.id(), "LESSON_COMPLETED", lessonId.toString(), now);
        }
        return progress.recompute(course, enrollment, true, false, now);
    }

    public Progress progress(UUID actor, UUID courseId) {
        policy.learner(actor);
        CourseRow course = courses.course(courseId, false).orElseThrow(CourseException::notFound);
        EnrollmentRow enrollment = activeEnrollment(actor, courseId);
        if (enrollment == null) throw CourseException.notFound("COURSE_ENROLLMENT_NOT_FOUND", "Enrollment not found");
        return progress.view(course, enrollment);
    }

    @Transactional
    public Progress acknowledgeUpdate(UUID actor, UUID courseId) {
        policy.learner(actor);
        CourseRow course = courses.course(courseId, false).orElseThrow(CourseException::notFound);
        EnrollmentRow e = courses.enrollment(actor, courseId, true).filter(EnrollmentRow::active)
                .orElseThrow(() -> CourseException.notFound("COURSE_ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        Instant now = now();
        courses.updateProgress(e.id(), e.status(), e.percent(), e.startedAt(), e.completedAt(), false, now);
        return progress.view(course, courses.enrollment(actor, courseId, false).orElseThrow());
    }

    public PageResponse<EnrollmentHistory> myEnrollments(UUID actor, Pageable pageable) {
        policy.learner(actor);
        CourseAccessPolicy.pageSize(pageable);
        var items = courses.enrollmentsOf(actor, pageable.getPageNumber(), pageable.getPageSize()).stream().map(e -> {
            CourseRow course = courses.course(e.courseId(), false).orElseThrow();
            return new EnrollmentHistory(course.summary(), progress.view(course, e));
        }).toList();
        return PageResponse.fromPage(new PageImpl<>(items, pageable, courses.countEnrollmentsOf(actor)));
    }

    static QuizOutline outline(PublicCourseRepository.QuizRow q, boolean completed) {
        return new QuizOutline(q.id(), q.lessonId(), q.title(), q.mandatory(), q.rule(), q.passingScore(), q.maxAttempts(),
                q.timeLimitSeconds(), q.questions().size(), completed);
    }

    private EnrollmentRow activeEnrollment(UUID actor, UUID course) {
        return courses.enrollment(actor, course, false).filter(EnrollmentRow::active).orElse(null);
    }

    private Instant now() { return clock.instant(); }
}
