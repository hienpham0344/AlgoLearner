package com.algolearner.platform.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.algolearner.platform.course.CourseDtos.AnswerVisibility;
import com.algolearner.platform.course.CourseDtos.Block;
import com.algolearner.platform.course.CourseDtos.BlockType;
import com.algolearner.platform.course.CourseDtos.CompletionRule;
import com.algolearner.platform.course.CourseDtos.CourseContent;
import com.algolearner.platform.course.CourseDtos.LessonContent;
import com.algolearner.platform.course.CourseDtos.OptionContent;
import com.algolearner.platform.course.CourseDtos.ProgressStatus;
import com.algolearner.platform.course.CourseDtos.QuestionContent;
import com.algolearner.platform.course.CourseDtos.QuestionType;
import com.algolearner.platform.course.CourseDtos.QuizContent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CourseRulesTest {
    static final UUID L1 = UUID.randomUUID(), L2 = UUID.randomUUID(), Q1 = UUID.randomUUID();

    @Nested class Progress {
        @Test void completedOnlyWhenAllLessonsAndMandatoryQuizzesDone() { // FR-PCRS-006 AC-01..04, AC-06
            var partial = CourseProgressCalculator.evaluate(Set.of(L1, L2), Set.of(Q1), Set.of(L1, L2), Set.of(),
                    ProgressStatus.IN_PROGRESS, true);
            assertThat(partial.status()).isEqualTo(ProgressStatus.IN_PROGRESS);
            assertThat(partial.percent()).isEqualByComparingTo("66.67");
            var done = CourseProgressCalculator.evaluate(Set.of(L1, L2), Set.of(Q1), Set.of(L1, L2), Set.of(Q1),
                    ProgressStatus.IN_PROGRESS, true);
            assertThat(done.status()).isEqualTo(ProgressStatus.COMPLETED);
            assertThat(done.becameCompleted()).isTrue();
        }

        @Test void optionalQuizAndPracticeDoNotBlockCompletion() { // FR-PCRS-006 AC-05
            var result = CourseProgressCalculator.evaluate(Set.of(L1), Set.of(), Set.of(L1), Set.of(), ProgressStatus.IN_PROGRESS, true);
            assertThat(result.status()).isEqualTo(ProgressStatus.COMPLETED);
        }

        @Test void completedIsNeverDemotedWhenNewLessonAppears() { // FR-PCRS-007 AC-02, AC-06; FR-PCRS-015 AC-03
            var result = CourseProgressCalculator.evaluate(Set.of(L1, L2), Set.of(), Set.of(L1), Set.of(), ProgressStatus.COMPLETED, true);
            assertThat(result.status()).isEqualTo(ProgressStatus.COMPLETED);
            assertThat(result.becameCompleted()).isFalse();
            assertThat(result.percent()).isEqualByComparingTo("50.00");
        }

        @Test void notStartedUntilActivityAndEmptyCourseNeverCompletes() { // FR-PCRS-005 AC-03
            assertThat(CourseProgressCalculator.evaluate(Set.of(L1), Set.of(), Set.of(), Set.of(), ProgressStatus.NOT_STARTED, false).status())
                    .isEqualTo(ProgressStatus.NOT_STARTED);
            assertThat(CourseProgressCalculator.evaluate(Set.of(L1), Set.of(), Set.of(), Set.of(), ProgressStatus.NOT_STARTED, true).status())
                    .isEqualTo(ProgressStatus.IN_PROGRESS);
            assertThat(CourseProgressCalculator.evaluate(Set.of(), Set.of(), Set.of(), Set.of(), ProgressStatus.IN_PROGRESS, true).status())
                    .isEqualTo(ProgressStatus.IN_PROGRESS);
        }

        @Test void removedLessonCompletionsDoNotInflateProgress() { // FR-PCRS-015 AC-01
            var result = CourseProgressCalculator.evaluate(Set.of(L2), Set.of(), Set.of(L1), Set.of(), ProgressStatus.IN_PROGRESS, true);
            assertThat(result.completedLessons()).isZero();
            assertThat(result.status()).isEqualTo(ProgressStatus.IN_PROGRESS);
        }
    }

    @Nested class Grading {
        final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        final QuestionContent single = new QuestionContent(UUID.randomUUID(), "1+1", QuestionType.SINGLE_CHOICE, BigDecimal.ONE, null,
                List.of(new OptionContent(a, "2", true), new OptionContent(b, "3", false)));
        final QuestionContent multi = new QuestionContent(UUID.randomUUID(), "even", QuestionType.MULTIPLE_CHOICE, BigDecimal.valueOf(3), null,
                List.of(new OptionContent(c, "2", true), new OptionContent(d, "4", true)));

        @Test void allOrNothingPerQuestionWeightedByPoints() {
            var graded = PublicQuizGrader.grade(List.of(single, multi), Map.of(single.id(), Set.of(a), multi.id(), Set.of(c)),
                    CompletionRule.PASSING_SCORE, BigDecimal.valueOf(50));
            assertThat(graded.score()).isEqualByComparingTo("25.00");
            assertThat(graded.passed()).isFalse();
        }

        @Test void submitIsEnoughPassesRegardlessOfScore() { // FR-PCRS-010 AC-05
            assertThat(PublicQuizGrader.grade(List.of(single), Map.of(), CompletionRule.SUBMIT, null).passed()).isTrue();
        }

        @Test void passingScoreThresholdIsInclusive() { // FR-PCRS-010 AC-06
            var graded = PublicQuizGrader.grade(List.of(single, multi), Map.of(multi.id(), Set.of(c, d)),
                    CompletionRule.PASSING_SCORE, BigDecimal.valueOf(75));
            assertThat(graded.score()).isEqualByComparingTo("75.00");
            assertThat(graded.passed()).isTrue();
        }
    }

    @Nested class Content {
        @Test void assignsIdsAndAcceptsAllSupportedBlockTypes() { // FR-PCRS-008 AC-01
            UUID problem = UUID.randomUUID(), quiz = UUID.randomUUID();
            var lesson = new LessonContent(null, "Intro", 10, "Arrays", true, List.of(Block.text("Hello"),
                    new Block(BlockType.CODE, null, "print(1)", "python", null, null, null, null),
                    new Block(BlockType.IMAGE, null, null, null, "https://cdn.example.org/a.png", "diagram", null, null),
                    new Block(BlockType.QUIZ, null, null, null, null, null, null, quiz),
                    new Block(BlockType.PRACTICE_LINK, null, null, null, null, null, problem, null)));
            var content = new CourseContent(" Course ", null, CourseDtos.Level.BEGINNER, "Arrays", 60, List.of(lesson), List.of(quiz(quiz)));
            var normalized = CourseContentRules.normalize(content, true, problem::equals);
            assertThat(normalized.title()).isEqualTo("Course");
            assertThat(normalized.lessons().get(0).id()).isNotNull();
            assertThat(normalized.quizzes().get(0).questions().get(0).id()).isNotNull();
        }

        @Test void rejectsInvalidContent() {
            UUID quiz = UUID.randomUUID();
            assertThatThrownBy(() -> CourseContentRules.normalize(course(List.of()), true, id -> true))
                    .hasMessageContaining("lesson"); // submission requires a lesson
            assertThatThrownBy(() -> CourseContentRules.normalize(course(List.of(
                    new Block(BlockType.IMAGE, null, null, null, "http://insecure.example.org/a.png", null, null, null))), false, id -> true))
                    .isInstanceOf(CourseException.class); // FR-PCRS-008 AC-06
            assertThatThrownBy(() -> CourseContentRules.normalize(course(List.of(
                    new Block(BlockType.PRACTICE_LINK, null, null, null, null, null, UUID.randomUUID(), null))), false, id -> false))
                    .hasMessageContaining("published public problem");
            assertThatThrownBy(() -> CourseContentRules.normalize(course(List.of(
                    new Block(BlockType.QUIZ, null, null, null, null, null, null, quiz))), false, id -> true))
                    .hasMessageContaining("unknown quiz");
        }

        @Test void rejectsQuestionsWhoseOptionsDoNotMatchTheType() {
            var bad = new QuizContent(null, null, "Q", true, CompletionRule.SUBMIT, null, null, null, AnswerVisibility.NEVER,
                    List.of(new QuestionContent(null, "?", QuestionType.SINGLE_CHOICE, BigDecimal.ONE, null,
                            List.of(new OptionContent(null, "a", true), new OptionContent(null, "b", true)))));
            assertThatThrownBy(() -> CourseContentRules.normalizeQuiz(bad, UUID.randomUUID())).isInstanceOf(CourseException.class);
            var noThreshold = new QuizContent(null, null, "Q", true, CompletionRule.PASSING_SCORE, null, null, null,
                    AnswerVisibility.NEVER, quiz(UUID.randomUUID()).questions());
            assertThatThrownBy(() -> CourseContentRules.normalizeQuiz(noThreshold, UUID.randomUUID())).hasMessageContaining("Passing score");
        }

        private CourseContent course(List<Block> blocks) {
            var lessons = blocks.isEmpty() ? List.<LessonContent>of() : List.of(new LessonContent(null, "L", null, null, false, blocks));
            return new CourseContent("C", null, null, null, null, lessons, List.of());
        }
    }

    static QuizContent quiz(UUID id) {
        return new QuizContent(id, null, "Quiz", true, CompletionRule.SUBMIT, null, 2, null, AnswerVisibility.AFTER_SUBMIT,
                List.of(new QuestionContent(null, "1+1?", QuestionType.TRUE_FALSE, BigDecimal.ONE, "Because",
                        List.of(new OptionContent(null, "2", true), new OptionContent(null, "3", false)))));
    }
}
