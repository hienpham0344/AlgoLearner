package com.algolearner.platform.course;

import com.algolearner.platform.course.CourseDtos.ProgressStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;

/**
 * FR-PCRS-005/006/007/015. Completion requires every active lesson and every mandatory quiz;
 * linked Public Practice never counts. A learner already COMPLETED is never demoted.
 */
public final class CourseProgressCalculator {
    private CourseProgressCalculator() { }

    public record Result(ProgressStatus status, BigDecimal percent, int completedLessons, int completedQuizzes,
                         boolean becameCompleted) { }

    public static Result evaluate(Set<UUID> activeLessons, Set<UUID> mandatoryQuizzes, Set<UUID> completedLessons,
                                  Set<UUID> completedQuizzes, ProgressStatus current, boolean started) {
        int lessonsDone = (int) activeLessons.stream().filter(completedLessons::contains).count();
        int quizzesDone = (int) mandatoryQuizzes.stream().filter(completedQuizzes::contains).count();
        int total = activeLessons.size() + mandatoryQuizzes.size();
        int done = lessonsDone + quizzesDone;
        BigDecimal percent = total == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(100L * done).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        if (current == ProgressStatus.COMPLETED) {
            return new Result(ProgressStatus.COMPLETED, percent, lessonsDone, quizzesDone, false);
        }
        if (!activeLessons.isEmpty() && done == total) {
            return new Result(ProgressStatus.COMPLETED, percent, lessonsDone, quizzesDone, true);
        }
        ProgressStatus status = started || done > 0 || !completedLessons.isEmpty() || !completedQuizzes.isEmpty()
                ? ProgressStatus.IN_PROGRESS : ProgressStatus.NOT_STARTED;
        return new Result(status, percent, lessonsDone, quizzesDone, false);
    }
}
