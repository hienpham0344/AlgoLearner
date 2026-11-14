package com.algolearner.platform.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssignmentDtos {
    private AssignmentDtos() { }

    public record CreateAssignmentRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title cannot exceed 255 characters")
            String title,
            String description,
            AssignmentType type,
            Instant startTime,
            Instant deadline,
            Boolean allowLateSubmission,
            BigDecimal latePenaltyPercent,
            Integer durationMinutes,
            TimeExpiredAction timeExpiredAction,
            Integer maxAttempts,
            ScoringPolicy scoringPolicy,
            Boolean shuffleQuestions,
            Boolean shuffleAnswers,
            GradePublishMode gradePublishMode,
            ViewAnswersMode viewAnswersMode,
            CodeFeedbackLevel codeFeedbackLevel
    ) { }

    public record UpdateAssignmentRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title cannot exceed 255 characters")
            String title,
            String description,
            AssignmentType type,
            Instant startTime,
            Instant deadline,
            Boolean allowLateSubmission,
            BigDecimal latePenaltyPercent,
            Integer durationMinutes,
            TimeExpiredAction timeExpiredAction,
            Integer maxAttempts,
            ScoringPolicy scoringPolicy,
            Boolean shuffleQuestions,
            Boolean shuffleAnswers,
            GradePublishMode gradePublishMode,
            ViewAnswersMode viewAnswersMode,
            CodeFeedbackLevel codeFeedbackLevel
    ) { }

    public record OptionRequest(
            @NotBlank(message = "Option content is required")
            String content,
            boolean isCorrect
    ) { }

    public record AddQuestionRequest(
            @NotNull(message = "Question type is required")
            QuestionType questionType,
            @NotBlank(message = "Title is required")
            String title,
            @NotBlank(message = "Prompt is required")
            String prompt,
            @NotNull(message = "Points are required")
            @PositiveOrZero(message = "Points must be non-negative")
            BigDecimal points,
            GradingPolicy gradingPolicy,
            EssayFormat essayFormat,
            String explanation,
            String codeTemplate,
            String codeLanguage,
            List<OptionRequest> options
    ) { }

    public record SaveDraftAnswerRequest(
            @NotNull(message = "Question ID is required")
            UUID questionId,
            String selectedOptionIds,
            String textAnswer,
            String fileUrl,
            String codeSubmission
    ) { }

    public record GradeEssayRequest(
            @NotNull(message = "Question ID is required")
            UUID questionId,
            @NotNull(message = "Score is required")
            @PositiveOrZero
            BigDecimal score,
            String feedback
    ) { }

    public record OverrideGradeRequest(
            @NotNull(message = "Overridden score is required")
            @PositiveOrZero
            BigDecimal overriddenScore,
            @NotBlank(message = "Override reason is required")
            String reason
    ) { }

    public record RejudgeRequest(
            String reason
    ) { }

    public record OptionView(
            UUID id,
            String content,
            Boolean isCorrect
    ) { }

    public record QuestionView(
            UUID id,
            QuestionType questionType,
            String title,
            String prompt,
            BigDecimal points,
            int displayOrder,
            GradingPolicy gradingPolicy,
            EssayFormat essayFormat,
            String codeTemplate,
            String codeLanguage,
            List<OptionView> options,
            String explanation
    ) { }

    public record AnswerView(
            UUID id,
            UUID questionId,
            String selectedOptionIds,
            String textAnswer,
            String fileUrl,
            String codeSubmission,
            boolean isDraft,
            BigDecimal score,
            String feedback
    ) { }

    public record AttemptView(
            UUID id,
            UUID assignmentId,
            UUID studentId,
            String studentName,
            int attemptNumber,
            AttemptStatus status,
            Instant startedAt,
            Instant submittedAt,
            boolean isLate,
            BigDecimal autoScore,
            BigDecimal manualScore,
            BigDecimal overriddenScore,
            BigDecimal finalScore,
            String overrideReason,
            String teacherFeedback,
            Long remainingSeconds,
            List<AnswerView> answers
    ) { }

    public record AssignmentSummaryView(
            UUID id,
            UUID classroomId,
            String title,
            AssignmentType type,
            AssignmentStatus status,
            Instant startTime,
            Instant deadline,
            Integer durationMinutes,
            int maxAttempts,
            boolean isGradePublished,
            Integer myRemainingAttempts,
            BigDecimal myBestScore,
            Instant createdAt
    ) { }

    public record AssignmentDetailView(
            UUID id,
            UUID classroomId,
            String title,
            String description,
            AssignmentType type,
            AssignmentStatus status,
            Instant startTime,
            Instant deadline,
            boolean allowLateSubmission,
            BigDecimal latePenaltyPercent,
            Integer durationMinutes,
            TimeExpiredAction timeExpiredAction,
            int maxAttempts,
            ScoringPolicy scoringPolicy,
            boolean shuffleQuestions,
            boolean shuffleAnswers,
            GradePublishMode gradePublishMode,
            boolean isGradePublished,
            ViewAnswersMode viewAnswersMode,
            CodeFeedbackLevel codeFeedbackLevel,
            List<QuestionView> questions,
            Integer myRemainingAttempts,
            BigDecimal myBestScore,
            Instant createdAt,
            Instant updatedAt
    ) { }
}
