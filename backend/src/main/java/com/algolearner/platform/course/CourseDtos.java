package com.algolearner.platform.course;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** SRS 4.11 API contract. All instants serialize as ISO-8601 UTC. */
public final class CourseDtos {
    private CourseDtos() { }

    public enum Level { BEGINNER, INTERMEDIATE, ADVANCED }
    public enum Publication { DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED }
    public enum UnpublishPolicy { ENROLLED_CONTINUE, LOCK_ALL }
    public enum ReviewStatus { DRAFT, PENDING_REVIEW, APPROVED, CHANGES_REQUESTED, REJECTED }
    public enum ReviewDecision { APPROVE, CHANGES_REQUESTED, REJECT }
    public enum PublicationAction { PUBLISH, UNPUBLISH, ARCHIVE }
    public enum ProgressStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }
    public enum BlockType { TEXT, CODE, IMAGE, QUIZ, PRACTICE_LINK }
    public enum QuestionType { SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE }
    public enum CompletionRule { SUBMIT, PASSING_SCORE }
    public enum AnswerVisibility { NEVER, AFTER_SUBMIT, AFTER_PASSED }
    public enum AttemptStatus { IN_PROGRESS, SUBMITTED }

    // ---- Authoring content (Teacher revision snapshot / Admin direct content) ----
    public record Create(@NotBlank @Size(max = 255) String title, @Size(max = 5000) String description,
                         Level level, @Size(max = 80) String topic, @Min(1) @Max(100000) Integer estimatedMinutes) {
        public Create(String title, String description) { this(title, description, null, null, null); }
    }

    public record Block(@NotNull BlockType type, @Size(max = 100000) String text, @Size(max = 50000) String code,
                        @Size(max = 30) String language, @Size(max = 2048) String imageUrl, @Size(max = 255) String alt,
                        UUID problemId, UUID quizId) {
        public static Block text(String text) { return new Block(BlockType.TEXT, text, null, null, null, null, null, null); }
    }

    public record LessonContent(UUID id, @NotBlank @Size(max = 255) String title, @Min(1) @Max(100000) Integer estimatedMinutes,
                                @Size(max = 500) String prerequisiteNote, boolean preview,
                                @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull Block> blocks) { }

    public record OptionContent(UUID id, @NotBlank @Size(max = 1000) String text, boolean correct) { }

    public record QuestionContent(UUID id, @NotBlank @Size(max = 5000) String prompt, @NotNull QuestionType type,
                                  @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") BigDecimal points,
                                  @Size(max = 5000) String explanation,
                                  @NotNull @Size(min = 2, max = 10) List<@Valid @NotNull OptionContent> options) { }

    public record QuizContent(UUID id, UUID lessonId, @NotBlank @Size(max = 255) String title, boolean mandatory,
                              @NotNull CompletionRule completionRule, @DecimalMin("0") @DecimalMax("100") BigDecimal passingScore,
                              @Min(1) @Max(100) Integer maxAttempts, @Min(60) @Max(86400) Integer timeLimitSeconds,
                              @NotNull AnswerVisibility answerVisibility,
                              @NotNull @Size(min = 1, max = 200) List<@Valid @NotNull QuestionContent> questions) { }

    public record CourseContent(@NotBlank @Size(max = 255) String title, @Size(max = 5000) String description, Level level,
                                @Size(max = 80) String topic, @Min(1) @Max(100000) Integer estimatedMinutes,
                                @NotNull @Size(max = 200) List<@Valid @NotNull LessonContent> lessons,
                                @NotNull @Size(max = 100) List<@Valid @NotNull QuizContent> quizzes) { }

    /** Legacy single-lesson append; {@code content} becomes one TEXT block when {@code blocks} is absent. */
    public record UnitRequest(@NotBlank @Size(max = 255) String title, @Size(max = 100000) String content,
                              @Size(max = 100) List<@Valid @NotNull Block> blocks, Boolean preview,
                              @Min(1) @Max(100000) Integer estimatedMinutes, @Size(max = 500) String prerequisiteNote) {
        public UnitRequest(String title, String content) { this(title, content, null, null, null, null); }
    }

    // ---- Learner views ----
    public record Summary(UUID id, UUID authorId, String authorName, String title, String description, Level level,
                          String topic, Integer estimatedMinutes, Publication status, UnpublishPolicy unpublishPolicy,
                          int lessonCount, int quizCount, Instant publishedAt, Instant createdAt, Instant updatedAt) { }

    public record CatalogItem(Summary course, ProgressStatus enrollmentStatus) { }

    public record LessonOutline(UUID id, String title, int displayOrder, Integer estimatedMinutes, String prerequisiteNote,
                                boolean preview, boolean completed) { }

    public record QuizOutline(UUID id, UUID lessonId, String title, boolean mandatory, CompletionRule completionRule,
                              BigDecimal passingScore, Integer maxAttempts, Integer timeLimitSeconds, int questionCount,
                              boolean completed) { }

    public record Detail(Summary course, List<LessonOutline> lessons, List<QuizOutline> quizzes, Progress progress,
                         boolean contentAccessible) { }

    public record Lesson(UUID id, UUID courseId, String title, int displayOrder, Integer estimatedMinutes,
                         String prerequisiteNote, boolean preview, List<Block> blocks, boolean completed) { }

    public record Progress(UUID courseId, UUID accountId, ProgressStatus status, int completedUnits, int totalUnits,
                           int completedMandatoryQuizzes, int totalMandatoryQuizzes, double progressPercent,
                           List<UUID> completedLessonIds, List<UUID> completedQuizIds, Instant enrolledAt, Instant startedAt,
                           Instant completedAt, boolean hasUpdate, boolean contentLocked) { }

    public record EnrollmentHistory(Summary course, Progress progress) { }

    // ---- Quiz attempts ----
    public record AnswerRequest(@NotNull UUID questionId, @NotNull @Size(max = 10) List<@NotNull UUID> optionIds) { }
    public record SaveAnswers(@NotNull @Size(max = 200) List<@Valid @NotNull AnswerRequest> answers) { }

    public record AttemptOption(UUID id, String text, Boolean correct) { }
    public record AttemptQuestion(UUID id, String prompt, QuestionType type, BigDecimal points, List<AttemptOption> options,
                                  List<UUID> selectedOptionIds, Boolean correct, String explanation) { }

    public record Attempt(UUID id, UUID quizId, int attemptNo, AttemptStatus status, Instant startedAt, Instant deadlineAt,
                          Instant submittedAt, BigDecimal score, Boolean passed, boolean answersRevealed,
                          List<AttemptQuestion> questions, Instant serverTime) { }

    public record QuizStatus(QuizOutline quiz, int attemptsUsed, Integer attemptsRemaining, BigDecimal officialScore,
                             UUID inProgressAttemptId, List<Attempt> attempts) { }

    // ---- Contribution / review ----
    public record ContributionSummary(UUID revisionId, UUID courseId, UUID authorId, String authorName, int revisionNo,
                                      String title, ReviewStatus status, String reason, Publication publicationStatus,
                                      Instant submittedAt, Instant reviewedAt, Instant updatedAt) { }

    public record Contribution(ContributionSummary summary, CourseContent content) { }

    public record Review(Boolean approved, ReviewDecision decision, @Size(max = 1000) String reason, Boolean publish) {
        public Review(boolean approved, String reason) { this(approved, null, reason, null); }
        public ReviewDecision resolved() {
            if (decision != null) return decision;
            if (approved == null) return null;
            return approved ? ReviewDecision.APPROVE : ReviewDecision.REJECT;
        }
    }

    public record PublicationChange(@NotNull PublicationAction action, UnpublishPolicy unpublishPolicy,
                                    @Size(max = 1000) String reason) { }

    public record AdminDetail(Summary course, CourseContent content, ContributionSummary openContribution,
                              long enrollmentCount) { }
}
