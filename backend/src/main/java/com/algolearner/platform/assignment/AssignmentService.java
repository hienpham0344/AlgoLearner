package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final AssignmentQuestionRepository questions;
    private final AssignmentQuestionOptionRepository options;
    private final AssignmentAttemptRepository attempts;
    private final AssignmentAnswerRepository answers;
    private final AssignmentRejudgeLogRepository rejudgeLogs;
    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;
    private final AccountRepository accounts;
    private final NotificationService notifications;
    private final Clock clock;

    public List<AssignmentDtos.AssignmentSummaryView> list(UUID accountId, UUID classroomId) {
        var classroom = requireClassroom(classroomId);
        boolean isOwner = classroom.getOwner().getId().equals(accountId);

        List<AssignmentEntity> list;
        if (isOwner) {
            list = assignments.findByClassroomIdOrderByCreatedAtDesc(classroomId);
        } else {
            requireActiveStudent(classroomId, accountId);
            list = assignments.findByClassroomIdAndStatusInOrderByCreatedAtDesc(
                    classroomId, List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)
            );
        }

        return list.stream().map(a -> toSummaryView(a, accountId, isOwner)).toList();
    }

    public AssignmentDtos.AssignmentDetailView get(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        boolean isOwner = classroom.getOwner().getId().equals(accountId);
        if (!isOwner) {
            requireActiveStudent(classroomId, accountId);
            if (assignment.getStatus() == AssignmentStatus.DRAFT) {
                throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
            }
        }

        return toDetailView(assignment, accountId, isOwner);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView create(UUID accountId, UUID classroomId, AssignmentDtos.CreateAssignmentRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var entity = AssignmentEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .title(request.title().trim())
                .description(request.description())
                .type(request.type() != null ? request.type() : AssignmentType.GRADED)
                .status(AssignmentStatus.DRAFT)
                .startTime(request.startTime())
                .deadline(request.deadline())
                .allowLateSubmission(Boolean.TRUE.equals(request.allowLateSubmission()))
                .latePenaltyPercent(request.latePenaltyPercent() != null ? request.latePenaltyPercent() : BigDecimal.ZERO)
                .durationMinutes(request.durationMinutes())
                .timeExpiredAction(request.timeExpiredAction() != null ? request.timeExpiredAction() : TimeExpiredAction.AUTO_SUBMIT)
                .maxAttempts(request.maxAttempts() != null ? request.maxAttempts() : 1)
                .scoringPolicy(request.scoringPolicy() != null ? request.scoringPolicy() : ScoringPolicy.HIGHEST)
                .shuffleQuestions(Boolean.TRUE.equals(request.shuffleQuestions()))
                .shuffleAnswers(Boolean.TRUE.equals(request.shuffleAnswers()))
                .gradePublishMode(request.gradePublishMode() != null ? request.gradePublishMode() : GradePublishMode.MANUAL)
                .isGradePublished(false)
                .viewAnswersMode(request.viewAnswersMode() != null ? request.viewAnswersMode() : ViewAnswersMode.NEVER)
                .codeFeedbackLevel(request.codeFeedbackLevel() != null ? request.codeFeedbackLevel() : CodeFeedbackLevel.DETAILED)
                .build();

        return toDetailView(assignments.save(entity), accountId, true);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView update(UUID accountId, UUID classroomId, UUID assignmentId, AssignmentDtos.UpdateAssignmentRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        assignment.setTitle(request.title().trim());
        if (request.description() != null) assignment.setDescription(request.description());
        if (request.type() != null) assignment.setType(request.type());
        if (request.startTime() != null) assignment.setStartTime(request.startTime());
        if (request.deadline() != null) assignment.setDeadline(request.deadline());
        if (request.allowLateSubmission() != null) assignment.setAllowLateSubmission(request.allowLateSubmission());
        if (request.latePenaltyPercent() != null) assignment.setLatePenaltyPercent(request.latePenaltyPercent());
        if (request.durationMinutes() != null) assignment.setDurationMinutes(request.durationMinutes());
        if (request.timeExpiredAction() != null) assignment.setTimeExpiredAction(request.timeExpiredAction());
        if (request.maxAttempts() != null) assignment.setMaxAttempts(request.maxAttempts());
        if (request.scoringPolicy() != null) assignment.setScoringPolicy(request.scoringPolicy());
        if (request.shuffleQuestions() != null) assignment.setShuffleQuestions(request.shuffleQuestions());
        if (request.shuffleAnswers() != null) assignment.setShuffleAnswers(request.shuffleAnswers());
        if (request.gradePublishMode() != null) assignment.setGradePublishMode(request.gradePublishMode());
        if (request.viewAnswersMode() != null) assignment.setViewAnswersMode(request.viewAnswersMode());
        if (request.codeFeedbackLevel() != null) assignment.setCodeFeedbackLevel(request.codeFeedbackLevel());

        return toDetailView(assignments.save(assignment), accountId, true);
    }

    @Transactional
    public void delete(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        if (attempts.existsByAssignmentId(assignmentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Cannot delete assignment with existing student submissions");
        }

        assignments.delete(assignment);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView publish(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        assignment.setStatus(AssignmentStatus.PUBLISHED);
        var saved = assignments.save(assignment);

        // Notify active students
        var activeMembers = memberships.findByClassroomIdAndStatus(classroomId, MembershipStatus.ACTIVE);
        for (var member : activeMembers) {
            notifications.notifyStudent(
                    member.getStudent(),
                    "ASSIGNMENT_PUBLISHED",
                    "New assignment: " + saved.getTitle(),
                    saved.getTitle(),
                    "/classrooms/" + classroomId + "/assignments/" + saved.getId(),
                    null,
                    saved.getId()
            );
        }

        return toDetailView(saved, accountId, true);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView close(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        assignment.setStatus(AssignmentStatus.CLOSED);
        return toDetailView(assignments.save(assignment), accountId, true);
    }

    @Transactional
    public void publishGrades(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        assignment.setGradePublished(true);
        assignments.save(assignment);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView cloneAssignment(UUID accountId, UUID classroomId, UUID assignmentId, UUID targetClassroomId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var targetClassroom = requireClassroom(targetClassroomId);
        requireOwner(targetClassroom, accountId);

        var src = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        var copy = AssignmentEntity.builder()
                .id(UUID.randomUUID())
                .classroom(targetClassroom)
                .title(src.getTitle() + " (Copy)")
                .description(src.getDescription())
                .type(src.getType())
                .status(AssignmentStatus.DRAFT)
                .startTime(null)
                .deadline(null)
                .allowLateSubmission(src.isAllowLateSubmission())
                .latePenaltyPercent(src.getLatePenaltyPercent())
                .durationMinutes(src.getDurationMinutes())
                .timeExpiredAction(src.getTimeExpiredAction())
                .maxAttempts(src.getMaxAttempts())
                .scoringPolicy(src.getScoringPolicy())
                .shuffleQuestions(src.isShuffleQuestions())
                .shuffleAnswers(src.isShuffleAnswers())
                .gradePublishMode(src.getGradePublishMode())
                .isGradePublished(false)
                .viewAnswersMode(src.getViewAnswersMode())
                .codeFeedbackLevel(src.getCodeFeedbackLevel())
                .build();

        var savedCopy = assignments.save(copy);

        // Clone questions & options
        var srcQuestions = questions.findByAssignmentIdOrderByDisplayOrderAsc(src.getId());
        for (var sq : srcQuestions) {
            var qCopy = AssignmentQuestionEntity.builder()
                    .id(UUID.randomUUID())
                    .assignment(savedCopy)
                    .questionType(sq.getQuestionType())
                    .title(sq.getTitle())
                    .prompt(sq.getPrompt())
                    .points(sq.getPoints())
                    .displayOrder(sq.getDisplayOrder())
                    .gradingPolicy(sq.getGradingPolicy())
                    .essayFormat(sq.getEssayFormat())
                    .explanation(sq.getExplanation())
                    .codeTemplate(sq.getCodeTemplate())
                    .codeLanguage(sq.getCodeLanguage())
                    .build();
            var savedQCopy = questions.save(qCopy);

            var srcOptions = options.findByQuestionIdOrderByDisplayOrderAsc(sq.getId());
            for (var opt : srcOptions) {
                var optCopy = AssignmentQuestionOptionEntity.builder()
                        .id(UUID.randomUUID())
                        .question(savedQCopy)
                        .content(opt.getContent())
                        .isCorrect(opt.isCorrect())
                        .displayOrder(opt.getDisplayOrder())
                        .build();
                options.save(optCopy);
            }
        }

        return toDetailView(savedCopy, accountId, true);
    }

    // --- Question Management ---
    @Transactional
    public AssignmentDtos.QuestionView addQuestion(UUID accountId, UUID classroomId, UUID assignmentId, AssignmentDtos.AddQuestionRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        int nextOrder = (int) questions.countByAssignmentId(assignmentId);
        var q = AssignmentQuestionEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .questionType(request.questionType())
                .title(request.title().trim())
                .prompt(request.prompt().trim())
                .points(request.points())
                .displayOrder(nextOrder)
                .gradingPolicy(request.gradingPolicy() != null ? request.gradingPolicy() : GradingPolicy.ALL_OR_NOTHING)
                .essayFormat(request.essayFormat())
                .explanation(request.explanation())
                .codeTemplate(request.codeTemplate())
                .codeLanguage(request.codeLanguage())
                .build();

        var saved = questions.save(q);

        if (request.options() != null) {
            int optOrder = 0;
            for (var optReq : request.options()) {
                var opt = AssignmentQuestionOptionEntity.builder()
                        .id(UUID.randomUUID())
                        .question(saved)
                        .content(optReq.content())
                        .isCorrect(optReq.isCorrect())
                        .displayOrder(optOrder++)
                        .build();
                options.save(opt);
            }
        }

        return toQuestionView(saved, true, true);
    }

    @Transactional
    public void deleteQuestion(UUID accountId, UUID classroomId, UUID assignmentId, UUID questionId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var q = questions.findByIdAndAssignmentId(questionId, assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));

        var qOptions = options.findByQuestionIdOrderByDisplayOrderAsc(questionId);
        options.deleteAll(qOptions);
        questions.delete(q);
    }

    // --- Student Taking Assignment (Attempts, Draft, Submit) ---
    @Transactional
    public AssignmentDtos.AttemptView startAttempt(UUID studentId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ASSIGNMENT_CLOSED);
        }

        var now = clock.instant();
        if (assignment.getStartTime() != null && now.isBefore(assignment.getStartTime())) {
            throw new ApiException(ErrorCode.ASSIGNMENT_NOT_STARTED);
        }

        if (assignment.getDeadline() != null && now.isAfter(assignment.getDeadline()) && !assignment.isAllowLateSubmission()) {
            throw new ApiException(ErrorCode.ASSIGNMENT_CLOSED);
        }

        // Check if an in-progress attempt already exists
        var existing = attempts.findFirstByAssignmentIdAndStudentIdAndStatus(assignmentId, studentId, AttemptStatus.IN_PROGRESS);
        if (existing.isPresent()) {
            return toAttemptView(existing.get(), assignment, false);
        }

        long pastAttempts = attempts.countByAssignmentIdAndStudentId(assignmentId, studentId);
        if (assignment.getMaxAttempts() > 0 && pastAttempts >= assignment.getMaxAttempts()) {
            throw new ApiException(ErrorCode.ATTEMPT_LIMIT_EXCEEDED);
        }

        var student = accounts.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .attemptNumber((int) pastAttempts + 1)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(now)
                .build();

        var savedAttempt = attempts.save(attempt);
        return toAttemptView(savedAttempt, assignment, false);
    }

    public AssignmentDtos.AttemptView getActiveAttempt(UUID studentId, UUID classroomId, UUID assignmentId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        var attempt = attempts.findFirstByAssignmentIdAndStudentIdAndStatus(assignmentId, studentId, AttemptStatus.IN_PROGRESS)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_ACTIVE_ATTEMPT));

        return toAttemptView(attempt, assignment, false);
    }

    @Transactional(noRollbackFor = TimeLimitExceededException.class)
    public AssignmentDtos.AnswerView saveDraftAnswer(UUID studentId, UUID classroomId, UUID assignmentId, UUID attemptId, AssignmentDtos.SaveDraftAnswerRequest request) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);

        if (!attempt.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.ATTEMPT_ALREADY_SUBMITTED);
        }

        // Verify time limit
        var assignment = attempt.getAssignment();
        if (isTimeExpired(attempt, assignment)) {
            if (assignment.getTimeExpiredAction() == TimeExpiredAction.AUTO_SUBMIT) {
                submitAttempt(studentId, classroomId, assignmentId, attemptId);
                throw new TimeLimitExceededException("Time limit expired; draft was auto-submitted");
            } else {
                attempt.setStatus(AttemptStatus.TIMED_OUT);
                attempts.save(attempt);
                throw new TimeLimitExceededException();
            }
        }

        var question = questions.findByIdAndAssignmentId(request.questionId(), assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));

        var answer = answers.findByAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseGet(() -> AssignmentAnswerEntity.builder()
                        .id(UUID.randomUUID())
                        .attempt(attempt)
                        .question(question)
                        .build());

        answer.setSelectedOptionIds(request.selectedOptionIds());
        answer.setTextAnswer(request.textAnswer());
        answer.setFileUrl(request.fileUrl());
        answer.setCodeSubmission(request.codeSubmission());
        answer.setDraft(true);
        answer.setLastSavedAt(clock.instant());

        var saved = answers.save(answer);
        return toAnswerView(saved);
    }

    @Transactional
    public AssignmentDtos.AttemptView submitAttempt(UUID studentId, UUID classroomId, UUID assignmentId, UUID attemptId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);

        if (!attempt.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.ATTEMPT_ALREADY_SUBMITTED);
        }

        var assignment = attempt.getAssignment();
        var now = clock.instant();

        boolean isLate = assignment.getDeadline() != null && now.isAfter(assignment.getDeadline());
        if (isLate && !assignment.isAllowLateSubmission()) {
            throw new ApiException(ErrorCode.ASSIGNMENT_CLOSED, "Deadline has passed and late submission is disabled");
        }

        attempt.setSubmittedAt(now);
        attempt.setLate(isLate);

        // Grade objective questions automatically
        var attemptAnswers = answers.findByAttemptId(attemptId);
        BigDecimal autoScore = BigDecimal.ZERO;
        boolean hasPendingManualGrading = false;

        for (var ans : attemptAnswers) {
            ans.setDraft(false);
            var q = ans.getQuestion();

            if (q.getQuestionType() == QuestionType.SINGLE_CHOICE || q.getQuestionType() == QuestionType.TRUE_FALSE) {
                BigDecimal qScore = gradeSingleChoiceOrTF(q, ans.getSelectedOptionIds());
                ans.setScore(qScore);
                autoScore = autoScore.add(qScore);
            } else if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                BigDecimal qScore = gradeMultipleChoice(q, ans.getSelectedOptionIds());
                ans.setScore(qScore);
                autoScore = autoScore.add(qScore);
            } else if (q.getQuestionType() == QuestionType.ESSAY) {
                hasPendingManualGrading = true;
            } else if (q.getQuestionType() == QuestionType.CODE) {
                // Keep code answers pending until a real judge provides a verdict.
                ans.setScore(null);
                hasPendingManualGrading = true;
            }
            answers.save(ans);
        }

        attempt.setAutoScore(autoScore);

        if (!hasPendingManualGrading) {
            BigDecimal finalScore = autoScore;
            if (isLate && assignment.getLatePenaltyPercent() != null && assignment.getLatePenaltyPercent().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal penalty = finalScore.multiply(assignment.getLatePenaltyPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                finalScore = finalScore.subtract(penalty).max(BigDecimal.ZERO);
            }
            attempt.setFinalScore(finalScore);
            attempt.setStatus(AttemptStatus.GRADED);
        } else {
            attempt.setStatus(AttemptStatus.SUBMITTED);
        }

        var saved = attempts.save(attempt);
        return toAttemptView(saved, assignment, false);
    }

    // --- Grading & Override & Rejudge (Teacher) ---
    @Transactional
    public void gradeEssay(UUID teacherId, UUID classroomId, UUID assignmentId, UUID attemptId, AssignmentDtos.GradeEssayRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);

        var attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);

        var question = questions.findByIdAndAssignmentId(request.questionId(), assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));
        if (question.getQuestionType() != QuestionType.ESSAY) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }

        var answer = answers.findByAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found"));

        answer.setScore(request.score());
        answer.setFeedback(request.feedback());
        answers.save(answer);

        recalculateFinalScore(attempt);
    }

    @Transactional
    public void overrideGrade(UUID teacherId, UUID classroomId, UUID assignmentId, UUID attemptId, AssignmentDtos.OverrideGradeRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);

        var attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);

        attempt.setOverriddenScore(request.overriddenScore());
        attempt.setOverrideReason(request.reason());
        attempt.setFinalScore(request.overriddenScore());
        attempt.setStatus(AttemptStatus.GRADED);
        attempts.save(attempt);
    }

    @Transactional
    public void rejudge(UUID teacherId, UUID classroomId, UUID assignmentId, AssignmentDtos.RejudgeRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        var teacher = accounts.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));

        var allAttempts = attempts.findByAssignmentIdOrderByCreatedAtDesc(assignmentId);
        for (var att : allAttempts) {
            if (att.getStatus() == AttemptStatus.IN_PROGRESS) continue;

            BigDecimal oldScore = att.getFinalScore();
            recalculateFinalScore(att);
            BigDecimal newScore = att.getFinalScore();

            var log = AssignmentRejudgeLogEntity.builder()
                    .id(UUID.randomUUID())
                    .assignment(assignment)
                    .attempt(att)
                    .performedBy(teacher)
                    .oldScore(oldScore)
                    .newScore(newScore)
                    .reason(request != null ? request.reason() : "Automated rejudge")
                    .createdAt(clock.instant())
                    .build();
            rejudgeLogs.save(log);

            // In-app notification for affected student (FR-ASG-017)
            notifications.notifyStudent(
                    att.getStudent(),
                    "ASSIGNMENT_REJUDGE",
                    "Your assignment '" + assignment.getTitle() + "' score was updated to " + newScore,
                    assignment.getTitle(),
                    "/classrooms/" + classroomId + "/assignments/" + assignmentId,
                    null,
                    assignmentId
            );
        }
    }

    public List<AssignmentDtos.AttemptView> getSubmissions(UUID teacherId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        var list = attempts.findByAssignmentIdOrderByCreatedAtDesc(assignmentId);
        return list.stream().map(a -> toAttemptView(a, assignment, true)).toList();
    }

    // --- Helper calculations ---
    private boolean isTimeExpired(AssignmentAttemptEntity attempt, AssignmentEntity assignment) {
        if (assignment.getDurationMinutes() == null || assignment.getDurationMinutes() <= 0) return false;
        long elapsedSeconds = Duration.between(attempt.getStartedAt(), clock.instant()).toSeconds();
        return elapsedSeconds > (assignment.getDurationMinutes() * 60L);
    }

    private void recalculateFinalScore(AssignmentAttemptEntity attempt) {
        if (attempt.getOverriddenScore() != null) {
            attempt.setFinalScore(attempt.getOverriddenScore());
            attempt.setStatus(AttemptStatus.GRADED);
            attempts.save(attempt);
            return;
        }

        var attemptAnswers = answers.findByAttemptId(attempt.getId());
        BigDecimal total = BigDecimal.ZERO;
        boolean allGraded = true;

        for (var ans : attemptAnswers) {
            if (ans.getScore() != null) {
                total = total.add(ans.getScore());
            } else {
                allGraded = false;
            }
        }

        if (attempt.isLate() && attempt.getAssignment().getLatePenaltyPercent() != null) {
            BigDecimal penalty = total.multiply(attempt.getAssignment().getLatePenaltyPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            total = total.subtract(penalty).max(BigDecimal.ZERO);
        }

        attempt.setFinalScore(total);
        if (allGraded) {
            attempt.setStatus(AttemptStatus.GRADED);
        }
        attempts.save(attempt);
    }

    private BigDecimal gradeSingleChoiceOrTF(AssignmentQuestionEntity q, String selectedOptionIds) {
        if (selectedOptionIds == null || selectedOptionIds.isBlank()) return BigDecimal.ZERO;
        var qOptions = options.findByQuestionIdOrderByDisplayOrderAsc(q.getId());
        for (var opt : qOptions) {
            if (opt.isCorrect()) {
                if (selectedOptionIds.contains(opt.getId().toString())) {
                    return q.getPoints();
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal gradeMultipleChoice(AssignmentQuestionEntity q, String selectedOptionIds) {
        if (selectedOptionIds == null || selectedOptionIds.isBlank()) return BigDecimal.ZERO;

        var qOptions = options.findByQuestionIdOrderByDisplayOrderAsc(q.getId());
        var correctIds = new HashSet<String>();
        var incorrectIds = new HashSet<String>();
        for (var opt : qOptions) {
            if (opt.isCorrect()) correctIds.add(opt.getId().toString());
            else incorrectIds.add(opt.getId().toString());
        }

        if (q.getGradingPolicy() == GradingPolicy.ALL_OR_NOTHING) {
            boolean allCorrectChosen = correctIds.stream().allMatch(selectedOptionIds::contains);
            boolean noIncorrectChosen = incorrectIds.stream().noneMatch(selectedOptionIds::contains);
            return (allCorrectChosen && noIncorrectChosen) ? q.getPoints() : BigDecimal.ZERO;
        } else {
            // Partial credit
            if (correctIds.isEmpty()) return BigDecimal.ZERO;
            long correctMatches = correctIds.stream().filter(selectedOptionIds::contains).count();
            long incorrectMatches = incorrectIds.stream().filter(selectedOptionIds::contains).count();

            if (incorrectMatches > 0) return BigDecimal.ZERO; // No negative points, but wrong choice invalidates
            BigDecimal pointPerCorrect = q.getPoints().divide(BigDecimal.valueOf(correctIds.size()), 2, RoundingMode.HALF_UP);
            return pointPerCorrect.multiply(BigDecimal.valueOf(correctMatches));
        }
    }

    private ClassroomEntity requireClassroom(UUID classroomId) {
        return classrooms.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    }

    private void requireOwner(ClassroomEntity classroom, UUID accountId) {
        if (!classroom.getOwner().getId().equals(accountId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
    }

    private void requireActiveStudent(UUID classroomId, UUID accountId) {
        boolean active = memberships.findByClassroomIdAndStudentId(classroomId, accountId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
    }

    private AssignmentDtos.AssignmentSummaryView toSummaryView(AssignmentEntity a, UUID accountId, boolean isOwner) {
        Integer myRemaining = null;
        BigDecimal myBest = null;
        if (!isOwner) {
            long used = attempts.countByAssignmentIdAndStudentId(a.getId(), accountId);
            myRemaining = a.getMaxAttempts() > 0 ? (int) Math.max(0, a.getMaxAttempts() - used) : 999;

            if (a.isGradePublished()) {
                var myAttempts = attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(a.getId(), accountId);
                myBest = myAttempts.stream()
                        .map(AssignmentAttemptEntity::getFinalScore)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
            }
        }

        return new AssignmentDtos.AssignmentSummaryView(
                a.getId(), a.getClassroom().getId(), a.getTitle(), a.getType(), a.getStatus(),
                a.getStartTime(), a.getDeadline(), a.getDurationMinutes(), a.getMaxAttempts(),
                a.isGradePublished(), myRemaining, myBest, a.getCreatedAt()
        );
    }

    private AssignmentDtos.AssignmentDetailView toDetailView(AssignmentEntity a, UUID accountId, boolean isOwner) {
        Integer myRemaining = null;
        BigDecimal myBest = null;
        if (!isOwner) {
            long used = attempts.countByAssignmentIdAndStudentId(a.getId(), accountId);
            myRemaining = a.getMaxAttempts() > 0 ? (int) Math.max(0, a.getMaxAttempts() - used) : 999;

            if (a.isGradePublished()) {
                var myAttempts = attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(a.getId(), accountId);
                myBest = myAttempts.stream()
                        .map(AssignmentAttemptEntity::getFinalScore)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
            }
        }

        boolean canSeeAnswers = isOwner || (a.getViewAnswersMode() == ViewAnswersMode.ALWAYS);
        var qViews = questions.findByAssignmentIdOrderByDisplayOrderAsc(a.getId()).stream()
                .map(q -> toQuestionView(q, canSeeAnswers, isOwner))
                .toList();

        return new AssignmentDtos.AssignmentDetailView(
                a.getId(), a.getClassroom().getId(), a.getTitle(), a.getDescription(), a.getType(), a.getStatus(),
                a.getStartTime(), a.getDeadline(), a.isAllowLateSubmission(), a.getLatePenaltyPercent(),
                a.getDurationMinutes(), a.getTimeExpiredAction(), a.getMaxAttempts(), a.getScoringPolicy(),
                a.isShuffleQuestions(), a.isShuffleAnswers(), a.getGradePublishMode(), a.isGradePublished(),
                a.getViewAnswersMode(), a.getCodeFeedbackLevel(), qViews, myRemaining, myBest,
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }

    private AssignmentDtos.QuestionView toQuestionView(AssignmentQuestionEntity q, boolean canSeeAnswers, boolean isOwner) {
        var qOpts = options.findByQuestionIdOrderByDisplayOrderAsc(q.getId());
        var optViews = qOpts.stream().map(o ->
                new AssignmentDtos.OptionView(o.getId(), o.getContent(), canSeeAnswers ? o.isCorrect() : null)
        ).toList();

        return new AssignmentDtos.QuestionView(
                q.getId(), q.getQuestionType(), q.getTitle(), q.getPrompt(), q.getPoints(),
                q.getDisplayOrder(), q.getGradingPolicy(), q.getEssayFormat(), q.getCodeTemplate(),
                q.getCodeLanguage(), optViews, canSeeAnswers ? q.getExplanation() : null
        );
    }

    private AssignmentDtos.AttemptView toAttemptView(AssignmentAttemptEntity att, AssignmentEntity a, boolean isOwner) {
        Long remainingSeconds = null;
        if (a.getDurationMinutes() != null && a.getDurationMinutes() > 0 && att.getStatus() == AttemptStatus.IN_PROGRESS) {
            long totalSeconds = a.getDurationMinutes() * 60L;
            long elapsed = Duration.between(att.getStartedAt(), clock.instant()).toSeconds();
            remainingSeconds = Math.max(0, totalSeconds - elapsed);
        }

        boolean showGrades = isOwner || a.isGradePublished();
        var ansViews = answers.findByAttemptId(att.getId()).stream()
                .map(ans -> toAnswerView(ans, showGrades)).toList();

        String studentName = att.getStudent().getFullName() != null
                ? att.getStudent().getFullName()
                : att.getStudent().getUsername();

        return new AssignmentDtos.AttemptView(
                att.getId(), a.getId(), att.getStudent().getId(), studentName, att.getAttemptNumber(),
                att.getStatus(), att.getStartedAt(), att.getSubmittedAt(), att.isLate(),
                showGrades ? att.getAutoScore() : null, showGrades ? att.getManualScore() : null,
                showGrades ? att.getOverriddenScore() : null, showGrades ? att.getFinalScore() : null,
                showGrades ? att.getOverrideReason() : null, showGrades ? att.getTeacherFeedback() : null,
                remainingSeconds, ansViews
        );
    }

    private AssignmentDtos.AnswerView toAnswerView(AssignmentAnswerEntity ans) {
        return toAnswerView(ans, true);
    }

    private AssignmentDtos.AnswerView toAnswerView(AssignmentAnswerEntity ans, boolean showGrades) {
        return new AssignmentDtos.AnswerView(
                ans.getId(), ans.getQuestion().getId(), ans.getSelectedOptionIds(), ans.getTextAnswer(),
                ans.getFileUrl(), ans.getCodeSubmission(), ans.isDraft(),
                showGrades ? ans.getScore() : null, showGrades ? ans.getFeedback() : null
        );
    }

    private void requireAttemptScope(AssignmentAttemptEntity attempt, UUID classroomId, UUID assignmentId) {
        var assignment = attempt.getAssignment();
        if (!assignment.getId().equals(assignmentId) || !assignment.getClassroom().getId().equals(classroomId)) {
            throw new ResourceNotFoundException("Attempt not found");
        }
    }

    private static final class TimeLimitExceededException extends ApiException {
        private TimeLimitExceededException() {
            super(ErrorCode.TIME_LIMIT_EXCEEDED);
        }

        private TimeLimitExceededException(String message) {
            super(ErrorCode.TIME_LIMIT_EXCEEDED, message);
        }
    }
}
