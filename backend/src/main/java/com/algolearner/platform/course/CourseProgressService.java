package com.algolearner.platform.course;

import com.algolearner.platform.course.CourseDtos.Progress;
import com.algolearner.platform.course.CourseDtos.ProgressStatus;
import com.algolearner.platform.course.PublicCourseRepository.CourseRow;
import com.algolearner.platform.course.PublicCourseRepository.EnrollmentRow;
import com.algolearner.platform.course.PublicCourseRepository.QuizRow;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists per-learner progress (FR-PCRS-005/006/007/015). Callers must hold the enrollment row lock. */
@Component
@Profile("!mock")
@RequiredArgsConstructor
public class CourseProgressService {
    private final PublicCourseRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Progress recompute(CourseRow course, EnrollmentRow enrollment, boolean markStarted, boolean contentChanged, Instant now) {
        var state = state(course.id(), enrollment.id());
        boolean started = enrollment.startedAt() != null || markStarted;
        var result = CourseProgressCalculator.evaluate(state.lessons, state.mandatory, state.doneLessons, state.doneQuizzes,
                enrollment.status(), started);
        Instant startedAt = enrollment.startedAt() != null ? enrollment.startedAt()
                : result.status() != ProgressStatus.NOT_STARTED ? now : null;
        Instant completedAt = result.becameCompleted() ? now : enrollment.completedAt();
        boolean hasUpdate = enrollment.hasUpdate() || (contentChanged && enrollment.status() == ProgressStatus.COMPLETED);
        repository.updateProgress(enrollment.id(), result.status(), result.percent(), startedAt, completedAt, hasUpdate, now);
        if (enrollment.startedAt() == null && startedAt != null) repository.event(enrollment.id(), "STARTED", null, now);
        if (result.becameCompleted()) repository.event(enrollment.id(), "COMPLETED", null, now);
        if (contentChanged && enrollment.status() == ProgressStatus.COMPLETED) {
            repository.event(enrollment.id(), "CONTENT_UPDATED", "Completed status preserved", now);
        }
        return view(course, new EnrollmentRow(enrollment.id(), enrollment.courseId(), enrollment.accountId(), result.status(),
                result.percent(), enrollment.enrolledAt(), startedAt, completedAt, hasUpdate, enrollment.unenrolledAt()), state);
    }

    /** Recomputes every active learner after published content changed, within the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recomputeCourse(CourseRow course, boolean contentChanged, Instant now) {
        for (EnrollmentRow enrollment : repository.activeEnrollmentsForUpdate(course.id())) {
            recompute(course, enrollment, false, contentChanged, now);
        }
    }

    public Progress view(CourseRow course, EnrollmentRow enrollment) {
        return view(course, enrollment, state(course.id(), enrollment.id()));
    }

    private Progress view(CourseRow course, EnrollmentRow enrollment, State state) {
        var result = CourseProgressCalculator.evaluate(state.lessons, state.mandatory, state.doneLessons, state.doneQuizzes,
                enrollment.status(), enrollment.startedAt() != null);
        List<UUID> lessons = state.lessonOrder.stream().filter(state.doneLessons::contains).toList();
        List<UUID> quizzes = state.quizOrder.stream().filter(state.doneQuizzes::contains).toList();
        boolean locked = CourseAccessPolicy.access(course, enrollment) == CourseAccessPolicy.Access.LOCKED;
        return new Progress(course.id(), enrollment.accountId(), enrollment.status(), result.completedLessons(),
                state.lessons.size(), result.completedQuizzes(), state.mandatory.size(), result.percent().doubleValue(),
                lessons, quizzes, enrollment.enrolledAt(), enrollment.startedAt(), enrollment.completedAt(),
                enrollment.hasUpdate(), locked);
    }

    private State state(UUID course, UUID enrollment) {
        List<UUID> lessonOrder = repository.lessons(course).stream().map(PublicCourseRepository.LessonRow::id).toList();
        List<QuizRow> quizzes = repository.quizzes(course);
        return new State(lessonOrder, quizzes.stream().map(QuizRow::id).toList(), Set.copyOf(lessonOrder),
                quizzes.stream().filter(QuizRow::mandatory).map(QuizRow::id).collect(Collectors.toSet()),
                repository.completedLessonIds(enrollment), repository.completedQuizIds(enrollment));
    }

    private record State(List<UUID> lessonOrder, List<UUID> quizOrder, Set<UUID> lessons, Set<UUID> mandatory,
                         Set<UUID> doneLessons, Set<UUID> doneQuizzes) { }
}
