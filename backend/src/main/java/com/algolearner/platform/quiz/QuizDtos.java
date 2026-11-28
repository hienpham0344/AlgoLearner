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
    public record Option(@NotNull UUID id,@NotBlank String text,boolean correct,@PositiveOrZero BigDecimal weight) {
        public Option(UUID id,String text,boolean correct){this(id,text,correct,null);}
    }
    public record Question(@NotNull UUID id,@NotBlank String prompt,@NotNull QuestionType type,
            @NotNull Scoring scoring,@NotNull @Positive BigDecimal points,@NotEmpty List<@Valid Option> options,String explanation) {
        public Question(UUID id,String prompt,QuestionType type,Scoring scoring,BigDecimal points,List<Option> options){
            this(id,prompt,type,scoring,points,options,null);
        }
    }
    public record Configure(@NotBlank @Size(max=255) String title,boolean graded,Instant opensAt,Instant closesAt,
            Integer durationSeconds,@Min(1) int maxAttempts,boolean allowLate,boolean shuffleQuestions,boolean shuffleAnswers,
            ReleasePolicy releasePolicy,TimeoutMode timeoutMode,@NotEmpty List<@Valid Question> questions,
            boolean showCorrectAnswers,boolean showExplanations,@DecimalMin("0") @DecimalMax("100") BigDecimal passingScore) {
        public Configure(String title,boolean graded,Instant opensAt,Instant closesAt,Integer durationSeconds,int maxAttempts,
                boolean allowLate,boolean shuffleQuestions,boolean shuffleAnswers,ReleasePolicy releasePolicy,TimeoutMode timeoutMode,List<Question> questions){
            this(title,graded,opensAt,closesAt,durationSeconds,maxAttempts,allowLate,shuffleQuestions,shuffleAnswers,releasePolicy,
                    timeoutMode,questions,false,false,null);
        }
    }
    public record QuizView(UUID id,UUID classroomId,String title,String status,boolean graded,Instant opensAt,Instant closesAt,
            Integer durationSeconds,int maxAttempts,boolean allowLate,ReleasePolicy releasePolicy) { }
    public record StudentOption(UUID id,String text) { }
    public record StudentQuestion(UUID id,String prompt,QuestionType type,BigDecimal points,List<StudentOption> options) { }
    public record AnswerView(UUID questionId,List<UUID> selectedOptionIds,long version) { }
    public record AttemptView(UUID id,UUID quizId,UUID studentId,String status,Instant startedAt,Instant cutoffAt,Instant submittedAt,
            BigDecimal score,boolean gradePublished,List<StudentQuestion> questions,List<AnswerView> answers,Instant serverTime,boolean late) { }
    public record SaveAnswer(@NotNull UUID questionId,@NotNull List<@NotNull UUID> selectedOptionIds,
            @Min(0) long expectedVersion,@NotNull UUID idempotencyKey) { }
    public record SaveReceipt(boolean accepted,String attemptStatus,UUID questionId,long version) { }
    public record AdjustAttempts(@NotNull UUID studentId,@NotBlank String action,@Min(0) int additionalAttempts,
            @NotBlank @Size(max=1000) String reason) { }
    public record ReviewQuestion(UUID questionId,List<UUID> correctOptionIds,String explanation) { }
    public record GradeOverride(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal score,
            @NotBlank @Size(max=1000) String reason) { }
    public record GradeChange(long id,UUID actorId,BigDecimal originalScore,BigDecimal finalScore,String reason,Instant createdAt) { }
    public record Result(boolean submitted,boolean completed,BigDecimal originalScore,BigDecimal finalScore) { }
    record Snapshot(List<Question> questions,boolean graded,ReleasePolicy releasePolicy,TimeoutMode timeoutMode) { }
}
