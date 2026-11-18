package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.notification.NotificationService;
import com.algolearner.platform.lesson.LessonLinkedItemRepository;
import com.algolearner.platform.lesson.LessonStatus;
import com.algolearner.platform.lesson.LinkedItemType;
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
    private final LessonLinkedItemRepository linkedItems;
    private final Clock clock;

    private final AssignmentRevisionRepository revisions;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AssignmentParticipationPolicy participation;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AssignmentEvaluationHistory evaluations;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.algolearner.platform.storage.ManagedFileService managedFiles;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher events;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AssignmentQuestionAuthoring authoring;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AssignmentCodeJudge codeJudge;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private AssignmentLessonProgressProvider lessonProgress;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.algolearner.platform.classroom.ClassroomService classroomService;
    private final AssignmentGradingAccess gradingAccess;
    private final com.algolearner.platform.audit.AuditService audit;

    public AssignmentService(AssignmentRepository assignments, AssignmentQuestionRepository questions,
            AssignmentQuestionOptionRepository options, AssignmentAttemptRepository attempts, AssignmentAnswerRepository answers,
            AssignmentRejudgeLogRepository rejudgeLogs, ClassroomRepository classrooms, ClassroomMembershipRepository memberships,
            AccountRepository accounts, NotificationService notifications, LessonLinkedItemRepository linkedItems, Clock clock) {
        this(assignments, questions, options, attempts, answers, rejudgeLogs, classrooms, memberships, accounts,
                notifications, linkedItems, clock, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AssignmentService(AssignmentRepository assignments, AssignmentQuestionRepository questions,
            AssignmentQuestionOptionRepository options, AssignmentAttemptRepository attempts, AssignmentAnswerRepository answers,
            AssignmentRejudgeLogRepository rejudgeLogs, ClassroomRepository classrooms, ClassroomMembershipRepository memberships,
            AccountRepository accounts, NotificationService notifications, LessonLinkedItemRepository linkedItems, Clock clock,
            AssignmentRevisionRepository revisions, AssignmentGradingAccess gradingAccess,
            com.algolearner.platform.audit.AuditService audit) {
        this.assignments = assignments; this.questions = questions; this.options = options; this.attempts = attempts;
        this.answers = answers; this.rejudgeLogs = rejudgeLogs; this.classrooms = classrooms; this.memberships = memberships;
        this.accounts = accounts; this.notifications = notifications; this.linkedItems = linkedItems; this.clock = clock;
        this.revisions = revisions; this.gradingAccess=gradingAccess; this.audit=audit;
        this.authoring = new AssignmentQuestionAuthoring(questions,options,null,com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());
    }

    public List<AssignmentDtos.AssignmentSummaryView> list(UUID accountId, UUID classroomId) {
        var classroom = requireClassroom(classroomId);
        boolean isOwner = classroom.getOwner().getId().equals(accountId);

        List<AssignmentEntity> list;
        if (isOwner) {
            list = assignments.findByClassroomIdOrderByCreatedAtDesc(classroomId);
        } else {
            requireActiveStudent(classroomId, accountId);
            list = assignments.findByClassroomIdAndStatusInOrderByCreatedAtDesc(
                    classroomId, List.of(AssignmentStatus.SCHEDULED, AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)
            );
        }

        return list.stream().map(a -> toSummaryView(a, accountId, isOwner)).toList();
    }

    @Transactional
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
            requireStarted(assignment);
            if (assignment.getStatus() == AssignmentStatus.SCHEDULED) {
                assignment = lockedAssignment(assignmentId, classroomId);
                activateDueAssignment(assignment);
            }
        }

        return toDetailView(assignment, accountId, isOwner);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView create(UUID accountId, UUID classroomId, AssignmentDtos.CreateAssignmentRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        AssignmentConfigurationPolicy.validate(request.startTime(), request.deadline(), request.durationMinutes(),
                request.maxAttempts(), request.latePenaltyPercent(), request.gradePublishMode());
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

        var assignment = lockedAssignment(assignmentId, classroomId);

        AssignmentConfigurationPolicy.validate(
                request.startTime(), request.deadline(),
                request.durationMinutes() == null ? assignment.getDurationMinutes() : request.durationMinutes(),
                request.maxAttempts() == null ? assignment.getMaxAttempts() : request.maxAttempts(),
                request.latePenaltyPercent(),
                request.gradePublishMode() == null ? assignment.getGradePublishMode() : request.gradePublishMode());
        boolean changesTimer = request.durationMinutes() != null
                && !Objects.equals(request.durationMinutes(), assignment.getDurationMinutes());
        boolean changesTimeoutAction = request.timeExpiredAction() != null
                && request.timeExpiredAction() != assignment.getTimeExpiredAction();
        if ((changesTimer || changesTimeoutAction) && attempts.existsByAssignmentId(assignmentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION,
                    "Cannot change the timer or timeout policy after students have started");
        }

        if (!Objects.equals(request.deadline(),assignment.getDeadline()) && attempts.existsByAssignmentId(assignmentId)
                && (request.deadline()!=null && !request.deadline().isAfter(clock.instant())
                    || assignment.getDeadline()!=null && !assignment.getDeadline().isAfter(clock.instant())))
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Cannot change an elapsed submission cutoff.");
        if (request.allowLateSubmission() != null && request.allowLateSubmission() != assignment.isAllowLateSubmission()
                && assignment.getDeadline() != null && !assignment.getDeadline().isAfter(clock.instant())
                && attempts.existsByAssignmentId(assignmentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Cannot change the late policy after an elapsed submission cutoff.");
        }
        assignment.setTitle(request.title().trim());
        if (request.description() != null) assignment.setDescription(request.description());
        if (request.type() != null) assignment.setType(request.type());
        assignment.setStartTime(request.startTime());
        assignment.setDeadline(request.deadline());
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

        var assignment = lockedAssignment(assignmentId, classroomId);

        if (attempts.existsByAssignmentId(assignmentId) || assignment.getPublishedRevisionId()!=null) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Published revisions and student history must be preserved; close this assignment instead.");
        }

        assignments.delete(assignment);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView publish(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = lockedAssignment(assignmentId, classroomId);

        AssignmentConfigurationPolicy.validate(assignment.getStartTime(), assignment.getDeadline(),
                assignment.getDurationMinutes(), assignment.getMaxAttempts(), assignment.getLatePenaltyPercent(),
                assignment.getGradePublishMode());
        var authored=questions.findByAssignmentIdOrderByDisplayOrderAsc(assignmentId);
        if(authored.stream().anyMatch(q->q.getPoints().signum()>0)
                && authored.stream().filter(q->q.getPoints().signum()>0).map(AssignmentQuestionEntity::getQuestionWeight)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add).signum()==0)
            throw new ApiException(ErrorCode.VALIDATION_FAILED,"Positive-point questions require a positive total weight.");
        if (revisions != null) {
            var revision = revisions.save(new AssignmentRevisionEntity(assignment, captureSnapshot(assignment)));
            assignment.setPublishedRevisionId(revision.getId());
        }
        assignment.setStatus(assignment.getStartTime() != null && clock.instant().isBefore(assignment.getStartTime())
                ? AssignmentStatus.SCHEDULED : AssignmentStatus.PUBLISHED);
        var saved = assignments.save(assignment);
        if (saved.getStatus() == AssignmentStatus.PUBLISHED) notifyPublished(saved);
        return toDetailView(saved, accountId, true);
    }

    private void notifyPublished(AssignmentEntity saved) {
        UUID classroomId = saved.getClassroom().getId();
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

    }

    @Transactional
    public void activateScheduledAssignment(UUID assignmentId) {
        var readable = assignments.findById(assignmentId).orElse(null);
        if (readable == null) return;
        var assignment = lockedAssignment(assignmentId, readable.getClassroom().getId());
        activateDueAssignment(assignment);
    }

    private void activateDueAssignment(AssignmentEntity assignment) {
        if (assignment.getStatus() != AssignmentStatus.SCHEDULED || assignment.getStartTime() == null
                || clock.instant().isBefore(assignment.getStartTime())
                || assignment.getClassroom().getStatus() != ClassroomStatus.ACTIVE
                || assignment.getClassroom().getOwner().getStatus() != AccountStatus.ACTIVE) return;
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignments.saveAndFlush(assignment);
        notifyPublished(assignment);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView close(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = lockedAssignment(assignmentId, classroomId);

        assignment.setStatus(AssignmentStatus.CLOSED);
        return toDetailView(assignments.save(assignment), accountId, true);
    }

    @Transactional
    public void publishGrades(UUID accountId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = lockedAssignment(assignmentId, classroomId);

        if (evaluations != null) {
            for (var attempt : attempts.findByAssignmentIdOrderByCreatedAtDesc(assignmentId)) {
                if (attempt.getSubmittedAt() != null) {
                    evaluations.publish(attempt,accountId);
                    progressChanged(attempt);
                }
            }
        }
        assignment.setGradePublished(true);
        assignments.save(assignment);
    }

    @Transactional
    public AssignmentDtos.AssignmentDetailView cloneAssignment(UUID accountId, UUID classroomId, UUID assignmentId, UUID targetClassroomId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var targetClassroom = requireClassroom(targetClassroomId);
        requireOwner(targetClassroom, accountId);

        if(classroomService!=null) {
            var ordered=new ArrayList<>(new HashSet<>(List.of(classroomId,targetClassroomId)));ordered.sort(UUID::compareTo);
            for(var id:ordered)classroomService.lockReadable(id);
        }
        var src = lockedAssignment(assignmentId,classroomId);

        var copy = AssignmentEntity.builder()
                .id(UUID.randomUUID())
                .classroom(targetClassroom)
                .title(src.getTitle() + " (Copy)")
                .description(src.getDescription())
                .type(src.getType())
                .status(AssignmentStatus.DRAFT)
                .startTime(src.getStartTime())
                .deadline(src.getDeadline())
                .allowLateSubmission(src.isAllowLateSubmission())
                .latePenaltyPercent(BigDecimal.ZERO)
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
                    .codeContentSnapshot(sq.getCodeContentSnapshot()).questionWeight(sq.getQuestionWeight())
                    .codeCaseWeights(sq.getCodeCaseWeights()==null?null:List.copyOf(sq.getCodeCaseWeights()))
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

        var assignment = lockedAssignment(assignmentId, classroomId);

        return toQuestionView(authoring.save(accountId, assignment, null, request), true, true);
    }

    @Transactional
    public AssignmentDtos.QuestionView updateQuestion(UUID teacher, UUID classId, UUID assignmentId, UUID questionId,
            AssignmentDtos.AddQuestionRequest request) {
        requireOwner(requireClassroom(classId),teacher);
        var assignment=lockedAssignment(assignmentId,classId);
        var question=questions.findByIdAndAssignmentId(questionId,assignmentId).orElseThrow(()->new ApiException(ErrorCode.QUESTION_NOT_FOUND));
        if(question.isRetired())throw new ApiException(ErrorCode.QUESTION_NOT_FOUND);
        return toQuestionView(authoring.save(teacher,assignment,question,request),true,true);
    }

    @Transactional
    public void reorderQuestions(UUID teacher, UUID classId, UUID assignmentId, List<UUID> ids) {
        requireOwner(requireClassroom(classId),teacher);lockedAssignment(assignmentId,classId);
        var all=questions.findByAssignmentIdOrderByDisplayOrderAsc(assignmentId);
        if(ids==null || ids.size()!=all.size() || new HashSet<>(ids).size()!=all.size()
                || !new HashSet<>(ids).equals(all.stream().map(AssignmentQuestionEntity::getId).collect(java.util.stream.Collectors.toSet())))
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        for(var q:all){q.setDisplayOrder(ids.indexOf(q.getId()));questions.save(q);}
    }

    @Transactional
    public void deleteQuestion(UUID accountId, UUID classroomId, UUID assignmentId, UUID questionId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var assignment = lockedAssignment(assignmentId, classroomId);
        var q = questions.findByIdAndAssignmentId(questionId, assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));

        q.setRetired(true);
        questions.save(q);
    }

    // --- Student Taking Assignment (Attempts, Draft, Submit) ---
    @Transactional(noRollbackFor = TimeLimitExceededException.class)
    public AssignmentDtos.AttemptView startAttempt(UUID studentId, UUID classroomId, UUID assignmentId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var assignment = lockedAssignment(assignmentId, classroomId);
        var now = clock.instant();

        // Check if an in-progress attempt already exists
        var existing = attempts.findFirstByAssignmentIdAndStudentIdAndStatus(assignmentId, studentId, AttemptStatus.IN_PROGRESS);
        if (existing.isPresent()) {
            requireWritable(assignment, effectiveSubmissionTime(existing.get()));
            if (isTimeExpired(existing.get(), assignment)) {
                finishExpiredAttempt(existing.get());
            }
            return toAttemptView(existing.get(), assignment, false);
        }

        requireWritable(assignment, now);
        if (!assignment.isAllowLateSubmission() && assignment.getDeadline() != null && !now.isBefore(assignment.getDeadline()))
            throw new ApiException(ErrorCode.ASSIGNMENT_CLOSED);

        var history = attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignmentId, studentId);
        var allowance = participation == null ? new AssignmentParticipationPolicy.Allowance(0, 0, Instant.EPOCH)
                : participation.allowance(assignmentId, studentId);
        if (history.stream().anyMatch(a -> a.getStatus() == AttemptStatus.TIMED_OUT
                && !a.getStartedAt().isBefore(allowance.unlockedAt()))) {
            throw new ApiException(ErrorCode.ATTEMPT_LIMIT_EXCEEDED, "Teacher must grant another attempt after a locked timeout.");
        }
        long pastAttempts = history.stream().filter(a -> a.getSubmittedAt() != null).count();
        if (assignment.getMaxAttempts() > 0 && pastAttempts - allowance.baseline() >= (long) assignment.getMaxAttempts() + allowance.extra()) {
            throw new ApiException(ErrorCode.ATTEMPT_LIMIT_EXCEEDED);
        }

        var student = accounts.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .attemptNumber(history.stream().mapToInt(AssignmentAttemptEntity::getAttemptNumber).max().orElse(0) + 1)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(now)
                .build();

        String snapshot;
        if (revisions != null && assignment.getPublishedRevisionId() != null) {
            var revision = revisions.findById(assignment.getPublishedRevisionId())
                    .orElseThrow(() -> new IllegalStateException("Published assignment revision is missing"));
            snapshot = revision.getContent();
            attempt.setRevisionId(revision.getId());
        } else {
            snapshot = captureSnapshot(assignment);
            if (revisions != null) {
                var revision = revisions.save(new AssignmentRevisionEntity(assignment, snapshot));
                assignment.setPublishedRevisionId(revision.getId()); attempt.setRevisionId(revision.getId());
            }
        }
        snapshot = AssignmentSnapshotPolicy.forAttempt(snapshot);
        attempt.setRevisionSnapshot(snapshot);
        attempt.setSnapshotProvenance("TRUSTED");
        var duration = AssignmentSnapshotPolicy.decode(snapshot).durationMinutes();
        if (duration != null && duration > 0) attempt.setBaseExpiresAt(now.plusSeconds(duration * 60L));
        var savedAttempt = attempts.save(attempt);
        return toAttemptView(savedAttempt, assignment, false);
    }

    @Transactional
    public UUID runCode(UUID student, UUID classId, UUID assignmentId, UUID attemptId, UUID questionId, String source) {
        requireClassroom(classId); requireActiveStudent(classId,student);
        var assignment=lockedAssignment(assignmentId,classId);
        var attempt=attempts.findForUpdateById(attemptId).orElseThrow(()->new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt,classId,assignmentId);
        if(!attempt.getStudent().getId().equals(student) || attempt.getStatus()!=AttemptStatus.IN_PROGRESS)
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        requireWritable(assignment,effectiveSubmissionTime(attempt));
        if(isTimeExpired(attempt,assignment))throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        var question=frozenQuestion(attempt,questionId);
        if(question==null || question.type()!=QuestionType.CODE || source==null || source.isBlank())throw new ApiException(ErrorCode.VALIDATION_FAILED);
        return codeJudge.evaluate(attempt,question,source,true);
    }

    @Transactional
    public void grantAttempts(UUID teacherId, UUID classroomId, UUID assignmentId, UUID studentId,
                              AssignmentDtos.AttemptGrantRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);
        lockedAssignment(assignmentId, classroomId);
        requireActiveStudent(classroomId, studentId);
        var history = attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignmentId, studentId);
        long count = history.stream().filter(a -> a.getSubmittedAt() != null).count();
        participation.grant(teacherId, assignmentId, studentId, request.extraAttempts(), request.reset(), request.reason(), count);
    }

    @Transactional
    public AssignmentDtos.AttemptView getActiveAttempt(UUID studentId, UUID classroomId, UUID assignmentId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var assignment = lockedAssignment(assignmentId, classroomId);

        var attempt = attempts.findFirstByAssignmentIdAndStudentIdAndStatus(assignmentId, studentId, AttemptStatus.IN_PROGRESS)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_ACTIVE_ATTEMPT));

        if (isTimeExpired(attempt, assignment)) {
            finishExpiredAttempt(attempt);
        }

        return toAttemptView(attempt, assignment, false);
    }

    public List<AssignmentDtos.AttemptView> ownOfficialAttempts(UUID studentId, UUID classroomId, UUID assignmentId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);
        var assignment = readableStudentAssignment(classroomId, assignmentId);
        return attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignmentId, studentId).stream()
                .filter(a -> a.getSubmittedAt() != null && (a.getStatus() == AttemptStatus.SUBMITTED || a.getStatus() == AttemptStatus.GRADED))
                .map(a -> toAttemptView(a, assignment, false)).toList();
    }

    public AssignmentDtos.AttemptView ownOfficialAttempt(UUID studentId, UUID classroomId, UUID assignmentId, UUID attemptId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);
        var assignment = readableStudentAssignment(classroomId, assignmentId);
        var attempt = ownAttempt(studentId, classroomId, assignmentId, attemptId);
        requireOfficialAttempt(attempt);
        return toAttemptView(attempt, assignment, false);
    }

    public com.algolearner.platform.judge.JudgeGateway.EvaluationResult codeResult(UUID studentId, UUID classroomId,
            UUID assignmentId, UUID attemptId, UUID jobId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);
        var assignment = readableStudentAssignment(classroomId, assignmentId);
        var attempt = ownAttempt(studentId, classroomId, assignmentId, attemptId);
        var job = codeJudge.reference(studentId, jobId);
        if (!studentId.equals(job.ownerId()) || !attemptId.equals(job.attemptId())
                || job.scope() != com.algolearner.platform.judge.JudgeGateway.SubmissionScope.CLASS_ASSIGNMENT
                || !assignmentId.equals(job.resourceId())
                || frozenQuestion(attempt, job.questionId()) == null) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        if (job.run()) {
            if (!Objects.equals(attempt.getRevisionId(), job.revisionId())) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        } else {
            var published = evaluations == null || attempt.getPublishedEvaluationId() == null ? null
                    : evaluations.published(attemptId, attempt.getPublishedEvaluationId());
            var part = published == null ? null : evaluations.parts(published).stream()
                    .filter(value -> jobId.equals(value.judgeJobId()) && job.questionId().equals(value.questionId()))
                    .findFirst().orElse(null);
            if (part == null || !Objects.equals(part.judgeRevisionId(), job.revisionId()))
                throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Only published code evaluation results are visible.");
            requireRevision(assignment, job.revisionId());
        }
        return codeJudge.result(studentId, jobId, job.run() ? CodeFeedbackLevel.FULL_DETAILS : assignment.getCodeFeedbackLevel());
    }

    private AssignmentAttemptEntity ownAttempt(UUID studentId, UUID classroomId, UUID assignmentId, UUID attemptId) {
        var attempt = attempts.findById(attemptId).orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);
        if (!attempt.getStudent().getId().equals(studentId)) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        return attempt;
    }

    private AssignmentEntity readableStudentAssignment(UUID classroomId, UUID assignmentId) {
        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        if (assignment.getStatus() == AssignmentStatus.DRAFT) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        requireStarted(assignment);
        return assignment;
    }

    @Transactional(noRollbackFor = TimeLimitExceededException.class)
    public AssignmentDtos.AnswerView saveDraftAnswer(UUID studentId, UUID classroomId, UUID assignmentId, UUID attemptId, AssignmentDtos.SaveDraftAnswerRequest request) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var assignment = lockedAssignment(assignmentId, classroomId);
        var attempt = attempts.findForUpdateById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);

        if (!attempt.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.ATTEMPT_ALREADY_SUBMITTED);
        }

        // Verify time limit
        requireWritable(assignment, effectiveSubmissionTime(attempt));
        if (isTimeExpired(attempt, assignment)) {
            finishExpiredAttempt(attempt);
            throw new TimeLimitExceededException();
        }

        var question = questions.findByIdAndAssignmentId(request.questionId(), assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));

        var answer = answers.findByAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseGet(() -> AssignmentAnswerEntity.builder()
                        .id(UUID.randomUUID())
                        .attempt(attempt)
                        .question(question)
                        .build());

        if (request.clientMutationId() != null && request.clientMutationId().equals(answer.getClientMutationId()))
            return toAnswerView(answer, false);
        if (request.expectedVersion() != null && request.expectedVersion() != answer.getAnswerVersion())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Answer version conflict; reload the saved answer.");
        if (request.fileUrl() != null && !request.fileUrl().isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Use a managed fileId.");
        var frozen = frozenQuestion(attempt, request.questionId());
        var type = frozen == null ? question.getQuestionType() : frozen.type();
        if (request.fileId() != null) {
            if (type != QuestionType.ESSAY || managedFiles == null) throw new ApiException(ErrorCode.VALIDATION_FAILED);
            managedFiles.requireReady(studentId, classroomId, request.fileId(), com.algolearner.platform.storage.FilePurpose.ESSAY_SUBMISSION);
        }
        String selected = AssignmentAnswerPolicy.selection(request,frozen);
        answer.setSelectedOptionIds(selected);
        answer.setStoredFileId(request.fileId());
        answer.setAnswerVersion(answer.getAnswerVersion() + 1);
        answer.setClientMutationId(request.clientMutationId());
        answer.setTextAnswer(request.textAnswer());
        answer.setFileUrl(request.fileUrl());
        answer.setCodeSubmission(request.codeSubmission());
        answer.setDraft(true);
        answer.setLastSavedAt(clock.instant());

        var saved = answers.save(answer);
        return toAnswerView(saved, false);
    }

    @Transactional(noRollbackFor = TimeLimitExceededException.class)
    public AssignmentDtos.AttemptView submitAttempt(UUID studentId, UUID classroomId, UUID assignmentId, UUID attemptId) {
        requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var assignment = lockedAssignment(assignmentId, classroomId);
        var attempt = attempts.findForUpdateById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);

        if (!attempt.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }

        if (attempt.getSubmittedAt() != null && (attempt.getStatus() == AttemptStatus.SUBMITTED || attempt.getStatus() == AttemptStatus.GRADED))
            return toAttemptView(attempt, assignment, false);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.ATTEMPT_ALREADY_SUBMITTED);
        }

        if (assignment.getMaxAttempts()>0) {
            long official=attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignmentId,studentId).stream()
                    .filter(t->t.getSubmittedAt()!=null).count();
            var allowance=participation==null?new AssignmentParticipationPolicy.Allowance(0,0,Instant.EPOCH):participation.allowance(assignmentId,studentId);
            if(official-allowance.baseline()>=(long)assignment.getMaxAttempts()+allowance.extra())throw new ApiException(ErrorCode.ATTEMPT_LIMIT_EXCEEDED);
        }
        var submittedAt = effectiveSubmissionTime(attempt);
        requireWritable(assignment, submittedAt);
        if (isTimeExpired(attempt, assignment)) {
            finishExpiredAttempt(attempt);
            if (attempt.getStatus() == AttemptStatus.TIMED_OUT) {
                throw new TimeLimitExceededException();
            }
            return toAttemptView(attempt, assignment, false);
        }

        finalizeAttempt(attempt, submittedAt);
        return toAttemptView(attempt, assignment, false);
    }

    private void finalizeAttempt(AssignmentAttemptEntity attempt, Instant submittedAt) {
        var assignment = attempt.getAssignment();
        boolean isLate = assignment.getDeadline() != null && submittedAt.isAfter(assignment.getDeadline());

        attempt.setSubmittedAt(submittedAt);
        attempt.setLate(isLate);

        // Grade objective questions automatically
        var attemptAnswers = answers.findByAttemptId(attempt.getId());
        BigDecimal autoScore = BigDecimal.ZERO;
        boolean hasPendingManualGrading = false;

        for (var ans : attemptAnswers) {
            ans.setDraft(false);
            var q = ans.getQuestion();
            var frozen = frozenQuestion(attempt, q.getId());
            if (frozen != null && (frozen.type() == QuestionType.ESSAY || frozen.type() == QuestionType.CODE)) {
                ans.setScore(null); hasPendingManualGrading = true;
                if (frozen.type() == QuestionType.CODE && codeJudge != null && ans.getCodeSubmission() != null
                        && !ans.getCodeSubmission().isBlank()) codeJudge.evaluate(attempt, frozen, ans.getCodeSubmission(), false);
                answers.save(ans); continue;
            }
            if (frozen != null && (frozen.type() == QuestionType.SINGLE_CHOICE || frozen.type() == QuestionType.TRUE_FALSE
                    || frozen.type() == QuestionType.MULTIPLE_CHOICE)) {
                var score = ObjectiveQuestionGrader.grade(frozen.type(), frozen.policy(), frozen.points(),
                        frozen.gradingOptions(), ans.getSelectedOptionIds());
                ans.setScore(score); autoScore = autoScore.add(score); answers.save(ans); continue;
            }

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

        var expected = attempt.getRevisionSnapshot() == null ? questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId()).stream()
                .map(q -> q.getQuestionType()).toList() : AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).questions().stream()
                .map(AssignmentSnapshotPolicy.Question::type).toList();
        if (expected.stream().anyMatch(type -> type == QuestionType.ESSAY || type == QuestionType.CODE)) hasPendingManualGrading = true;
        attempt.setAutoScore(autoScore);

        if (!hasPendingManualGrading) {
            BigDecimal finalScore = autoScore;
            attempt.setFinalScore(finalScore);
            attempt.setStatus(AttemptStatus.GRADED);
        } else {
            attempt.setStatus(AttemptStatus.SUBMITTED);
        }

        if(attempt.getRevisionSnapshot()!=null) recalculateFinalScore(attempt);
        recordEvaluation(attempt, null, "AUTO", null);
        attempts.save(attempt);
        progressChanged(attempt);
    }

    // --- Grading & Override & Rejudge (Teacher) ---
    @Transactional
    public void gradeEssay(UUID teacherId, UUID classroomId, UUID assignmentId, UUID attemptId, AssignmentDtos.GradeEssayRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);

        lockedAssignment(assignmentId,classroomId);
        var attempt = attempts.findForUpdateById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);
        requireOfficialAttempt(attempt);

        var question = questions.findByIdAndAssignmentId(request.questionId(), assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));
        var frozen = frozenQuestion(attempt, question.getId());
        if ((frozen == null ? question.getQuestionType() : frozen.type()) != QuestionType.ESSAY) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }

        var answer = answers.findByAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseGet(() -> AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt).question(question)
                        .isDraft(false).lastSavedAt(clock.instant()).build());

        var maximum = frozen == null ? question.getPoints() : frozen.points();
        if (request.score() == null || request.score().signum() < 0 || maximum == null || request.score().compareTo(maximum) > 0)
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Score must be between zero and the question maximum.");
        answer.setScore(request.score());
        answer.setFeedback(request.feedback());
        answers.save(answer);

        recalculateFinalScore(attempt);
        recordEvaluation(attempt, teacherId, "MANUAL", null);
    }

    @Transactional
    public void overrideGrade(UUID teacherId, UUID classroomId, UUID assignmentId, UUID attemptId, AssignmentDtos.OverrideGradeRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);

        lockedAssignment(assignmentId,classroomId);
        var attempt = attempts.findForUpdateById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt, classroomId, assignmentId);
        requireOfficialAttempt(attempt);

        if (request.overriddenScore() == null || request.overriddenScore().signum() < 0
                || request.reason() == null || request.reason().isBlank()) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        attempt.setOverriddenScore(request.overriddenScore());
        attempt.setOverrideReason(request.reason());
        recalculateFinalScore(attempt);
        recordEvaluation(attempt, teacherId, "OVERRIDE", request.reason());
        attempts.save(attempt);
    }

    @Transactional
    public void rejudge(UUID teacherId, UUID classroomId, UUID assignmentId, AssignmentDtos.RejudgeRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, teacherId);

        var assignment = lockedAssignment(assignmentId,classroomId);

        var teacher = accounts.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));

        rejudgeAuthorized(teacher,assignment,request);
    }

    @Transactional
    public void adminRejudge(UUID admin,UUID classId,UUID assignmentId,AssignmentDtos.RejudgeRequest request) {
        var actor=gradingAccess.requireAdmin(admin,AssignmentGradingAccess.Permission.REJUDGE);
        var reason=AssignmentGradingAccess.reason(request==null?null:request.reason());
        requireWritableClassroom(requireClassroom(classId));
        var assignment=lockedAssignment(assignmentId,classId);
        rejudgeAuthorized(actor,assignment,new AssignmentDtos.RejudgeRequest(reason,request.target()));
    }

    private void rejudgeAuthorized(AccountEntity actor,AssignmentEntity assignment,AssignmentDtos.RejudgeRequest request) {
        var assignmentId=assignment.getId();var actorId=actor.getId();
        var target = request == null ? AssignmentDtos.RejudgeTarget.ORIGINAL : request.target();
        var latestRevision = target == AssignmentDtos.RejudgeTarget.LATEST_PUBLISHED
                ? requireRevision(assignment, assignment.getPublishedRevisionId()) : null;
        var latestContent = latestRevision == null ? null : AssignmentSnapshotPolicy.decode(latestRevision.getContent());
        var allAttempts = attempts.findByAssignmentIdOrderByCreatedAtDesc(assignmentId);
        for (var att : allAttempts) {
            if (att.getSubmittedAt()==null || att.getStatus() != AttemptStatus.SUBMITTED && att.getStatus() != AttemptStatus.GRADED) continue;

            if (att.getRevisionSnapshot() == null || !"TRUSTED".equals(att.getSnapshotProvenance()))
                throw new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Original snapshot unavailable; historical content cannot be rejudged.");
            BigDecimal oldScore = att.getFinalScore();
            BigDecimal auto=BigDecimal.ZERO;
            for(var answer:answers.findByAttemptId(att.getId())) {
                var q=frozenQuestion(att,answer.getQuestion().getId());
                if(q.type()==QuestionType.CODE) {
                    answer.setScore(null);
                    if(codeJudge!=null && answer.getCodeSubmission()!=null) {
                        var context = latestContent == null ? AssignmentSnapshotPolicy.decode(att.getRevisionSnapshot()) : latestContent;
                        var targetQuestion = context.questions().stream().filter(value -> value.id().equals(q.id()) && value.type()==QuestionType.CODE)
                                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Selected published revision does not contain the original code question."));
                        codeJudge.rejudge(att,q,targetQuestion,latestRevision==null?att.getRevisionId():latestRevision.getId(),
                                context.codeFeedbackLevel(),answer.getCodeSubmission());
                    }
                } else if(q.type()!=QuestionType.ESSAY) {
                    var score=ObjectiveQuestionGrader.grade(q.type(),q.policy(),q.points(),q.gradingOptions(),answer.getSelectedOptionIds());
                    answer.setScore(score);auto=auto.add(score);
                }
                answers.save(answer);
            }
            att.setAutoScore(auto);
            recalculateFinalScore(att);
            recordEvaluation(att,actorId,"REJUDGE",request==null?null:request.reason());
            BigDecimal newScore = att.getFinalScore();

            var log = AssignmentRejudgeLogEntity.builder()
                    .id(UUID.randomUUID())
                    .assignment(assignment)
                    .attempt(att)
                    .performedBy(actor)
                    .oldScore(oldScore)
                    .newScore(newScore)
                    .reason(request != null ? request.reason() : "Automated rejudge")
                    .createdAt(clock.instant())
                    .build();
            rejudgeLogs.save(log);

        }
    }

    private AssignmentRevisionEntity requireRevision(AssignmentEntity assignment, UUID revisionId) {
        if (revisionId == null || revisions == null) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Published testcase revision is unavailable.");
        return revisions.findById(revisionId).filter(value -> value.getAssignment().getId().equals(assignment.getId())
                && "TRUSTED".equals(value.getProvenance()))
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Testcase revision does not belong to this assignment."));
    }

    public List<AssignmentEvaluationHistory.Evaluation> evaluationHistory(UUID teacher,UUID classId,UUID assignmentId,UUID attemptId) {
        requireOwnerForRead(requireClassroom(classId),teacher);
        return scopedEvaluationHistory(classId,assignmentId,attemptId);
    }

    public List<AssignmentEvaluationHistory.Evaluation> adminEvaluationHistory(UUID admin,UUID classId,UUID assignmentId,UUID attemptId) {
        gradingAccess.requireAdmin(admin,AssignmentGradingAccess.Permission.READ_HISTORY);
        requireClassroom(classId);
        return scopedEvaluationHistory(classId,assignmentId,attemptId);
    }

    private List<AssignmentEvaluationHistory.Evaluation> scopedEvaluationHistory(UUID classId,UUID assignmentId,UUID attemptId) {
        var attempt=attempts.findById(attemptId).orElseThrow(()->new ResourceNotFoundException("Attempt not found"));
        requireAttemptScope(attempt,classId,assignmentId);requireOfficialAttempt(attempt);
        return evaluations.history(attemptId);
    }

    public List<AssignmentDtos.AttemptView> getSubmissions(UUID teacherId, UUID classroomId, UUID assignmentId) {
        var classroom = requireClassroom(classroomId);
        requireOwnerForRead(classroom, teacherId);

        var assignment = assignments.findByIdAndClassroomId(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        var list = attempts.findByAssignmentIdOrderByCreatedAtDesc(assignmentId);
        return list.stream().filter(a -> a.getSubmittedAt() != null).map(a -> toAttemptView(a, assignment, true)).toList();
    }

    @Transactional
    public AssignmentDtos.CodeCaseWeightsView codeCaseWeights(UUID teacher,UUID classId,UUID assignmentId,UUID questionId,
            AssignmentDtos.CodeCaseWeightsRequest request) {
        requireOwner(requireClassroom(classId),teacher);
        return applyCodeCaseWeights(teacher,"TEACHER",lockedAssignment(assignmentId,classId),questionId,request);
    }

    @Transactional
    public AssignmentDtos.CodeCaseWeightsView adminCodeCaseWeights(UUID admin,UUID classId,UUID assignmentId,UUID questionId,
            AssignmentDtos.CodeCaseWeightsRequest request) {
        gradingAccess.requireAdmin(admin,AssignmentGradingAccess.Permission.CHANGE_CODE_CASE_WEIGHTS);
        requireWritableClassroom(requireClassroom(classId));
        return applyCodeCaseWeights(admin,"ADMIN",lockedAssignment(assignmentId,classId),questionId,request);
    }

    private AssignmentDtos.CodeCaseWeightsView applyCodeCaseWeights(UUID actor,String role,AssignmentEntity assignment,UUID questionId,
            AssignmentDtos.CodeCaseWeightsRequest request) {
        var reason=AssignmentGradingAccess.reason(request==null?null:request.reason());
        var question=questions.findByIdAndAssignmentId(questionId,assignment.getId())
                .filter(q->!q.isRetired()).orElseThrow(()->new ApiException(ErrorCode.QUESTION_NOT_FOUND));
        var content=AssignmentSnapshotPolicy.decodeCode(question.getCodeContentSnapshot());
        var weights=request.weights();
        if(question.getQuestionType()!=QuestionType.CODE || content==null || weights==null
                || weights.size()!=content.testCases().size() || weights.stream().anyMatch(w->w==null || w.signum()<0)
                || weights.stream().reduce(BigDecimal.ZERO,BigDecimal::add).signum()==0)
            throw new ApiException(ErrorCode.VALIDATION_FAILED,"Weights must match the original testcase count and have a positive total");
        var before=String.valueOf(question.getCodeCaseWeights());
        question.setCodeCaseWeights(List.copyOf(weights));questions.save(question);
        if(audit!=null)audit.record(actor,role,"ASSIGNMENT_QUESTION",questionId.toString(),"UPDATE_CODE_CASE_WEIGHTS",before,weights.toString(),reason,null);
        return new AssignmentDtos.CodeCaseWeightsView(questionId,weights);
    }

    @Transactional
    public void publishDueGrades(UUID assignmentId) {
        var found=assignments.findById(assignmentId).orElse(null);
        if(found==null)return;
        var assignment=lockedAssignment(assignmentId,found.getClassroom().getId());
        if(evaluations==null || assignment.getGradePublishMode()!=GradePublishMode.AFTER_DEADLINE
                || assignment.getDeadline()==null || clock.instant().isBefore(assignment.getDeadline()))return;
        for(var attempt:attempts.findByAssignmentIdOrderByCreatedAtDesc(assignmentId)) {
            if(attempt.getSubmittedAt()!=null && attempt.getStatus()==AttemptStatus.GRADED) {
                evaluations.publish(attempt);progressChanged(attempt);
            }
        }
        assignment.setGradePublished(true);
    }

    @Transactional
    public void refreshCodeResults(UUID attemptId) {
        var assignmentId=attempts.findAssignmentIdById(attemptId).orElse(null);
        if(assignmentId==null)return;
        var assignment=assignments.findById(assignmentId).orElse(null);
        if(assignment==null)return;
        lockedAssignment(assignmentId,assignment.getClassroom().getId());
        var attempt=attempts.findForUpdateById(attemptId).orElse(null);
        if(attempt==null || attempt.getSubmittedAt()==null || attempt.getRevisionSnapshot()==null)return;
        boolean changed=false;
        for(var result:codeJudge.completed(attempt)) {
            var answer=answers.findByAttemptIdAndQuestionId(attemptId,result.question()).orElse(null);
            if(answer==null)throw new IllegalStateException("Official code result has no assignment answer");
            answer.setScore(result.score());answers.save(answer);
            codeJudge.applied(attempt,result);changed=true;
        }
        if(changed){recalculateFinalScore(attempt);recordEvaluation(attempt,null,"AUTO",null);progressChanged(attempt);}
    }

    // --- Helper calculations ---
    @Transactional
    public void expireAttempt(UUID attemptId) {
        var assignmentId = attempts.findAssignmentIdById(attemptId).orElse(null);
        if (assignmentId == null) return;
        var readable = assignments.findById(assignmentId).orElse(null);
        if (readable != null && classroomService != null) classroomService.lockReadable(readable.getClassroom().getId());
        var assignment = assignments.findForUpdateById(assignmentId).orElse(null);
        if (assignment == null) return;
        var attempt = attempts.findForUpdateById(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != AttemptStatus.IN_PROGRESS
                || !isTimeExpired(attempt, assignment)) return;
        finishExpiredAttempt(attempt);
    }

    private void finishExpiredAttempt(AssignmentAttemptEntity attempt) {
        var assignment = attempt.getAssignment();
        boolean allowed = attempt.getStudent().getStatus() == AccountStatus.ACTIVE
                && memberships.findByClassroomIdAndStudentId(assignment.getClassroom().getId(), attempt.getStudent().getId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE).isPresent();
        if (allowed) {
            try {
                requireWritable(assignment, expiresAt(attempt));
            } catch (ApiException denied) {
                allowed = false;
            }
        }
        var timeoutAction = attempt.getRevisionSnapshot() == null ? assignment.getTimeExpiredAction()
                : AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).timeoutAction();
        if (allowed && timeoutAction == TimeExpiredAction.AUTO_SUBMIT) {
            finalizeAttempt(attempt, expiresAt(attempt));
        } else {
            attempt.setStatus(AttemptStatus.TIMED_OUT);
            attempts.save(attempt);
        }
    }

    private boolean isTimeExpired(AssignmentAttemptEntity attempt, AssignmentEntity assignment) {
        var cutoff = expiresAt(attempt);
        return cutoff != null && !clock.instant().isBefore(cutoff);
    }

    private Instant expiresAt(AssignmentAttemptEntity attempt) {
        var assignment = attempt.getAssignment();
        Instant cutoff = attempt.getBaseExpiresAt();
        if (cutoff == null && assignment.getDurationMinutes() != null && assignment.getDurationMinutes() > 0)
            cutoff = attempt.getStartedAt().plusSeconds(assignment.getDurationMinutes() * 60L);
        if (!assignment.isAllowLateSubmission() && assignment.getDeadline() != null
                && (cutoff == null || assignment.getDeadline().isBefore(cutoff))) cutoff = assignment.getDeadline();
        return cutoff;
    }

    private Instant effectiveSubmissionTime(AssignmentAttemptEntity attempt) {
        return isTimeExpired(attempt, attempt.getAssignment()) ? expiresAt(attempt) : clock.instant();
    }

    private AssignmentEntity lockedAssignment(UUID assignmentId, UUID classroomId) {
        if (classroomService != null) classroomService.lockReadable(classroomId);
        return assignments.findForUpdate(assignmentId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));
    }

    private void requireStarted(AssignmentEntity assignment) {
        if (assignment.getStartTime() != null && clock.instant().isBefore(assignment.getStartTime())) {
            throw new ApiException(ErrorCode.ASSIGNMENT_NOT_STARTED);
        }
    }

    private void requireWritable(AssignmentEntity assignment, Instant submittedAt) {
        if (assignment.getClassroom().getStatus() != ClassroomStatus.ACTIVE
                || assignment.getClassroom().getOwner().getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
        activateDueAssignment(assignment);
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED
                || (lessonProgress != null ? !lessonProgress.availableToStudent(assignment.getId(),assignment.getClassroom().getId(),null)
                    : linkedItems.existsByItemTypeAndItemIdAndLessonStatus(LinkedItemType.ASSIGNMENT, assignment.getId(), LessonStatus.ARCHIVED))) {
            throw new ApiException(ErrorCode.ASSIGNMENT_CLOSED);
        }
        requireStarted(assignment);
        if (assignment.getDeadline() != null && submittedAt.isAfter(assignment.getDeadline())
                && !assignment.isAllowLateSubmission()) {
            throw new ApiException(ErrorCode.ASSIGNMENT_CLOSED);
        }
    }

    private void recalculateFinalScore(AssignmentAttemptEntity attempt) {
        var score=AssignmentScoreCalculator.calculate(attempt,answers.findByAttemptId(attempt.getId()),
                attempt.getRevisionSnapshot()==null?questions.findByAssignmentIdOrderByDisplayOrderAsc(attempt.getAssignment().getId()):List.of());
        attempt.setAutoScore(score.automatic());attempt.setManualScore(score.manual());
        attempt.setFinalScore(score.complete()?(attempt.getOverriddenScore()==null?score.total():attempt.getOverriddenScore()):null);
        attempt.setStatus(score.complete()?AttemptStatus.GRADED:AttemptStatus.SUBMITTED);
        attempts.save(attempt);
    }

    private void progressChanged(AssignmentAttemptEntity attempt) {
        if (events != null) {
            attempts.flush();
            events.publishEvent(new AssignmentProgressChanged(attempt.getAssignment().getClassroom().getId(),
                attempt.getAssignment().getId(), attempt.getStudent().getId()));
        }
    }

    private void recordEvaluation(AssignmentAttemptEntity attempt, UUID actor, String kind, String reason) {
        if (evaluations == null) return;
        evaluations.append(attempt, answers.findByAttemptId(attempt.getId()), actor, kind, reason);
        var assignment=attempt.getAssignment();
        boolean automatic=assignment.getGradePublishMode()==GradePublishMode.IMMEDIATE && "AUTO".equals(kind)
                || assignment.getGradePublishMode()==GradePublishMode.AFTER_DEADLINE && assignment.getDeadline()!=null
                && !clock.instant().isBefore(assignment.getDeadline());
        if(automatic && attempt.getStatus()==AttemptStatus.GRADED) evaluations.publish(attempt);
    }

    private String captureSnapshot(AssignmentEntity assignment) {
        var frozen = questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId()).stream().map(q -> {
            var frozenOptions = options.findByQuestionIdOrderByDisplayOrderAsc(q.getId()).stream()
                    .map(o -> new AssignmentSnapshotPolicy.Option(o.getId(), o.getContent(), o.isCorrect(), o.getDisplayOrder())).toList();
            return new AssignmentSnapshotPolicy.Question(q.getId(), q.getQuestionType(), q.getTitle(), q.getPrompt(),
                    q.getPoints(), q.getGradingPolicy(), q.getEssayFormat(), q.getExplanation(), q.getCodeTemplate(),
                    q.getCodeLanguage(), q.getDisplayOrder(), frozenOptions, q.getCodeContentSnapshot(), q.getQuestionWeight(), q.getCodeCaseWeights());
        }).toList();
        return AssignmentSnapshotPolicy.encode(new AssignmentSnapshotPolicy.Content(assignment.getDurationMinutes(),
                assignment.getTimeExpiredAction(), assignment.isShuffleQuestions(), assignment.isShuffleAnswers(), frozen, assignment.getCodeFeedbackLevel()));
    }

    private AssignmentSnapshotPolicy.Question frozenQuestion(AssignmentAttemptEntity attempt, UUID questionId) {
        if (attempt.getRevisionSnapshot() == null) return null;
        return AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).questions().stream()
                .filter(q -> q.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));
    }

    private BigDecimal gradeSingleChoiceOrTF(AssignmentQuestionEntity q, String selectedOptionIds) {
        return ObjectiveQuestionGrader.grade(q.getQuestionType(), q.getGradingPolicy(), q.getPoints(),
                options.findByQuestionIdOrderByDisplayOrderAsc(q.getId()), selectedOptionIds);
    }

    private BigDecimal gradeMultipleChoice(AssignmentQuestionEntity q, String selectedOptionIds) {
        return gradeSingleChoiceOrTF(q, selectedOptionIds);
    }

    private ClassroomEntity requireClassroom(UUID classroomId) {
        return classrooms.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    }

    private void requireOwnerForRead(ClassroomEntity classroom,UUID accountId) {
        if(!classroom.getOwner().getId().equals(accountId) || classroom.getOwner().getRole()!=com.algolearner.platform.auth.AccountRole.TEACHER)
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
    }
    private void requireOwner(ClassroomEntity classroom, UUID accountId) {
        requireWritableClassroom(classroom);
        requireOwnerForRead(classroom,accountId);
    }
    private void requireWritableClassroom(ClassroomEntity classroom) {
        if(classroomService!=null)classroomService.lockReadable(classroom.getId());
        if(classroom.getStatus()!=ClassroomStatus.ACTIVE || classroom.getOwner().getStatus()!=AccountStatus.ACTIVE)
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
    }

    private void requireActiveStudent(UUID classroomId, UUID accountId) {
        boolean active = memberships.findByClassroomIdAndStudentId(classroomId, accountId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
    }

    private BigDecimal publishedBestScore(AssignmentEntity assignment, List<AssignmentAttemptEntity> history) {
        BigDecimal best=null, ratio=null;
        for(var attempt:history) {
            BigDecimal score;
            if(attempt.getPublishedEvaluationId()!=null && evaluations!=null) {
                var evaluation=evaluations.published(attempt.getId(),attempt.getPublishedEvaluationId());
                score=evaluation!=null && evaluation.complete()?evaluation.finalScore():null;
            } else score=attempt.getCurrentEvaluationId()==null && assignment.isGradePublished()?attempt.getFinalScore():null;
            if(score==null || attempt.getStatus()==AttemptStatus.IN_PROGRESS || attempt.getStatus()==AttemptStatus.TIMED_OUT)continue;
            BigDecimal maximum=attempt.getRevisionSnapshot()==null?null:AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).questions().stream()
                    .map(AssignmentSnapshotPolicy.Question::points).reduce(BigDecimal.ZERO,BigDecimal::add);
            var normalized=maximum!=null && maximum.signum()>0?score.divide(maximum,12,RoundingMode.HALF_UP):score;
            if(best==null || assignment.getScoringPolicy()==ScoringPolicy.HIGHEST && normalized.compareTo(ratio)>0) {best=score;ratio=normalized;}
            if(assignment.getScoringPolicy()==ScoringPolicy.LATEST)break;
        }
        return best;
    }

    private AssignmentDtos.AssignmentSummaryView toSummaryView(AssignmentEntity a, UUID accountId, boolean isOwner) {
        if (!isOwner && a.getStartTime() != null && clock.instant().isBefore(a.getStartTime())) {
            return new AssignmentDtos.AssignmentSummaryView(a.getId(), a.getClassroom().getId(), a.getTitle(), null,
                    a.getStatus(), a.getStartTime(), null, null, null, null, null, null, null);
        }
        Integer myRemaining = null;
        BigDecimal myBest = null;
        if (!isOwner) {
            var history = attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(a.getId(), accountId);
            long used = history.stream().filter(t -> t.getSubmittedAt() != null).count();
            var allowance = participation == null ? new AssignmentParticipationPolicy.Allowance(0,0,Instant.EPOCH) : participation.allowance(a.getId(),accountId);
            myRemaining = a.getMaxAttempts() > 0 ? (int)Math.max(0,(long)a.getMaxAttempts()+allowance.extra()+allowance.baseline()-used) : 999;
            myBest = publishedBestScore(a,history);
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
            var history = attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(a.getId(), accountId);
            long used = history.stream().filter(t -> t.getSubmittedAt() != null).count();
            var allowance = participation == null ? new AssignmentParticipationPolicy.Allowance(0,0,Instant.EPOCH) : participation.allowance(a.getId(),accountId);
            myRemaining = a.getMaxAttempts() > 0 ? (int)Math.max(0,(long)a.getMaxAttempts()+allowance.extra()+allowance.baseline()-used) : 999;
            myBest = publishedBestScore(a,history);
        }

        boolean canSeeAnswers = isOwner || (a.getViewAnswersMode() == ViewAnswersMode.ALWAYS);
        var qViews = questions.findByAssignmentIdOrderByDisplayOrderAsc(a.getId()).stream()
                .map(q -> toQuestionView(q, canSeeAnswers, isOwner))
                .toList();

        if(!isOwner && revisions!=null && a.getPublishedRevisionId()!=null) {
            var revision=revisions.findById(a.getPublishedRevisionId()).orElseThrow(()->new IllegalStateException("Published revision is missing"));
            var submitted=attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(a.getId(),accountId).stream()
                    .map(AssignmentAttemptEntity::getSubmittedAt).filter(Objects::nonNull).findFirst().orElse(null);
            qViews=snapshotQuestionViews(AssignmentAttemptEntity.builder().revisionSnapshot(revision.getContent()).submittedAt(submitted).build(),a,false);
        }
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
                q.getCodeLanguage(), optViews, canSeeAnswers ? q.getExplanation() : null,q.getQuestionWeight(),isOwner?q.getCodeCaseWeights():null,isOwner?AssignmentSnapshotPolicy.decodeCode(q.getCodeContentSnapshot()):null
        );
    }

    private AssignmentDtos.AttemptView toAttemptView(AssignmentAttemptEntity att, AssignmentEntity a, boolean isOwner) {
        Long remainingSeconds = null;
        if (att.getStatus() == AttemptStatus.IN_PROGRESS && expiresAt(att) != null)
            remainingSeconds = Math.max(0, Duration.between(clock.instant(), expiresAt(att)).toSeconds());

        var published = !isOwner && evaluations != null && att.getPublishedEvaluationId() != null
                ? evaluations.published(att.getId(), att.getPublishedEvaluationId()) : null;
        boolean showGrades = isOwner || published != null || (att.getCurrentEvaluationId() == null && a.isGradePublished());
        var publishedParts = published == null ? List.<AssignmentEvaluationHistory.Part>of() : evaluations.parts(published);
        var ansViews = answers.findByAttemptId(att.getId()).stream().map(ans -> {
            var view = toAnswerView(ans, showGrades && published == null);
            if (published == null) return view;
            var part = publishedParts.stream().filter(p -> p.questionId().equals(ans.getQuestion().getId())).findFirst().orElse(null);
            return new AssignmentDtos.AnswerView(view.id(), view.questionId(), view.selectedOptionIds(), view.textAnswer(), view.fileUrl(),
                    view.codeSubmission(), view.isDraft(), part == null ? null : part.score(), part == null ? null : part.feedback(), view.version(),view.fileId());
        }).toList();

        String studentName = att.getStudent().getFullName() != null
                ? att.getStudent().getFullName()
                : att.getStudent().getUsername();

        return new AssignmentDtos.AttemptView(
                att.getId(), a.getId(), att.getStudent().getId(), studentName, att.getAttemptNumber(),
                att.getStatus(), att.getStartedAt(), att.getSubmittedAt(), att.isLate(),
                published != null ? published.autoScore() : showGrades ? att.getAutoScore() : null,
                published != null ? published.manualScore() : showGrades ? att.getManualScore() : null,
                published != null ? published.overriddenScore() : showGrades ? att.getOverriddenScore() : null,
                published != null ? published.finalScore() : showGrades ? att.getFinalScore() : null,
                published != null ? published.reason() : showGrades ? att.getOverrideReason() : null,
                published == null && showGrades ? att.getTeacherFeedback() : null,
                remainingSeconds, ansViews, att.getRevisionId(), att.getSnapshotProvenance(), snapshotQuestionViews(att, a, isOwner),
                isOwner ? List.of() : studentCodeJobs(att)
        );
    }

    private List<AssignmentDtos.CodeJobView> studentCodeJobs(AssignmentAttemptEntity attempt) {
        if (evaluations == null || attempt.getPublishedEvaluationId() == null) return List.of();
        var published = evaluations.published(attempt.getId(), attempt.getPublishedEvaluationId());
        if (published == null) return List.of();
        return evaluations.parts(published).stream().filter(part -> part.judgeJobId() != null)
                .map(part -> new AssignmentDtos.CodeJobView(part.questionId(), part.judgeJobId(), part.judgeGeneration())).toList();
    }

    private List<AssignmentDtos.QuestionView> snapshotQuestionViews(AssignmentAttemptEntity attempt, AssignmentEntity assignment, boolean isOwner) {
        if (attempt.getRevisionSnapshot() == null) return List.of();
        boolean showAnswers = isOwner || assignment.getViewAnswersMode() == ViewAnswersMode.ALWAYS
                || assignment.getViewAnswersMode() == ViewAnswersMode.AFTER_SUBMIT && attempt.getSubmittedAt() != null
                || assignment.getViewAnswersMode() == ViewAnswersMode.AFTER_DEADLINE && assignment.getDeadline() != null
                    && !clock.instant().isBefore(assignment.getDeadline());
        return AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).questions().stream().map(q ->
                new AssignmentDtos.QuestionView(q.id(), q.type(), q.title(), q.prompt(), q.points(), q.displayOrder(),
                        q.policy(), q.essayFormat(), q.codeTemplate(), q.codeLanguage(), q.options().stream()
                        .map(o -> new AssignmentDtos.OptionView(o.id(), o.content(), showAnswers ? o.correct() : null)).toList(),
                        showAnswers ? q.explanation() : null,q.weight(),isOwner?q.caseWeights():null,isOwner?AssignmentSnapshotPolicy.decodeCode(q.codeContent()):null)).toList();
    }

    private AssignmentDtos.AnswerView toAnswerView(AssignmentAnswerEntity ans) {
        return toAnswerView(ans, true);
    }

    private AssignmentDtos.AnswerView toAnswerView(AssignmentAnswerEntity ans, boolean showGrades) {
        return new AssignmentDtos.AnswerView(
                ans.getId(), ans.getQuestion().getId(), ans.getSelectedOptionIds(), ans.getTextAnswer(),
                ans.getFileUrl(), ans.getCodeSubmission(), ans.isDraft(),
                showGrades ? ans.getScore() : null, showGrades ? ans.getFeedback() : null,
                ans.getAnswerVersion(), ans.getStoredFileId()
        );
    }

    private void requireOfficialAttempt(AssignmentAttemptEntity attempt) {
        if (attempt.getSubmittedAt()==null || attempt.getStatus() != AttemptStatus.SUBMITTED && attempt.getStatus() != AttemptStatus.GRADED)
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION, "Only official submissions can be graded.");
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
