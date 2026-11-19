package com.algolearner.platform.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class QuizDtos {
    private QuizDtos() { }
    public enum Kind { GRADED, UNGRADED }
    public enum QuestionType { SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE }
    public enum Scoring { ALL_OR_NOTHING, PARTIAL_CREDIT }
    public enum Timeout { AUTO_SUBMIT, LOCK_WITHOUT_SUBMIT }
    public enum Completion { SUBMIT_IS_ENOUGH, PASSING_SCORE }
    public enum Publication { AUTO, MANUAL }
    public enum Status { DRAFT, PUBLISHED, CLOSED }
    public record Option(@NotNull UUID id, @NotBlank @Size(max=4000) String text,
                         @NotNull Boolean correct,
                         @DecimalMin("0") @DecimalMax("1000") @Digits(integer=4,fraction=6) BigDecimal weight) { }
    public record Question(@NotNull UUID id, @NotBlank @Size(max=20000) String text,
                           @NotNull QuestionType type, @NotNull @DecimalMin("0.01") @DecimalMax("1000") BigDecimal points,
                           @Size(max=20000) String explanation, @NotNull @Size(min=2,max=20) List<@Valid Option> options) { }
    public record Config(@NotNull Kind kind, @Min(1) @Max(10000) int maxAttempts,
                         Instant startTime, Instant deadline, boolean allowLateAttempts,
                         @Min(1) @Max(604800) Integer timeLimitSeconds, @NotNull Timeout timeout,
                         boolean shuffleQuestions, boolean shuffleAnswers, @NotNull Scoring scoring,
                         @NotNull Completion completion, @DecimalMin("0") @DecimalMax("100") BigDecimal passingScore,
                         @NotNull Publication publication, boolean showCorrectAnswers, boolean showExplanations,
                         @NotNull @Size(min=1,max=200) List<@Valid Question> questions) { }
    public record Write(@NotBlank @Size(max=200) String title, @NotNull @Valid Config config) { }
    public record ChangeStatus(@NotNull Status status) { }
    public record PublishGrades(boolean published) { }
    public record Answer(@NotNull @Size(max=20) List<@NotNull UUID> optionIds) { }
    public record AccessOverride(boolean resetAttempts, @Min(0) @Max(10000) int extraAttempts,
                                 Instant deadline, @Min(1) @Max(604800) Integer timeLimitSeconds) { }
    public record GradeOverride(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal score,
                                @NotBlank @Size(max=2000) String reason) { }
    public record OptionView(UUID id, String text, Boolean correct) { }
    public record QuestionView(UUID id, String text, QuestionType type, BigDecimal points,
                               String explanation, List<OptionView> options) { }
    public record QuizView(UUID id, UUID classroomId, String title, String status, Config teacherConfiguration,
                           Kind kind, int maxAttempts, Instant startTime, Instant deadline,
                           Integer timeLimitSeconds, List<QuestionView> questions) { }
    public record AttemptView(UUID id, UUID studentId, int number, String status, Instant serverTime,
                              Instant startedAt, Instant deadline, Instant submittedAt, boolean late,
                              BigDecimal score, Boolean completed, List<QuestionView> questions,
                              Map<UUID,List<UUID>> answers) { }
    public record Result(UUID studentId, BigDecimal originalAutoScore, BigDecimal finalScore,
                         Boolean completed, int attemptsUsed, int attemptsAllowed) { }
    public record GradeChange(UUID id, UUID actorId, BigDecimal originalScore, BigDecimal finalScore,
                              String reason, Instant createdAt) { }
}
