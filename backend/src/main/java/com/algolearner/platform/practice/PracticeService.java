package com.algolearner.platform.practice;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
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
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * SRS 4.8 Classroom Practice. Access model: only the owning Teacher manages and monitors a practice, only an
 * ACTIVE student member sees available practices, and every student sees only their own submissions.
 * Practice is ungraded and has no deadline; its results never feed the gradebook (4.10).
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

    public PracticeService(ClassroomRepository classrooms, ClassroomMembershipRepository memberships,
                           AccountRepository accounts, ClassroomPracticeRepository practices,
                           PracticeTestcaseRepository testcases, PracticeSubmissionRepository submissions,
                           PracticeCompletionRepository completions, PracticeJudgePort judge,
                           LessonStatusPort lessons, RunRateLimiter runLimiter, Clock clock) {
        this.classrooms = classrooms; this.memberships = memberships; this.accounts = accounts;
        this.practices = practices; this.testcases = testcases; this.submissions = submissions;
        this.completions = completions; this.judge = judge; this.lessons = lessons;
        this.runLimiter = runLimiter; this.clock = clock;
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
        if (initial == PracticeStatus.PUBLISHED) {
            requireReady(practice, "Publishing needs at least one sample and one hidden testcase");
            practice.setStatus(PracticeStatus.PUBLISHED);
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
        if (input.lessonId() != null) practice.setLessonId(input.lessonId());
        if (input.allowedLanguages() != null) {
            if (input.allowedLanguages().isEmpty()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "At least one language is required");
            practice.setAllowedLanguages(EnumSet.copyOf(input.allowedLanguages()));
        }
        if (input.starterCode() != null) practice.setStarterCode(starterCode(input.starterCode(), practice.getAllowedLanguages()));
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
        practice.setStatus(input.status());
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
        var practice = practice(classroomId, practiceId);
        requireNotArchived(practice);
        openEditableVersion(practice, rejudge);
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
        var practice = practice(classroomId, practiceId);
        requireNotArchived(practice);
        var original = currentTestcase(practice, testcaseId);
        var copies = openEditableVersion(practice, rejudge);
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
        var practice = practice(classroomId, practiceId);
        requireNotArchived(practice);
        var original = currentTestcase(practice, testcaseId);
        var copies = openEditableVersion(practice, rejudge);
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
    private Map<UUID, PracticeTestcaseEntity> openEditableVersion(ClassroomPracticeEntity practice, boolean rejudge) {
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
        }
        if (rejudge) submissions.markForRejudge(practice.getId(), practice.getTestcaseVersion(), RejudgeStatus.PENDING_REJUDGE);
        return copies;
    }

    // ------------------------------------------------------------------ teacher: monitoring

    public PageResponse<ProgressView> progress(UUID actorId, UUID classroomId, UUID practiceId, Pageable pageable) {
        teacher(actorId, classroomId);
        practice(classroomId, practiceId);
        Page<ClassroomMembershipEntity> members = memberships.findByClassroomIdAndStatus(classroomId, MembershipStatus.ACTIVE, pageable);
        Set<UUID> studentIds = members.getContent().stream().map(m -> m.getStudent().getId()).collect(Collectors.toSet());
        Map<UUID, PracticeCompletionEntity> done = completions.findForPractice(practiceId, studentIds).stream()
                .collect(Collectors.toMap(c -> c.getId().getStudentId(), Function.identity()));
        return PageResponse.fromPage(members.map(m -> {
            var completion = done.get(m.getStudent().getId());
            return new ProgressView(m.getStudent().getId(), m.getStudent().getUsername(),
                    completion == null ? CompletionStatus.NOT_COMPLETED : CompletionStatus.COMPLETED,
                    completion == null ? null : completion.getCompletedAt());
        }));
    }

    public PageResponse<SubmissionView> classSubmissions(UUID actorId, UUID classroomId, UUID practiceId, UUID studentId, Pageable pageable) {
        teacher(actorId, classroomId);
        practice(classroomId, practiceId);
        Page<PracticeSubmissionEntity> page = studentId == null
                ? submissions.findByPracticeId(practiceId, pageable)
                : submissions.findByPracticeIdAndStudentId(practiceId, studentId, pageable);
        return PageResponse.fromPage(page.map(PracticeMapper::submission));
    }

    // ------------------------------------------------------------------ shared reads

    public PageResponse<PracticeSummary> list(UUID actorId, UUID classroomId, Pageable pageable) {
        var ctx = participant(actorId, classroomId);
        if (ctx.teacher()) {
            return PageResponse.fromPage(practices.findByClassroomId(classroomId, pageable)
                    .map(p -> PracticeMapper.summary(p, isAvailable(p), null)));
        }
        Page<ClassroomPracticeEntity> page = practices.findByClassroomIdAndStatus(classroomId, PracticeStatus.PUBLISHED, pageable);
        List<ClassroomPracticeEntity> visible = page.getContent().stream().filter(this::isAvailable).toList();
        Set<UUID> ids = visible.stream().map(ClassroomPracticeEntity::getId).collect(Collectors.toSet());
        Set<UUID> completed = completions.findForStudent(actorId, ids).stream().map(c -> c.getId().getPracticeId()).collect(Collectors.toSet());
        // Practices hidden by an archived lesson are dropped from this page; the total is reduced to match.
        long total = Math.max(visible.size(), page.getTotalElements() - (page.getContent().size() - visible.size()));
        return PageResponse.fromPage(new PageImpl<>(
                visible.stream().map(p -> PracticeMapper.summary(p, true, status(completed.contains(p.getId())))).toList(), pageable, total));
    }

    public PracticeDetail get(UUID actorId, UUID classroomId, UUID practiceId) {
        var ctx = participant(actorId, classroomId);
        var practice = practice(classroomId, practiceId);
        if (ctx.teacher()) return detailFor(practice, null);
        requireAvailable(practice);
        return detailFor(practice, status(completions.existsById(new PracticeCompletionId(actorId, practiceId))));
    }

    // ------------------------------------------------------------------ student: RUN / SUBMIT

    /** RUN: sample testcases or one custom input; nothing is persisted and completion is untouched. */
    public RunResponse run(UUID actorId, UUID classroomId, UUID practiceId, RunInput input) {
        var ctx = student(actorId, classroomId);
        var practice = practice(classroomId, practiceId);
        requireAvailable(practice);
        requireStudentCanWrite(ctx);
        requireLanguage(practice, input.language());
        boolean custom = input.customInput() != null;
        List<PracticeTestcaseEntity> samples = List.of();
        List<JudgeCase> cases;
        if (custom) {
            if (input.customInput().getBytes(StandardCharsets.UTF_8).length > MAX_CUSTOM_INPUT_BYTES) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Custom input must be at most 64 KB");
            }
            cases = List.of(new JudgeCase(0, input.customInput(), null));
        } else {
            samples = testcases.findByPracticeIdAndVersionAndSampleTrueOrderByOrderIndexAsc(practiceId, practice.getTestcaseVersion());
            if (samples.isEmpty()) throw new ApiException(ErrorCode.PRACTICE_NOT_READY, "Practice has no sample testcases");
            cases = judgeCases(samples);
        }
        runLimiter.acquire(actorId);
        var result = callJudge(practiceId, actorId, () -> judge.run(new PracticeJudgePort.RunRequest(practiceId,
                practice.getProblemType(), input.language(), input.sourceCode(), practice.getTimeLimitMs(), practice.getMemoryLimitMb(), cases)));
        if (result.cases() == null || result.cases().isEmpty()) throw judgeFailure(practiceId, actorId, new IllegalStateException("empty run result"));
        final List<PracticeTestcaseEntity> sampleRows = samples;
        var views = result.cases().stream().map(r -> {
            if (r.index() < 0 || r.index() >= cases.size()) throw judgeFailure(practiceId, actorId, new IllegalStateException("unknown testcase index"));
            return custom
                    ? new RunCaseResult(r.index(), input.customInput(), null, r.verdict(), r.stdout(), r.stderr(), r.runtimeMs())
                    : new RunCaseResult(r.index(), sampleRows.get(r.index()).getInput(), sampleRows.get(r.index()).getExpectedOutput(),
                            r.verdict(), r.stdout(), r.stderr(), r.runtimeMs());
        }).toList();
        var overall = views.stream().map(RunCaseResult::verdict).filter(v -> v != PracticeVerdict.ACCEPTED).findFirst().orElse(PracticeVerdict.ACCEPTED);
        return new RunResponse(overall, views);
    }

    /**
     * SUBMIT: judge every testcase of the current version, store the submission pointing at that version, and
     * record the first ACCEPTED as completion. A judge outage stores nothing and invents no verdict.
     */
    @Transactional
    public SubmissionView submit(UUID actorId, UUID classroomId, UUID practiceId, CodeRequest input) {
        var ctx = student(actorId, classroomId);
        var practice = practice(classroomId, practiceId);
        requireAvailable(practice);
        requireStudentCanWrite(ctx);
        requireLanguage(practice, input.language());
        int version = practice.getTestcaseVersion();
        var all = testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practiceId, version);
        if (all.isEmpty()) throw new ApiException(ErrorCode.PRACTICE_NOT_READY);
        var result = callJudge(practiceId, actorId, () -> judge.submit(new PracticeJudgePort.SubmitRequest(practiceId,
                practice.getProblemType(), input.language(), input.sourceCode(), practice.getTimeLimitMs(),
                practice.getMemoryLimitMb(), version, judgeCases(all))));
        if (result.verdict() == null) throw judgeFailure(practiceId, actorId, new IllegalStateException("judge returned no verdict"));

        var submission = new PracticeSubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setPractice(practice);
        submission.setStudent(ctx.account());
        submission.setTestcaseVersion(version);
        submission.setCode(input.sourceCode());
        submission.setLanguage(input.language());
        submission.setVerdict(result.verdict());
        submission.setRuntimeMs(result.runtimeMs());
        submission.setMemoryKb(result.memoryKb());
        submission.setJudgeDetails(result.details());
        submission.setSubmittedAt(clock.instant());
        submissions.save(submission);

        var completionId = new PracticeCompletionId(actorId, practiceId);
        if (result.verdict() == PracticeVerdict.ACCEPTED && !completions.existsById(completionId)) {
            var completion = new PracticeCompletionEntity();
            completion.setId(completionId);
            completion.setStudent(ctx.account());
            completion.setPractice(practice);
            completion.setFirstAcceptedSubmission(submission);
            completion.setCompletedAt(clock.instant());
            completions.save(completion);
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
        if (!ctx.teacher()) requireHistoryVisible(practice);
        var submission = submissions.findByIdAndPracticeId(submissionId, practiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        if (!ctx.teacher() && !submission.getStudent().getId().equals(actorId)) throw forbidden("Submission belongs to another student");
        return PracticeMapper.submission(submission);
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

    private record Ctx(AccountEntity account, ClassroomEntity classroom, boolean teacher) { }

    /**
     * The owning Teacher (account must be ACTIVE) or an ACTIVE-membership student. A non-active student may still
     * read; {@link #requireStudentCanWrite} blocks RUN/SUBMIT for them.
     */
    private Ctx participant(UUID actorId, UUID classroomId) {
        var account = accounts.findById(actorId).orElseThrow(() -> forbidden("Account access denied"));
        var classroom = classrooms.findById(classroomId).orElseThrow(() -> new ResourceNotFoundException("Classroom", classroomId));
        if (account.getRole() == AccountRole.TEACHER && classroom.getOwner().getId().equals(actorId)) {
            if (account.getStatus() != AccountStatus.ACTIVE) throw forbidden("Inactive accounts cannot perform this operation");
            return new Ctx(account, classroom, true);
        }
        if (account.getRole() == AccountRole.STUDENT && memberships.findByClassroomIdAndStudentId(classroomId, actorId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE).orElse(false)) return new Ctx(account, classroom, false);
        throw forbidden("Only the classroom owner or an active member may access practices");
    }

    private Ctx teacher(UUID actorId, UUID classroomId) {
        var ctx = participant(actorId, classroomId);
        if (!ctx.teacher()) throw forbidden("Only the classroom owner may perform this operation");
        return ctx;
    }

    private Ctx student(UUID actorId, UUID classroomId) {
        var ctx = participant(actorId, classroomId);
        if (ctx.teacher()) throw forbidden("Only a student member may perform this operation");
        return ctx;
    }

    /** RUN/SUBMIT need an ACTIVE student in a classroom that is neither archived nor read-only (owner inactive). */
    private static void requireStudentCanWrite(Ctx ctx) {
        if (ctx.account().getStatus() != AccountStatus.ACTIVE) throw forbidden("Suspended or inactive accounts are read-only");
        if (ctx.classroom().getOwner().getStatus() != AccountStatus.ACTIVE) throw new ApiException(ErrorCode.CLASSROOM_READ_ONLY);
        requireActiveClassroom(ctx.classroom());
    }

    private ClassroomPracticeEntity practice(UUID classroomId, UUID practiceId) {
        return practices.findByIdAndClassroomId(practiceId, classroomId)
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
        if (deadline != null || (maxScore != null && maxScore > 0)) throw new ApiException(ErrorCode.PRACTICE_SCOPE_VIOLATION);
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
        var result = new EnumMap<PracticeLanguage, String>(PracticeLanguage.class);
        if (source != null) {
            source.forEach((language, code) -> {
                if (!allowed.contains(language)) throw new ApiException(ErrorCode.LANGUAGE_NOT_ALLOWED, "Starter code given for a language that is not allowed");
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
