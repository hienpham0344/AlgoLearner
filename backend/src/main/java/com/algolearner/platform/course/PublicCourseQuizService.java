package com.algolearner.platform.course;

import com.algolearner.platform.course.CourseDtos.AnswerVisibility;
import com.algolearner.platform.course.CourseDtos.Attempt;
import com.algolearner.platform.course.CourseDtos.AttemptOption;
import com.algolearner.platform.course.CourseDtos.AttemptQuestion;
import com.algolearner.platform.course.CourseDtos.AttemptStatus;
import com.algolearner.platform.course.CourseDtos.QuestionContent;
import com.algolearner.platform.course.CourseDtos.QuizStatus;
import com.algolearner.platform.course.CourseDtos.SaveAnswers;
import com.algolearner.platform.course.PublicCourseRepository.AttemptRow;
import com.algolearner.platform.course.PublicCourseRepository.CourseRow;
import com.algolearner.platform.course.PublicCourseRepository.EnrollmentRow;
import com.algolearner.platform.course.PublicCourseRepository.QuizRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-PCRS-010: auto-graded public quiz attempts. Timers use server time and never reset on reload;
 * expired attempts are finalized with their saved answers. Attempt history is never deleted.
 */
@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional
public class PublicCourseQuizService {
    private final PublicCourseRepository courses;
    private final CourseAccessPolicy policy;
    private final CourseProgressService progress;
    private final Clock clock;

    public QuizStatus status(UUID actor, UUID courseId, UUID quizId) {
        var ctx = context(actor, courseId, quizId);
        List<AttemptRow> attempts = finalizeExpired(ctx, courses.attempts(quizId, ctx.enrollment.id()));
        int used = attempts.size();
        Integer remaining = ctx.quiz.maxAttempts() == null ? null : Math.max(0, ctx.quiz.maxAttempts() - used);
        BigDecimal official = attempts.stream().filter(a -> a.status() == AttemptStatus.SUBMITTED).map(AttemptRow::score)
                .max(Comparator.naturalOrder()).orElse(null);
        UUID open = attempts.stream().filter(a -> a.status() == AttemptStatus.IN_PROGRESS).map(AttemptRow::id).findFirst().orElse(null);
        boolean completed = courses.completedQuizIds(ctx.enrollment.id()).contains(quizId);
        return new QuizStatus(PublicCourseService.outline(ctx.quiz, completed), used, remaining, official, open,
                attempts.stream().map(a -> view(a, false)).toList());
    }

    public Attempt start(UUID actor, UUID courseId, UUID quizId) {
        var ctx = context(actor, courseId, quizId);
        List<AttemptRow> attempts = finalizeExpired(ctx, courses.attempts(quizId, ctx.enrollment.id()));
        var open = attempts.stream().filter(a -> a.status() == AttemptStatus.IN_PROGRESS).findFirst();
        if (open.isPresent()) return view(open.get(), true);
        if (ctx.quiz.maxAttempts() != null && attempts.size() >= ctx.quiz.maxAttempts()) {
            throw CourseException.conflict("QUIZ_ATTEMPTS_EXHAUSTED", "No quiz attempts remain");
        }
        Instant now = now();
        Instant deadline = ctx.quiz.timeLimitSeconds() == null ? null : now.plusSeconds(ctx.quiz.timeLimitSeconds());
        UUID id = courses.insertAttempt(quizId, ctx.enrollment.id(), attempts.size() + 1, ctx.quiz.content(), now, deadline);
        if (ctx.enrollment.startedAt() == null) progress.recompute(ctx.course, ctx.enrollment, true, false, now);
        return view(courses.attempt(id, false).orElseThrow(), true);
    }

    /** The timeout finalization must commit even though the caller receives 409 QUIZ_TIME_EXPIRED. */
    @Transactional(noRollbackFor = CourseException.class)
    public Attempt saveAnswers(UUID actor, UUID courseId, UUID quizId, UUID attemptId, SaveAnswers request) {
        var ctx = context(actor, courseId, quizId);
        AttemptRow attempt = ownAttempt(ctx, attemptId);
        if (attempt.status() != AttemptStatus.IN_PROGRESS) throw CourseException.conflict("QUIZ_ATTEMPT_CLOSED", "Attempt is already submitted");
        if (expired(attempt)) {
            finish(ctx, attempt, attempt.deadlineAt());
            throw CourseException.conflict("QUIZ_TIME_EXPIRED", "Time limit reached; saved answers were submitted");
        }
        courses.saveAnswers(attemptId, merge(attempt, request), now());
        return view(courses.attempt(attemptId, false).orElseThrow(), true);
    }

    public Attempt submit(UUID actor, UUID courseId, UUID quizId, UUID attemptId, SaveAnswers request) {
        var ctx = context(actor, courseId, quizId);
        AttemptRow attempt = ownAttempt(ctx, attemptId);
        if (attempt.status() == AttemptStatus.SUBMITTED) return view(attempt, false);
        if (expired(attempt)) {
            finish(ctx, attempt, attempt.deadlineAt());
        } else {
            if (request != null) {
                courses.saveAnswers(attemptId, merge(attempt, request), now());
                attempt = courses.attempt(attemptId, true).orElseThrow();
            }
            finish(ctx, attempt, now());
        }
        return view(courses.attempt(attemptId, false).orElseThrow(), false);
    }

    private void finish(Context ctx, AttemptRow attempt, Instant submittedAt) {
        var graded = PublicQuizGrader.grade(attempt.quiz().questions(), attempt.answers(), attempt.quiz().completionRule(),
                attempt.quiz().passingScore());
        Instant now = now();
        courses.submitAttempt(attempt.id(), submittedAt, graded.score(), graded.passed(), now);
        courses.event(ctx.enrollment.id(), "QUIZ_SUBMITTED", attempt.quizId() + ":" + graded.score(), now);
        EnrollmentRow fresh = courses.enrollment(ctx.enrollment.accountId(), ctx.course.id(), true).orElseThrow();
        progress.recompute(ctx.course, fresh, true, false, now);
    }

    private List<AttemptRow> finalizeExpired(Context ctx, List<AttemptRow> attempts) {
        boolean changed = false;
        for (AttemptRow attempt : attempts) {
            if (attempt.status() == AttemptStatus.IN_PROGRESS && expired(attempt)) {
                finish(ctx, courses.attempt(attempt.id(), true).orElseThrow(), attempt.deadlineAt());
                changed = true;
            }
        }
        return changed ? courses.attempts(ctx.quiz.id(), ctx.enrollment.id()) : attempts;
    }

    private Map<UUID, Set<UUID>> merge(AttemptRow attempt, SaveAnswers request) {
        Map<UUID, QuestionContent> questions = attempt.quiz().questions().stream()
                .collect(Collectors.toMap(QuestionContent::id, q -> q));
        var merged = new LinkedHashMap<>(attempt.answers());
        for (var answer : request.answers()) {
            QuestionContent question = questions.get(answer.questionId());
            if (question == null) throw CourseException.invalid("Answer references an unknown question");
            Set<UUID> options = question.options().stream().map(CourseDtos.OptionContent::id).collect(Collectors.toSet());
            Set<UUID> selected = new HashSet<>(answer.optionIds());
            if (!options.containsAll(selected)) throw CourseException.invalid("Answer references an unknown option");
            if (question.type() != CourseDtos.QuestionType.MULTIPLE_CHOICE && selected.size() > 1) {
                throw CourseException.invalid("Only one option may be selected");
            }
            merged.put(question.id(), selected);
        }
        return merged;
    }

    private Attempt view(AttemptRow a, boolean includeDraftAnswers) {
        boolean submitted = a.status() == AttemptStatus.SUBMITTED;
        AnswerVisibility visibility = a.quiz().answerVisibility();
        boolean revealed = submitted && (visibility == AnswerVisibility.AFTER_SUBMIT
                || (visibility == AnswerVisibility.AFTER_PASSED && Boolean.TRUE.equals(a.passed())));
        var questions = a.quiz().questions().stream().map(q -> {
            Set<UUID> selected = a.answers().getOrDefault(q.id(), Set.of());
            var options = q.options().stream().map(o -> new AttemptOption(o.id(), o.text(), revealed ? o.correct() : null)).toList();
            return new AttemptQuestion(q.id(), q.prompt(), q.type(), q.points(), options,
                    selected.stream().sorted().toList(), revealed ? PublicQuizGrader.correct(q, selected) : null,
                    revealed ? q.explanation() : null);
        }).toList();
        return new Attempt(a.id(), a.quizId(), a.attemptNo(), a.status(), a.startedAt(), a.deadlineAt(), a.submittedAt(),
                a.score(), a.passed(), revealed, questions, now());
    }

    private AttemptRow ownAttempt(Context ctx, UUID attemptId) {
        return courses.attempt(attemptId, true)
                .filter(a -> a.quizId().equals(ctx.quiz.id()) && a.enrollmentId().equals(ctx.enrollment.id()))
                .orElseThrow(() -> CourseException.notFound("QUIZ_ATTEMPT_NOT_FOUND", "Quiz attempt not found"));
    }

    private boolean expired(AttemptRow attempt) {
        return attempt.deadlineAt() != null && !now().isBefore(attempt.deadlineAt());
    }

    private Context context(UUID actor, UUID courseId, UUID quizId) {
        policy.learner(actor);
        CourseRow course = courses.course(courseId, false).orElseThrow(CourseException::notFound);
        EnrollmentRow enrollment = policy.learningEnrollment(actor, course);
        QuizRow quiz = courses.quiz(courseId, quizId)
                .orElseThrow(() -> CourseException.notFound("QUIZ_NOT_FOUND", "Quiz not found"));
        return new Context(Objects.requireNonNull(course), enrollment, quiz);
    }

    private Instant now() { return clock.instant(); }

    private record Context(CourseRow course, EnrollmentRow enrollment, QuizRow quiz) { }
}
