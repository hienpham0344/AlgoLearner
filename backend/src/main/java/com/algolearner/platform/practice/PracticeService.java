package com.algolearner.platform.practice;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.exception.JudgeServiceUnavailableException;
import com.algolearner.platform.common.exception.ResourceNotAvailableException;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.practice.PracticeDtos.*;
import com.algolearner.platform.practice.PracticeJudgePort.JudgeCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * SRS 4.8 Classroom Practice. Access model: only the owning Teacher manages a practice (an Admin may read it, never
 * change it), only an ACTIVE student member sees available practices and runs/submits code, and every student sees
 * only their own submissions.
 * Practice is ungraded and has no deadline; its results never feed the gradebook (4.10).
 *
 * <p>RUN and SUBMIT never hold a database transaction (or JDBC connection) while the Code Judge runs: they read in a
 * short read-only transaction, call the judge with no transaction open, then write in a second short transaction.
 */
@Service
@Profile("!mock | (mock & integration)")
@Transactional(readOnly = true)
public class PracticeService {
    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);
    static final int MAX_CUSTOM_INPUT_BYTES = 64 * 1024;

    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;
    private final AccountRepository accounts;
    private final ClassroomPracticeRepository practices;
    private final PracticeTestcaseRepository testcases;
    private final PracticeSubmissionRepository submissions;
    private final PracticeCompletionRepository completions;
    private final PracticeJudgePort judge;
    private final LessonStatusPort lessons;
    private final RunRateLimiter runLimiter;
    private final Clock clock;
    private final ObjectProvider<AuditService> audit;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public PracticeService(ClassroomRepository classrooms, ClassroomMembershipRepository memberships,
                           AccountRepository accounts, ClassroomPracticeRepository practices,
                           PracticeTestcaseRepository testcases, PracticeSubmissionRepository submissions,
                           PracticeCompletionRepository completions, PracticeJudgePort judge,
                           LessonStatusPort lessons, RunRateLimiter runLimiter, Clock clock,
                           PlatformTransactionManager transactionManager, ObjectProvider<AuditService> audit) {
        this.classrooms = classrooms; this.memberships = memberships; this.accounts = accounts;
        this.practices = practices; this.testcases = testcases; this.submissions = submissions;
        this.completions = completions; this.judge = judge; this.lessons = lessons;
        this.runLimiter = runLimiter; this.clock = clock; this.audit = audit;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ teacher: practice lifecycle

    @Transactional
    public PracticeDetail create(UUID actorId, UUID classroomId, CreatePractice input) {
        var ctx = teacher(actorId, classroomId);
        requireUngraded(input.deadline(), input.maxScore());
        requireActiveClassroom(ctx.classroom());
        PracticeStatus initial = input.status() == null ? PracticeStatus.DRAFT : input.status();
        if (initial == PracticeStatus.ARCHIVED) throw new ApiException(ErrorCode.VALIDATION_FAILED, "A new practice cannot start archived");
        Set<PracticeLanguage> languages = EnumSet.copyOf(input.allowedLanguages());
        var practice = new ClassroomPracticeEntity();
        practice.setId(UUID.randomUUID());
        practice.setClassroom(ctx.classroom());
        practice.setLessonId(input.lessonId());
        practice.setTitle(input.title().strip());
        practice.setDescription(clean(input.description()));
        practice.setProblemType(input.problemType() == null ? PracticeProblemType.STANDARD_IO : input.problemType());
        practice.setAllowedLanguages(languages);
        practice.setStarterCode(starterCode(input.starterCode(), languages));
        practice.setTimeLimitMs(input.timeLimitMs() == null ? 2000 : input.timeLimitMs());
        practice.setMemoryLimitMb(input.memoryLimitMb() == null ? 256 : input.memoryLimitMb());
        practice.setStatus(PracticeStatus.DRAFT);
        practice.setTestcaseVersion(1);
        // save() merges an entity that already has an id; only the returned instance carries the audit timestamps.
        practice = practices.save(practice);
        if (input.testcases() != null) {
            int order = 0;
            for (TestcaseInput t : input.testcases()) {
                testcases.save(newTestcase(practice, 1, t, t.orderIndex() != null ? t.orderIndex() : order));
                order++;
            }
            testcases.flush();
        }
        audit(actorId, "PRACTICE_CREATE", practice.getId(), null, PracticeStatus.DRAFT.name());
        if (initial == PracticeStatus.PUBLISHED) {
            requireReady(practice, "Publishing needs at least one sample and one hidden testcase");
            practice.setStatus(PracticeStatus.PUBLISHED);
            audit(actorId, "PRACTICE_PUBLISH", practice.getId(), PracticeStatus.DRAFT.name(), PracticeStatus.PUBLISHED.name());
        }
        return detailFor(practice, null);
    }

    @Transactional
    public PracticeDetail update(UUID actorId, UUID classroomId, UUID practiceId, UpdatePractice input) {
        var ctx = teacher(actorId, classroomId);
        requireUngraded(input.deadline(), input.maxScore());
        requireActiveClassroom(ctx.classroom());
        var practice = practice(classroomId, practiceId);
        requireNotArchived(practice);
        if (input.title() != null) {
            if (input.title().isBlank()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Title must not be blank");
            practice.setTitle(input.title().strip());
        }
        if (input.description() != null) practice.setDescription(clean(input.description()));
        if (input.problemType() != null) practice.setProblemType(input.problemType());
        boolean clearLesson = Boolean.TRUE.equals(input.clearLessonId());
        if (clearLesson && input.lessonId() != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "lessonId and clearLessonId cannot be combined");
        }
        if (clearLesson) practice.setLessonId(null);
        else if (input.lessonId() != null) practice.setLessonId(input.lessonId());
        if (input.allowedLanguages() != null) {
            if (input.allowedLanguages().isEmpty()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "At least one language is required");
            var languages = EnumSet.copyOf(input.allowedLanguages());
            practice.setAllowedLanguages(languages);
            // Boilerplate of a language that is no longer allowed must not linger in the detail view.
            practice.setStarterCode(starterCode(practice.getStarterCode(), languages, true));
        }
        if (input.starterCode() != null) practice.setStarterCode(starterCode(input.starterCode(), practice.getAllowedLanguages(), false));
        if (input.timeLimitMs() != null) practice.setTimeLimitMs(input.timeLimitMs());
        if (input.memoryLimitMb() != null) practice.setMemoryLimitMb(input.memoryLimitMb());
        return detailFor(practice, null);
    }

    /**
     * DRAFT -> PUBLISHED -> ARCHIVED only. Archiving is a status change: submissions and completions are never
     * deleted. Publishing needs at least one sample and one hidden testcase in the current version.
     */
    @Transactional
    public PracticeDetail changeStatus(UUID actorId, UUID classroomId, UUID practiceId, ChangeStatus input) {
        var ctx = teacher(actorId, classroomId);
        requireActiveClassroom(ctx.classroom());
        var practice = practice(classroomId, practiceId);
        boolean allowed = (practice.getStatus() == PracticeStatus.DRAFT && input.status() == PracticeStatus.PUBLISHED)
                || (practice.getStatus() == PracticeStatus.PUBLISHED && input.status() == PracticeStatus.ARCHIVED);
        if (!allowed) {
            throw new ApiException(ErrorCode.PRACTICE_INVALID_STATUS_TRANSITION,
                    "Cannot move practice from " + practice.getStatus() + " to " + input.status());
        }
        if (input.status() == PracticeStatus.PUBLISHED) requireReady(practice, "Publishing needs at least one sample and one hidden testcase");
        PracticeStatus before = practice.getStatus();
        practice.setStatus(input.status());
        audit(actorId, input.status() == PracticeStatus.PUBLISHED ? "PRACTICE_PUBLISH" : "PRACTICE_ARCHIVE",
                practice.getId(), before.name(), input.status().name());
        return detailFor(practice, null);
    }

    /** Only a DRAFT can be deleted, and a draft is never visible to students, so it has no submissions. */
    @Transactional
    public void delete(UUID actorId, UUID classroomId, UUID practiceId) {
        var ctx = teacher(actorId, classroomId);
        requireActiveClassroom(ctx.classroom());
        var practice = practice(classroomId, practiceId);
        if (practice.getStatus() != PracticeStatus.DRAFT) throw new ApiException(ErrorCode.PRACTICE_NOT_DELETABLE);
        testcases.deleteByPracticeId(practiceId);
        practices.delete(practice);
        audit(actorId, "PRACTICE_DELETE", practiceId, PracticeStatus.DRAFT.name(), null);
    }

    // ------------------------------------------------------------------ teacher: testcases (versioned)

    public List<TeacherTestcase> listTestcases(UUID actorId, UUID classroomId, UUID practiceId) {
        teacher(actorId, classroomId);
        var practice = practice(classroomId, practiceId);
        return testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practiceId, practice.getTestcaseVersion())
                .stream().map(PracticeMapper::teacherTestcase).toList();
    }

    @Transactional
    public TeacherTestcase addTestcase(UUID actorId, UUID classroomId, UUID practiceId, TestcaseInput input, boolean rejudge) {
        var ctx = teacher(actorId, classroomId);
        requireActiveClassroom(ctx.classroom());
        var practice = lockedPractice(classroomId, practiceId);
        requireNotArchived(practice);
        openEditableVersion(actorId, practice, rejudge);
        int version = practice.getTestcaseVersion();
        int order = input.orderIndex() != null ? input.orderIndex()
                : testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practiceId, version).size();
        var testcase = newTestcase(practice, version, input, order);
        testcases.saveAndFlush(testcase);
        return PracticeMapper.teacherTestcase(testcase);
    }

    @Transactional
    public TeacherTestcase updateTestcase(UUID actorId, UUID classroomId, UUID practiceId, UUID testcaseId,
                                          TestcaseInput input, boolean rejudge) {
        var ctx = teacher(actorId, classroomId);
        requireActiveClassroom(ctx.classroom());
        var practice = lockedPractice(classroomId, practiceId);
        requireNotArchived(practice);
        var original = currentTestcase(practice, testcaseId);
        var copies = openEditableVersion(actorId, practice, rejudge);
        var target = copies == null ? original : copies.get(original.getId());
        target.setInput(input.input());
        target.setExpectedOutput(input.expectedOutput());
        target.setSample(input.sample());
        if (input.orderIndex() != null) target.setOrderIndex(input.orderIndex());
        testcases.saveAndFlush(target);
        keepPublishedReady(practice);
        return PracticeMapper.teacherTestcase(target);
    }

    @Transactional
    public void deleteTestcase(UUID actorId, UUID classroomId, UUID practiceId, UUID testcaseId, boolean rejudge) {
        var ctx = teacher(actorId, classroomId);
        requireActiveClassroom(ctx.classroom());
        var practice = lockedPractice(classroomId, practiceId);
        requireNotArchived(practice);
        var original = currentTestcase(practice, testcaseId);
        var copies = openEditableVersion(actorId, practice, rejudge);
        testcases.delete(copies == null ? original : copies.get(original.getId()));
        testcases.flush();
        keepPublishedReady(practice);
    }

    /**
     * If any submission was judged against the current testcase version, snapshot it as version + 1 and let the
     * edit apply to the copy, so old submissions keep pointing at the untouched old rows. Returns old id -> copy,
     * or null when the edit can happen in place. {@code rejudge} flags every older-version submission
     * PENDING_REJUDGE; verdicts and completions stay as they were until the 4.9 rejudge job runs.
     */
    private Map<UUID, PracticeTestcaseEntity> openEditableVersion(UUID actorId, ClassroomPracticeEntity practice, boolean rejudge) {
        Map<UUID, PracticeTestcaseEntity> copies = null;
        int current = practice.getTestcaseVersion();
        if (submissions.existsByPracticeIdAndTestcaseVersion(practice.getId(), current)) {
            copies = new HashMap<>();
            int next = current + 1;
            for (var old : testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practice.getId(), current)) {
                var copy = new PracticeTestcaseEntity();
                copy.setId(UUID.randomUUID());
                copy.setPractice(practice);
                copy.setVersion(next);
                copy.setInput(old.getInput());
                copy.setExpectedOutput(old.getExpectedOutput());
                copy.setSample(old.isSample());
                copy.setOrderIndex(old.getOrderIndex());
                testcases.save(copy);
                copies.put(old.getId(), copy);
            }
            practice.setTestcaseVersion(next);
            testcases.flush();
            audit(actorId, "PRACTICE_TESTCASE_VERSION", practice.getId(), "v" + current, "v" + next);
        }
        if (rejudge) {
            int flagged = submissions.markForRejudge(practice.getId(), practice.getTestcaseVersion(), RejudgeStatus.PENDING_REJUDGE);
            audit(actorId, "PRACTICE_REJUDGE_REQUESTED", practice.getId(), null, "submissions=" + flagged);
        }
        return copies;
    }

    // ------------------------------------------------------------------ teacher: monitoring

    public PageResponse<ProgressView> progress(UUID actorId, UUID classroomId, UUID practiceId, Pageable pageable) {
        staff(actorId, classroomId);
        practice(classroomId, practiceId);
        Page<ClassroomMembershipEntity> members = memberships.findByClassroomIdAndStatus(classroomId, MembershipStatus.ACTIVE, pageable);
        Set<UUID> studentIds = members.getContent().stream().map(m -> m.getStudent().getId()).collect(Collectors.toSet());
        Map<UUID, PracticeCompletionEntity> done = studentIds.isEmpty() ? Map.of()
                : completions.findForPractice(practiceId, studentIds).stream()
                        .collect(Collectors.toMap(c -> c.getId().getStudentId(), Function.identity()));
        return PageResponse.fromPage(members.map(m -> {
            var completion = done.get(m.getStudent().getId());
            return new ProgressView(m.getStudent().getId(), m.getStudent().getUsername(),
                    completion == null ? CompletionStatus.NOT_COMPLETED : CompletionStatus.COMPLETED,
                    completion == null ? null : completion.getCompletedAt());
        }));
    }

    public PageResponse<SubmissionView> classSubmissions(UUID actorId, UUID classroomId, UUID practiceId, UUID studentId, Pageable pageable) {
        staff(actorId, classroomId);
        practice(classroomId, practiceId);
        Page<PracticeSubmissionEntity> page = studentId == null
                ? submissions.findByPracticeId(practiceId, pageable)
                : submissions.findByPracticeIdAndStudentId(practiceId, studentId, pageable);
        return PageResponse.fromPage(page.map(PracticeMapper::submission));
    }

    // ------------------------------------------------------------------ shared reads

    public PageResponse<PracticeSummary> list(UUID actorId, UUID classroomId, Pageable pageable) {
        var ctx = participant(actorId, classroomId);
        Set<UUID> archivedLessons = lessons.archivedLessonIds(classroomId);
        if (ctx.staff()) {
            return PageResponse.fromPage(practices.findByClassroomId(classroomId, pageable)
                    .map(p -> PracticeMapper.summary(p, isAvailable(p, archivedLessons), null)));
        }
        // Practices hidden by an archived lesson are excluded in SQL, so content, totalElements and totalPages stay
        // consistent across pages.
        Page<ClassroomPracticeEntity> page = archivedLessons.isEmpty()
                ? practices.findByClassroomIdAndStatus(classroomId, PracticeStatus.PUBLISHED, pageable)
                : practices.findVisible(classroomId, PracticeStatus.PUBLISHED, archivedLessons, pageable);
        Set<UUID> ids = page.getContent().stream().map(ClassroomPracticeEntity::getId).collect(Collectors.toSet());
        Set<UUID> completed = ids.isEmpty() ? Set.of()
                : completions.findForStudent(actorId, ids).stream().map(c -> c.getId().getPracticeId()).collect(Collectors.toSet());
        return PageResponse.fromPage(page.map(p -> PracticeMapper.summary(p, true, status(completed.contains(p.getId())))));
    }

    public PracticeDetail get(UUID actorId, UUID classroomId, UUID practiceId) {
        var ctx = participant(actorId, classroomId);
        var practice = practice(classroomId, practiceId);
        if (ctx.staff()) return detailFor(practice, null);
        requireAvailable(practice);
        return detailFor(practice, status(completions.existsById(new PracticeCompletionId(actorId, practiceId))));
    }

    // ------------------------------------------------------------------ student: RUN / SUBMIT

    /** What RUN needs from the database, captured in a short read-only transaction before the judge is called. */
    private record RunPlan(PracticeProblemType problemType, int timeLimitMs, int memoryLimitMb, List<JudgeCase> cases, boolean custom) { }

    /** What SUBMIT judged: the testcase snapshot, kept to verify it is still current when the result is stored. */
    private record SubmitPlan(int version, PracticeProblemType problemType, int timeLimitMs, int memoryLimitMb, List<JudgeCase> cases) { }

    /**
     * RUN: sample testcases or one custom input; nothing is persisted and completion is untouched. SUPPORTS (not the
     * class-level read-only transaction): no JDBC connection is held while the judge runs.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public RunResponse run(UUID actorId, UUID classroomId, UUID practiceId, RunInput input) {
        var plan = readTx.execute(tx -> {
            var ctx = student(actorId, classroomId);
            var practice = practice(classroomId, practiceId);
            requireAvailable(practice);
            requireStudentCanWrite(ctx);
            requireLanguage(practice, input.language());
            boolean custom = input.customInput() != null;
            List<JudgeCase> cases;
            if (custom) {
                if (input.customInput().getBytes(StandardCharsets.UTF_8).length > MAX_CUSTOM_INPUT_BYTES) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Custom input must be at most 64 KB");
                }
                cases = List.of(new JudgeCase(0, input.customInput(), null));
            } else {
                var samples = testcases.findByPracticeIdAndVersionAndSampleTrueOrderByOrderIndexAsc(practiceId, practice.getTestcaseVersion());
                if (samples.isEmpty()) throw new ApiException(ErrorCode.PRACTICE_NOT_READY, "Practice has no sample testcases");
                cases = judgeCases(samples);
            }
            return new RunPlan(practice.getProblemType(), practice.getTimeLimitMs(), practice.getMemoryLimitMb(), cases, custom);
        });
        runLimiter.acquire(actorId);
        var cases = plan.cases();
        var result = callJudge(practiceId, actorId, () -> judge.run(new PracticeJudgePort.RunRequest(practiceId,
                plan.problemType(), input.language(), input.sourceCode(), plan.timeLimitMs(), plan.memoryLimitMb(), cases)));
        requireCompleteRun(practiceId, actorId, result, cases.size());
        var views = result.cases().stream()
                .sorted(Comparator.comparingInt(PracticeJudgePort.CaseResult::index))
                .map(r -> new RunCaseResult(r.index(), cases.get(r.index()).input(), plan.custom() ? null : cases.get(r.index()).expectedOutput(),
                        r.verdict(), r.stdout(), r.stderr(), r.runtimeMs()))
                .toList();
        var overall = views.stream().map(RunCaseResult::verdict).filter(v -> v != PracticeVerdict.ACCEPTED).findFirst().orElse(PracticeVerdict.ACCEPTED);
        return new RunResponse(overall, views);
    }

    /** One result per submitted case, no duplicate or unknown index, no INTERNAL_ERROR: anything else is a judge failure. */
    private void requireCompleteRun(UUID practiceId, UUID studentId, PracticeJudgePort.RunResult result, int expected) {
        if (result.cases() == null || result.cases().size() != expected) {
            throw judgeFailure(practiceId, studentId, new IllegalStateException("run result does not cover every testcase"));
        }
        var seen = new HashSet<Integer>();
        for (var r : result.cases()) {
            if (r == null || r.index() < 0 || r.index() >= expected || !seen.add(r.index())) {
                throw judgeFailure(practiceId, studentId, new IllegalStateException("unknown or duplicate testcase index"));
            }
            if (r.verdict() == null || r.verdict() == PracticeVerdict.INTERNAL_ERROR) {
                throw judgeFailure(practiceId, studentId, new IllegalStateException("judge reported no usable verdict for a testcase"));
            }
        }
    }

    /**
     * SUBMIT: judge every testcase of the current version, store the submission pointing at that version, and
     * record the first ACCEPTED as completion. A judge outage stores nothing and invents no verdict.
     *
     * <p>Three steps, never one transaction: (1) read-only: access checks and the testcase snapshot; (2) judge, with
     * no transaction open; (3) read-write: re-check access, lock the practice row, make sure the testcases did not
     * change while judging (otherwise 409, nothing stored), store the submission and the completion.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public SubmissionView submit(UUID actorId, UUID classroomId, UUID practiceId, CodeRequest input) {
        var plan = readTx.execute(tx -> {
            var ctx = student(actorId, classroomId);
            var practice = practice(classroomId, practiceId);
            requireAvailable(practice);
            requireStudentCanWrite(ctx);
            requireLanguage(practice, input.language());
            int version = practice.getTestcaseVersion();
            var all = testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practiceId, version);
            if (all.isEmpty()) throw new ApiException(ErrorCode.PRACTICE_NOT_READY);
            return new SubmitPlan(version, practice.getProblemType(), practice.getTimeLimitMs(), practice.getMemoryLimitMb(), judgeCases(all));
        });
        var result = callJudge(practiceId, actorId, () -> judge.submit(new PracticeJudgePort.SubmitRequest(practiceId,
                plan.problemType(), input.language(), input.sourceCode(), plan.timeLimitMs(), plan.memoryLimitMb(),
                plan.version(), plan.cases())));
        requireUsableSubmit(practiceId, actorId, result, plan.cases().size());
        return writeTx.execute(tx -> storeSubmission(actorId, classroomId, practiceId, input, plan, result));
    }

    /** A verdict-less, INTERNAL_ERROR or self-contradicting result is a judge failure (503), never a stored submission. */
    private void requireUsableSubmit(UUID practiceId, UUID studentId, PracticeJudgePort.SubmitResult result, int total) {
        if (result.verdict() == null) throw judgeFailure(practiceId, studentId, new IllegalStateException("judge returned no verdict"));
        if (result.verdict() == PracticeVerdict.INTERNAL_ERROR) {
            throw judgeFailure(practiceId, studentId, new IllegalStateException("judge reported INTERNAL_ERROR"));
        }
        if (result.testsPassed() < 0 || result.testsPassed() > total
                || (result.verdict() == PracticeVerdict.ACCEPTED && result.testsPassed() != total)) {
            throw judgeFailure(practiceId, studentId, new IllegalStateException("judge returned an inconsistent tests-passed count"));
        }
    }

    private SubmissionView storeSubmission(UUID actorId, UUID classroomId, UUID practiceId, CodeRequest input,
                                           SubmitPlan plan, PracticeJudgePort.SubmitResult result) {
        var ctx = student(actorId, classroomId);
        requireStudentCanWrite(ctx);
        var practice = lockedPractice(classroomId, practiceId);
        requireAvailable(practice);
        if (practice.getTestcaseVersion() != plan.version()
                || !plan.cases().equals(judgeCases(testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practiceId, plan.version())))) {
            throw new ApiException(ErrorCode.PRACTICE_TESTCASES_CHANGED);
        }
        var submission = new PracticeSubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setPractice(practice);
        submission.setStudent(ctx.account());
        submission.setTestcaseVersion(plan.version());
        submission.setCode(input.sourceCode());
        submission.setLanguage(input.language());
        submission.setVerdict(result.verdict());
        submission.setTestsPassed(result.testsPassed());
        submission.setTotalTests(plan.cases().size());
        submission.setRuntimeMs(result.runtimeMs());
        submission.setMemoryKb(result.memoryKb());
        submission.setJudgeDetails(result.details());
        submission.setSubmittedAt(clock.instant());
        submissions.saveAndFlush(submission);

        // Atomic first-ACCEPTED-wins: a parallel ACCEPTED for the same student cannot turn into a 500.
        if (result.verdict() == PracticeVerdict.ACCEPTED) {
            completions.insertIfAbsent(actorId, practiceId, submission.getId(), clock.instant());
        }
        return PracticeMapper.submission(submission);
    }

    public PageResponse<SubmissionView> mySubmissions(UUID actorId, UUID classroomId, UUID practiceId, Pageable pageable) {
        student(actorId, classroomId);
        requireHistoryVisible(practice(classroomId, practiceId));
        return PageResponse.fromPage(submissions.findByPracticeIdAndStudentId(practiceId, actorId, pageable).map(PracticeMapper::submission));
    }

    /** Teacher sees any submission of the practice; a student only their own (otherwise 403). */
    public SubmissionView submission(UUID actorId, UUID classroomId, UUID practiceId, UUID submissionId) {
        var ctx = participant(actorId, classroomId);
        var practice = practice(classroomId, practiceId);
        if (!ctx.staff()) requireHistoryVisible(practice);
        var submission = submissions.findByIdAndPracticeId(submissionId, practiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        if (!ctx.staff() && !submission.getStudent().getId().equals(actorId)) throw forbidden("Submission belongs to another student");
        return PracticeMapper.submission(submission);
    }

    // ------------------------------------------------------------------ audit (SRS 4.16)

    /**
     * Sensitive teacher actions are written to the audit log in the caller's transaction. Only states and counts are
     * recorded, never source code or testcase content. The audit module is absent in the {@code mock} profile.
     */
    private void audit(UUID actorId, String action, UUID practiceId, String before, String after) {
        audit.ifAvailable(log -> log.record(actorId, AccountRole.TEACHER.name(), "CLASSROOM_PRACTICE",
                practiceId.toString(), action, before, after, null, null));
    }

    // ------------------------------------------------------------------ judge boundary

    /** Any failure of the judge becomes a 503. The cause is logged (never the source code) and nothing is stored. */
    private <T> T callJudge(UUID practiceId, UUID studentId, Supplier<T> call) {
        try {
            T result = call.get();
            if (result == null) throw new IllegalStateException("judge returned no result");
            return result;
        } catch (RuntimeException e) {
            throw judgeFailure(practiceId, studentId, e);
        }
    }

    private JudgeServiceUnavailableException judgeFailure(UUID practiceId, UUID studentId, Exception cause) {
        log.error("Code judge failure practice={} student={}", practiceId, studentId, cause);
        return cause instanceof JudgeServiceUnavailableException ex ? ex
                : new JudgeServiceUnavailableException("Code judge failed: " + cause.getClass().getSimpleName(), cause);
    }

    // ------------------------------------------------------------------ access helpers

    /** {@code admin}: an ACTIVE Admin, who may read (never change) the practice data of any classroom. */
    private record Ctx(AccountEntity account, ClassroomEntity classroom, boolean teacher, boolean admin) {
        /** Sees practice-level data of every student: the owning Teacher, or an Admin (read side only). */
        boolean staff() { return teacher || admin; }
    }

    /**
     * The owning Teacher, an ACTIVE-membership student or an Admin (read-only), all with an ACTIVE account (FR-CLS-009: a suspended or
     * deactivated account reaches no learning feature, reads included). A student of a classroom whose owner is
     * inactive still reads; {@link #requireStudentCanWrite} blocks RUN/SUBMIT for them (FR-CLS-010).
     */
    private Ctx participant(UUID actorId, UUID classroomId) {
        var account = accounts.findById(actorId).orElseThrow(() -> forbidden("Account access denied"));
        if (account.getStatus() != AccountStatus.ACTIVE) throw forbidden("Inactive accounts cannot access classroom practice");
        var classroom = classrooms.findById(classroomId).orElseThrow(() -> new ResourceNotFoundException("Classroom", classroomId));
        if (account.getRole() == AccountRole.ADMIN) return new Ctx(account, classroom, false, true);
        if (account.getRole() == AccountRole.TEACHER && classroom.getOwner().getId().equals(actorId)) {
            return new Ctx(account, classroom, true, false);
        }
        if (account.getRole() == AccountRole.STUDENT && memberships.findByClassroomIdAndStudentId(classroomId, actorId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE).orElse(false)) return new Ctx(account, classroom, false, false);
        throw forbidden("Only the classroom owner or an active member may access practices");
    }

    private Ctx teacher(UUID actorId, UUID classroomId) {
        var ctx = participant(actorId, classroomId);
        if (!ctx.teacher()) throw forbidden("Only the classroom owner may perform this operation");
        return ctx;
    }

    /** Owner Teacher or Admin, for read-only monitoring. Hidden testcases and every write stay Teacher-only. */
    private Ctx staff(UUID actorId, UUID classroomId) {
        var ctx = participant(actorId, classroomId);
        if (!ctx.staff()) throw forbidden("Only the classroom owner or an Admin may perform this operation");
        return ctx;
    }

    private Ctx student(UUID actorId, UUID classroomId) {
        var ctx = participant(actorId, classroomId);
        if (ctx.teacher() || ctx.admin()) throw forbidden("Only a student member may perform this operation");
        return ctx;
    }

    /** RUN/SUBMIT need a classroom that is neither archived nor read-only (owner inactive); the student is ACTIVE already. */
    private static void requireStudentCanWrite(Ctx ctx) {
        if (ctx.classroom().getOwner().getStatus() != AccountStatus.ACTIVE) throw new ApiException(ErrorCode.CLASSROOM_READ_ONLY);
        requireActiveClassroom(ctx.classroom());
    }

    private ClassroomPracticeEntity practice(UUID classroomId, UUID practiceId) {
        return practices.findByIdAndClassroomId(practiceId, classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Practice", practiceId));
    }

    /**
     * The practice row under a write lock. Testcase edits and the SUBMIT write step both take it, so versioning
     * ({@link #openEditableVersion}) can never interleave with a submission being stored or with another edit.
     */
    private ClassroomPracticeEntity lockedPractice(UUID classroomId, UUID practiceId) {
        return practices.lockByIdAndClassroomId(practiceId, classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Practice", practiceId));
    }

    private PracticeTestcaseEntity currentTestcase(ClassroomPracticeEntity practice, UUID testcaseId) {
        return testcases.findByIdAndPracticeIdAndVersion(testcaseId, practice.getId(), practice.getTestcaseVersion())
                .orElseThrow(() -> new ResourceNotFoundException("Testcase", testcaseId));
    }

    /** PUBLISHED and not hidden by an archived lesson. */
    private boolean isAvailable(ClassroomPracticeEntity practice) {
        return practice.getStatus() == PracticeStatus.PUBLISHED
                && (practice.getLessonId() == null || !lessons.isArchived(practice.getLessonId()));
    }

    /** Same rule against a precomputed set of archived lesson ids (listings: one lookup instead of one per row). */
    private static boolean isAvailable(ClassroomPracticeEntity practice, Set<UUID> archivedLessons) {
        return practice.getStatus() == PracticeStatus.PUBLISHED
                && (practice.getLessonId() == null || !archivedLessons.contains(practice.getLessonId()));
    }

    private void requireAvailable(ClassroomPracticeEntity practice) {
        if (!isAvailable(practice)) throw new ResourceNotAvailableException("Practice", practice.getId());
    }

    /** History stays readable after archiving, but a draft never existed for students. */
    private static void requireHistoryVisible(ClassroomPracticeEntity practice) {
        if (practice.getStatus() == PracticeStatus.DRAFT) throw new ResourceNotAvailableException("Practice", practice.getId());
    }

    private static void requireActiveClassroom(ClassroomEntity classroom) {
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) throw new ApiException(ErrorCode.CLASSROOM_ARCHIVED);
    }

    private static void requireNotArchived(ClassroomPracticeEntity practice) {
        if (practice.getStatus() == PracticeStatus.ARCHIVED) throw new ApiException(ErrorCode.PRACTICE_READ_ONLY);
    }

    private static void requireLanguage(ClassroomPracticeEntity practice, PracticeLanguage language) {
        if (!practice.getAllowedLanguages().contains(language)) throw new ApiException(ErrorCode.LANGUAGE_NOT_ALLOWED);
    }

    /** Practice is ungraded with no deadline: any attempt to configure either is a 400. */
    private static void requireUngraded(Object deadline, Integer maxScore) {
        if (deadline != null || (maxScore != null && maxScore != 0)) throw new ApiException(ErrorCode.PRACTICE_SCOPE_VIOLATION);
    }

    private void requireReady(ClassroomPracticeEntity practice, String message) {
        int v = practice.getTestcaseVersion();
        if (testcases.countByPracticeIdAndVersionAndSample(practice.getId(), v, true) == 0
                || testcases.countByPracticeIdAndVersionAndSample(practice.getId(), v, false) == 0) {
            throw new ApiException(ErrorCode.PRACTICE_NOT_READY, message);
        }
    }

    private void keepPublishedReady(ClassroomPracticeEntity practice) {
        if (practice.getStatus() == PracticeStatus.PUBLISHED) requireReady(practice, "A published practice must keep a sample and a hidden testcase");
    }

    private PracticeDetail detailFor(ClassroomPracticeEntity practice, CompletionStatus completion) {
        // Only sample testcases are ever loaded for a detail view, for teachers and students alike.
        var samples = testcases.findByPracticeIdAndVersionAndSampleTrueOrderByOrderIndexAsc(practice.getId(), practice.getTestcaseVersion());
        return PracticeMapper.detail(practice, isAvailable(practice), completion, samples);
    }

    private static Map<PracticeLanguage, String> starterCode(Map<PracticeLanguage, String> source, Set<PracticeLanguage> allowed) {
        return starterCode(source, allowed, false);
    }

    /** {@code dropDisallowed}: silently drop entries for languages outside {@code allowed} instead of rejecting them. */
    private static Map<PracticeLanguage, String> starterCode(Map<PracticeLanguage, String> source, Set<PracticeLanguage> allowed,
                                                             boolean dropDisallowed) {
        var result = new EnumMap<PracticeLanguage, String>(PracticeLanguage.class);
        if (source != null) {
            source.forEach((language, code) -> {
                if (!allowed.contains(language)) {
                    if (dropDisallowed) return;
                    throw new ApiException(ErrorCode.LANGUAGE_NOT_ALLOWED, "Starter code given for a language that is not allowed");
                }
                if (code != null) result.put(language, code);
            });
        }
        return result;
    }

    private static PracticeTestcaseEntity newTestcase(ClassroomPracticeEntity practice, int version, TestcaseInput in, int order) {
        var t = new PracticeTestcaseEntity();
        t.setId(UUID.randomUUID());
        t.setPractice(practice);
        t.setVersion(version);
        t.setInput(in.input());
        t.setExpectedOutput(in.expectedOutput());
        t.setSample(in.sample());
        t.setOrderIndex(order);
        return t;
    }

    private static ApiException forbidden(String message) { return new ApiException(ErrorCode.FORBIDDEN_OPERATION, message); }
    private static CompletionStatus status(boolean completed) { return completed ? CompletionStatus.COMPLETED : CompletionStatus.NOT_COMPLETED; }
    private static String clean(String value) { return value == null ? null : value.strip(); }

    private static List<JudgeCase> judgeCases(List<PracticeTestcaseEntity> source) {
        List<JudgeCase> cases = new ArrayList<>(source.size());
        IntStream.range(0, source.size()).forEach(i -> cases.add(new JudgeCase(i, source.get(i).getInput(), source.get(i).getExpectedOutput())));
        return cases;
    }
}
