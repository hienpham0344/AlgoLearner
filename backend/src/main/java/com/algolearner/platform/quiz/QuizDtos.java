package com.algolearner.platform.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuizDtos {
    private QuizDtos() { }
    public enum QuestionType { SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE }
    public enum Scoring { ALL_OR_NOTHING, PARTIAL }
    public enum ReleasePolicy { MANUAL, IMMEDIATE }
    public enum TimeoutMode { AUTO_SUBMIT, LOCK }
    public record Option(@NotNull UUID id,@NotBlank String text,boolean correct) { }
    public record Question(@NotNull UUID id,@NotBlank String prompt,@NotNull QuestionType type,
            @NotNull Scoring scoring,@NotNull @Positive BigDecimal points,@NotEmpty List<@Valid Option> options) { }
    public record Configure(@NotBlank @Size(max=255) String title,boolean graded,Instant opensAt,Instant closesAt,
            Integer durationSeconds,@Min(1) int maxAttempts,boolean allowLate,boolean shuffleQuestions,boolean shuffleAnswers,
            ReleasePolicy releasePolicy,TimeoutMode timeoutMode,@NotEmpty List<@Valid Question> questions) { }
    public record QuizView(UUID id,UUID classroomId,String title,String status,boolean graded,Instant opensAt,Instant closesAt,
            Integer durationSeconds,int maxAttempts,boolean allowLate,ReleasePolicy releasePolicy) { }
    public record StudentOption(UUID id,String text) { }
    public record StudentQuestion(UUID id,String prompt,QuestionType type,BigDecimal points,List<StudentOption> options) { }
    public record AnswerView(UUID questionId,List<UUID> selectedOptionIds,long version) { }
    public record AttemptView(UUID id,UUID quizId,String status,Instant startedAt,Instant cutoffAt,Instant submittedAt,
            BigDecimal score,boolean gradePublished,List<StudentQuestion> questions,List<AnswerView> answers) { }
    public record SaveAnswer(@NotNull UUID questionId,@NotNull List<@NotNull UUID> selectedOptionIds,
            @Min(0) long expectedVersion,@NotNull UUID idempotencyKey) { }
    public record SaveReceipt(boolean accepted,String attemptStatus,UUID questionId,long version) { }
    public record AdjustAttempts(@NotNull UUID studentId,@NotBlank String action,@Min(0) int additionalAttempts,
            @NotBlank @Size(max=1000) String reason) { }
    public record OfficialScore(boolean submitted,BigDecimal score,UUID evaluationId) { }
    record Snapshot(List<Question> questions,boolean graded,ReleasePolicy releasePolicy,TimeoutMode timeoutMode) { }
}
