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
            List<OptionRequest> options,
            UUID publicProblemId,
            List<com.algolearner.platform.judge.JudgeGateway.TestCaseSnapshot> testCases,
            BigDecimal questionWeight,
            List<BigDecimal> codeCaseWeights
    ) {
        public AddQuestionRequest(QuestionType questionType, String title, String prompt, BigDecimal points,
                GradingPolicy gradingPolicy, EssayFormat essayFormat, String explanation, String codeTemplate,
                String codeLanguage, List<OptionRequest> options) {
            this(questionType,title,prompt,points,gradingPolicy,essayFormat,explanation,codeTemplate,codeLanguage,options,null,null,null,null);
        }
    }

    public record SaveDraftAnswerRequest(
            @NotNull(message = "Question ID is required")
            UUID questionId,
            String selectedOptionIds,
            String textAnswer,
            String fileUrl,
            String codeSubmission,
            Long expectedVersion,
            UUID clientMutationId,
            UUID fileId,
            List<UUID> selectedOptions
    ) {
        public SaveDraftAnswerRequest(UUID questionId, String selectedOptionIds, String textAnswer, String fileUrl, String codeSubmission) {
            this(questionId, selectedOptionIds, textAnswer, fileUrl, codeSubmission, null, null, null, null);
        }
    }

    public record AttemptGrantRequest(@jakarta.validation.constraints.Min(0) int extraAttempts,
            boolean reset, @jakarta.validation.constraints.NotBlank String reason) { }

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

    public enum RejudgeTarget { ORIGINAL, LATEST_PUBLISHED }

    public record RejudgeRequest(String reason, RejudgeTarget target) {
        public RejudgeRequest { if (target == null) target = RejudgeTarget.ORIGINAL; }
        public RejudgeRequest(String reason) { this(reason, RejudgeTarget.ORIGINAL); }
    }

    public record CodeCaseWeightsRequest(@jakarta.validation.constraints.NotEmpty
            List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal> weights,
            @NotBlank @jakarta.validation.constraints.Size(max=500) String reason) { }
    public record CodeCaseWeightsView(UUID questionId,List<BigDecimal> weights) {
        public CodeCaseWeightsView { weights=List.copyOf(weights); }
    }

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
            String explanation,
            BigDecimal questionWeight,
            List<BigDecimal> codeCaseWeights,
            com.algolearner.platform.problem.PublicProblemCatalog.ContentSnapshot codeContent
    ) {
        public QuestionView(UUID id,QuestionType questionType,String title,String prompt,BigDecimal points,int displayOrder,
                GradingPolicy gradingPolicy,EssayFormat essayFormat,String codeTemplate,String codeLanguage,List<OptionView> options,String explanation) {
            this(id,questionType,title,prompt,points,displayOrder,gradingPolicy,essayFormat,codeTemplate,codeLanguage,options,explanation,BigDecimal.ONE,null,null);
        }
    }

    public record AnswerView(
            UUID id,
            UUID questionId,
            String selectedOptionIds,
            String textAnswer,
            String fileUrl,
            String codeSubmission,
            boolean isDraft,
            BigDecimal score,
            String feedback,
            long version,
            UUID fileId
    ) {
        public AnswerView(UUID id, UUID questionId, String selectedOptionIds, String textAnswer, String fileUrl,
                String codeSubmission, boolean isDraft, BigDecimal score, String feedback) {
            this(id, questionId, selectedOptionIds, textAnswer, fileUrl, codeSubmission, isDraft, score, feedback, 0, null);
        }
    }

    public record CodeJobView(UUID questionId, UUID jobId, Long generation) { }

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
            List<AnswerView> answers,
            UUID revisionId,
            String snapshotProvenance,
            List<QuestionView> questions,
            List<CodeJobView> codeJobs
    ) {
        public AttemptView(UUID id, UUID assignmentId, UUID studentId, String studentName, int attemptNumber,
                AttemptStatus status, Instant startedAt, Instant submittedAt, boolean isLate, BigDecimal autoScore,
                BigDecimal manualScore, BigDecimal overriddenScore, BigDecimal finalScore, String overrideReason,
                String teacherFeedback, Long remainingSeconds, List<AnswerView> answers, UUID revisionId,
                String snapshotProvenance, List<QuestionView> questions) {
            this(id, assignmentId, studentId, studentName, attemptNumber, status, startedAt, submittedAt, isLate,
                    autoScore, manualScore, overriddenScore, finalScore, overrideReason, teacherFeedback,
                    remainingSeconds, answers, revisionId, snapshotProvenance, questions, List.of());
        }
        public AttemptView(UUID id, UUID assignmentId, UUID studentId, String studentName, int attemptNumber,
                AttemptStatus status, Instant startedAt, Instant submittedAt, boolean isLate, BigDecimal autoScore,
                BigDecimal manualScore, BigDecimal overriddenScore, BigDecimal finalScore, String overrideReason,
                String teacherFeedback, Long remainingSeconds, List<AnswerView> answers) {
            this(id, assignmentId, studentId, studentName, attemptNumber, status, startedAt, submittedAt, isLate,
                    autoScore, manualScore, overriddenScore, finalScore, overrideReason, teacherFeedback,
                    remainingSeconds, answers, null, "LEGACY_MISSING", List.of());
        }
    }

    public record AssignmentSummaryView(
            UUID id,
            UUID classroomId,
            String title,
            AssignmentType type,
            AssignmentStatus status,
            Instant startTime,
            Instant deadline,
            Integer durationMinutes,
            Integer maxAttempts,
            Boolean isGradePublished,
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
