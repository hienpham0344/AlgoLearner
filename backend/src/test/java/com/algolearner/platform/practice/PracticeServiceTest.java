package com.algolearner.platform.practice;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.JudgeServiceUnavailableException;
import com.algolearner.platform.common.exception.ResourceNotAvailableException;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.practice.PracticeDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PracticeServiceTest {
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ClassroomPracticeRepository practices = mock(ClassroomPracticeRepository.class);
    private final PracticeTestcaseRepository testcases = mock(PracticeTestcaseRepository.class);
    private final PracticeSubmissionRepository submissions = mock(PracticeSubmissionRepository.class);
    private final PracticeCompletionRepository completions = mock(PracticeCompletionRepository.class);
    private final PracticeJudgePort judge = mock(PracticeJudgePort.class);
    private final AtomicBoolean lessonArchived = new AtomicBoolean(false);
    private final Set<UUID> archivedLessonIds = new java.util.HashSet<>();
    private final LessonStatusPort lessons = new LessonStatusPort() {
        @Override public boolean isArchived(UUID lessonId) { return lessonArchived.get() || archivedLessonIds.contains(lessonId); }
        @Override public Set<UUID> archivedLessonIds(UUID classroomId) { return Set.copyOf(archivedLessonIds); }
    };
    /** Number of transactions currently open; the judge must be called while this is 0. */
    private final AtomicInteger openTransactions = new AtomicInteger();
    private final PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            openTransactions.incrementAndGet();
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { openTransactions.decrementAndGet(); }
        @Override public void rollback(TransactionStatus status) { openTransactions.decrementAndGet(); }
    };
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T03:30:00Z"), ZoneOffset.UTC);
    private final RunRateLimiter limiter = new RunRateLimiter(clock);
    private final AuditService audit = mock(AuditService.class);
    private PracticeService service;

    private AccountEntity teacher;
    private AccountEntity otherTeacher;
    private AccountEntity student;
    private AccountEntity otherStudent;
    private ClassroomEntity classroom;
    private ClassroomPracticeEntity practice;
    private PracticeTestcaseEntity sample;
    private PracticeTestcaseEntity hidden;

    @BeforeEach
    void setUp() {
        service = new PracticeService(classrooms, memberships, accounts, practices, testcases, submissions, completions,
                judge, lessons, limiter, clock, transactionManager, auditProvider(audit));
        teacher = account(AccountRole.TEACHER);
        otherTeacher = account(AccountRole.TEACHER);
        student = account(AccountRole.STUDENT);
        otherStudent = account(AccountRole.STUDENT);
        classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID());
        classroom.setOwner(teacher);
        classroom.setStatus(ClassroomStatus.ACTIVE);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        member(student);

        practice = new ClassroomPracticeEntity();
        practice.setId(UUID.randomUUID());
        practice.setClassroom(classroom);
        practice.setTitle("Sum");
        practice.setAllowedLanguages(Set.of(PracticeLanguage.JAVA));
        practice.setStatus(PracticeStatus.PUBLISHED);
        when(practices.findByIdAndClassroomId(practice.getId(), classroom.getId())).thenReturn(Optional.of(practice));
        sample = testcase("1 2", "3", true, 0, 1);
        hidden = testcase("SECRET-IN", "SECRET-OUT", false, 1, 1);
        stubVersion(1, sample, hidden);
        when(practices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(practices.lockByIdAndClassroomId(practice.getId(), classroom.getId())).thenReturn(Optional.of(practice));
        when(testcases.countByPracticeIdAndVersionAndSample(eq(practice.getId()), anyInt(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(1L);
    }

    // ================================================================ PRC-01 (student)

    @Test @DisplayName("TC-PRC-01-01 RUN with sample and custom input returns feedback and persists nothing")
    void tcPrc0101_run() {
        when(judge.run(any())).thenReturn(new PracticeJudgePort.RunResult(
                List.of(new PracticeJudgePort.CaseResult(0, PracticeVerdict.ACCEPTED, "3", "", 4))));

        var onSamples = service.run(student.getId(), classroom.getId(), practice.getId(), run(null));
        var onCustom = service.run(student.getId(), classroom.getId(), practice.getId(), run("9 9"));

        assertThat(onSamples.cases()).singleElement().satisfies(c -> assertThat(c.expectedOutput()).isEqualTo("3"));
        assertThat(onCustom.cases()).singleElement().satisfies(c -> {
            assertThat(c.input()).isEqualTo("9 9");
            assertThat(c.expectedOutput()).isNull();
        });
        var requests = ArgumentCaptor.forClass(PracticeJudgePort.RunRequest.class);
        verify(judge, times(2)).run(requests.capture());
        assertThat(requests.getAllValues().get(0).testcases()).extracting(PracticeJudgePort.JudgeCase::input).containsExactly("1 2");
        assertThat(requests.getAllValues().get(1).testcases()).extracting(PracticeJudgePort.JudgeCase::input).containsExactly("9 9");
        verifyNoInteractions(submissions, completions);
        verify(testcases, never()).findByPracticeIdAndVersionOrderByOrderIndexAsc(any(), anyInt());
    }

    @Test @DisplayName("TC-PRC-01-01 custom input over 64 KB is rejected before reaching the judge")
    void tcPrc0101_customInputIsBounded() {
        String tooBig = "x".repeat(PracticeService.MAX_CUSTOM_INPUT_BYTES + 1);
        assertThatThrownBy(() -> service.run(student.getId(), classroom.getId(), practice.getId(), run(tooBig)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("VALIDATION_FAILED"));
        verifyNoInteractions(judge, submissions, completions);
    }

    @Test @DisplayName("TC-PRC-01-01 RUN is limited to 5 per minute per user")
    void tcPrc0101_rateLimit() {
        when(judge.run(any())).thenReturn(new PracticeJudgePort.RunResult(
                List.of(new PracticeJudgePort.CaseResult(0, PracticeVerdict.ACCEPTED, "3", "", 4))));
        for (int i = 0; i < 5; i++) service.run(student.getId(), classroom.getId(), practice.getId(), run(null));

        assertThatThrownBy(() -> service.run(student.getId(), classroom.getId(), practice.getId(), run(null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED"));
        verify(judge, times(5)).run(any());
    }

    @Test @DisplayName("TC-PRC-01-02 SUBMIT judges the current testcase version, stores it and completes on ACCEPTED")
    void tcPrc0102_submit() {
        practice.setTestcaseVersion(3);
        var s3 = testcase("1 2", "3", true, 0, 3);
        var h3 = testcase("H3-IN", "H3-OUT", false, 1, 3);
        stubVersion(3, s3, h3);
        when(judge.submit(any())).thenReturn(new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 2, 12, 2048, "{\"ok\":true}"));

        var view = service.submit(student.getId(), classroom.getId(), practice.getId(), code());

        assertThat(view.verdict()).isEqualTo(PracticeVerdict.ACCEPTED);
        assertThat(view.testcaseVersion()).isEqualTo(3);
        var request = ArgumentCaptor.forClass(PracticeJudgePort.SubmitRequest.class);
        verify(judge).submit(request.capture());
        assertThat(request.getValue().testcaseVersion()).isEqualTo(3);
        assertThat(request.getValue().testcases()).extracting(PracticeJudgePort.JudgeCase::input).containsExactly("1 2", "H3-IN");
        var stored = ArgumentCaptor.forClass(PracticeSubmissionEntity.class);
        verify(submissions).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getTestcaseVersion()).isEqualTo(3);
        assertThat(stored.getValue().getJudgeDetails()).isEqualTo("{\"ok\":true}");
        // FR-PRC-004: tests passed is stored next to how many testcases the snapshot had
        assertThat(stored.getValue().getTestsPassed()).isEqualTo(2);
        assertThat(stored.getValue().getTotalTests()).isEqualTo(2);
        assertThat(view.testsPassed()).isEqualTo(2);
        assertThat(view.totalTests()).isEqualTo(2);
        verify(completions).insertIfAbsent(student.getId(), practice.getId(), stored.getValue().getId(), clock.instant());
    }

    @Test @DisplayName("TC-PRC-01-03 unlimited attempts: every submission is stored, none is blocked")
    void tcPrc0103_unlimitedAttempts() {
        when(judge.submit(any())).thenReturn(new PracticeJudgePort.SubmitResult(PracticeVerdict.WRONG_ANSWER, 1, 5, 1024, null));
        for (int i = 0; i < 12; i++) service.submit(student.getId(), classroom.getId(), practice.getId(), code());

        verify(submissions, times(12)).saveAndFlush(any());
        verify(completions, never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test @DisplayName("TC-PRC-01-04 only ACCEPTED completes; completion is one atomic upsert, per student")
    void tcPrc0104_completion() {
        when(judge.submit(any())).thenReturn(new PracticeJudgePort.SubmitResult(PracticeVerdict.TIME_LIMIT_EXCEEDED, 1, 2000, 1024, null));
        var tle = service.submit(student.getId(), classroom.getId(), practice.getId(), code());
        assertThat(tle.verdict()).isEqualTo(PracticeVerdict.TIME_LIMIT_EXCEEDED);
        verify(completions, never()).insertIfAbsent(any(), any(), any(), any());

        when(judge.submit(any())).thenReturn(new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 2, 5, 1024, null));
        service.submit(student.getId(), classroom.getId(), practice.getId(), code());
        verify(completions, times(1)).insertIfAbsent(eq(student.getId()), eq(practice.getId()), any(), any());

        // No read-then-write: a second ACCEPTED just issues the same idempotent insert (the DB keeps the first one).
        service.submit(student.getId(), classroom.getId(), practice.getId(), code());
        verify(completions, times(2)).insertIfAbsent(eq(student.getId()), eq(practice.getId()), any(), any());
        verify(completions, never()).existsById(any());
        verify(completions, never()).save(any());

        member(otherStudent);
        service.submit(otherStudent.getId(), classroom.getId(), practice.getId(), code());
        verify(completions, times(1)).insertIfAbsent(eq(otherStudent.getId()), eq(practice.getId()), any(), any());
    }

    @Test @DisplayName("TC-PRC-01-05 DRAFT practice or archived lesson rejects RUN/SUBMIT, history stays")
    void tcPrc0105_unavailable() {
        practice.setStatus(PracticeStatus.DRAFT);
        assertThatThrownBy(() -> service.run(student.getId(), classroom.getId(), practice.getId(), run(null))).isInstanceOf(ResourceNotAvailableException.class);
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code())).isInstanceOf(ResourceNotAvailableException.class);
        assertThatThrownBy(() -> service.get(student.getId(), classroom.getId(), practice.getId())).isInstanceOf(ResourceNotAvailableException.class);

        practice.setStatus(PracticeStatus.PUBLISHED);
        practice.setLessonId(UUID.randomUUID());
        lessonArchived.set(true);
        assertThatThrownBy(() -> service.run(student.getId(), classroom.getId(), practice.getId(), run(null))).isInstanceOf(ResourceNotAvailableException.class);
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code())).isInstanceOf(ResourceNotAvailableException.class);
        verifyNoInteractions(judge, completions);
        verify(submissions, never()).saveAndFlush(any());

        // earlier submissions remain readable after the lesson is archived
        var old = submissionOf(student);
        when(submissions.findByIdAndPracticeId(old.getId(), practice.getId())).thenReturn(Optional.of(old));
        assertThat(service.submission(student.getId(), classroom.getId(), practice.getId(), old.getId()).id()).isEqualTo(old.getId());
    }

    @Test @DisplayName("TC-PRC-01-06 suspended student reaches nothing (FR-CLS-009); read-only class: reads work, RUN/SUBMIT are 403")
    void tcPrc0106_readOnly() {
        student.setStatus(AccountStatus.SUSPENDED);
        assertForbidden(() -> service.get(student.getId(), classroom.getId(), practice.getId()));
        assertForbidden(() -> service.list(student.getId(), classroom.getId(), PageRequest.of(0, 10)));
        assertForbidden(() -> service.mySubmissions(student.getId(), classroom.getId(), practice.getId(), PageRequest.of(0, 10)));
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("FORBIDDEN_OPERATION"));
        assertThatThrownBy(() -> service.run(student.getId(), classroom.getId(), practice.getId(), run(null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("FORBIDDEN_OPERATION"));

        student.setStatus(AccountStatus.ACTIVE);
        teacher.setStatus(AccountStatus.SUSPENDED);
        assertThat(service.get(student.getId(), classroom.getId(), practice.getId()).id()).isEqualTo(practice.getId());
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("CLASSROOM_READ_ONLY"));
        verifyNoInteractions(judge);
        verify(completions, never()).insertIfAbsent(any(), any(), any(), any());
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test @DisplayName("TC-PRC-01-07 a student outside the class (or who left) gets 403")
    void tcPrc0107_nonMember() {
        assertForbidden(() -> service.get(otherStudent.getId(), classroom.getId(), practice.getId()));
        assertForbidden(() -> service.list(otherStudent.getId(), classroom.getId(), null));
        assertForbidden(() -> service.run(otherStudent.getId(), classroom.getId(), practice.getId(), run(null)));
        var left = new ClassroomMembershipEntity();
        left.setStatus(MembershipStatus.LEFT);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), otherStudent.getId())).thenReturn(Optional.of(left));
        assertForbidden(() -> service.submit(otherStudent.getId(), classroom.getId(), practice.getId(), code()));
        verifyNoInteractions(judge);
    }

    @Test @DisplayName("TC-PRC-01-08 judge outage: 503, technical log, no fake verdict, nothing stored")
    void tcPrc0108_judgeUnavailable() {
        doThrow(new RuntimeException("connection timed out")).when(judge).submit(any());
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOf(JudgeServiceUnavailableException.class);

        doThrow(new JudgeServiceUnavailableException("sandbox crashed")).when(judge).submit(any());
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOf(JudgeServiceUnavailableException.class).hasMessage("sandbox crashed");

        doReturn(null).when(judge).submit(any());
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOf(JudgeServiceUnavailableException.class);

        doReturn(new PracticeJudgePort.SubmitResult(null, 0, null, null, null)).when(judge).submit(any());
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOf(JudgeServiceUnavailableException.class);

        doThrow(new RuntimeException("boom")).when(judge).run(any());
        assertThatThrownBy(() -> service.run(student.getId(), classroom.getId(), practice.getId(), run(null)))
                .isInstanceOf(JudgeServiceUnavailableException.class);

        verify(submissions, never()).saveAndFlush(any());
        verify(completions, never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test
    void languageOutsideAllowedSetIsRejected() {
        var python = new CodeRequest(PracticeLanguage.PYTHON, "print(3)");
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), python))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("LANGUAGE_NOT_ALLOWED"));
        verifyNoInteractions(judge);
    }

    @Test
    void studentDetailLoadsOnlySampleTestcases() {
        var detail = service.get(student.getId(), classroom.getId(), practice.getId());

        assertThat(detail.sampleTestcases()).extracting(SampleTestcase::input).containsExactly("1 2");
        assertThat(detail.toString()).doesNotContain("SECRET");
        verify(testcases, never()).findByPracticeIdAndVersionOrderByOrderIndexAsc(any(), anyInt());
    }

    @Test
    void studentCannotReadAnotherStudentsSubmission() {
        member(otherStudent);
        var other = submissionOf(otherStudent);
        when(submissions.findByIdAndPracticeId(other.getId(), practice.getId())).thenReturn(Optional.of(other));

        assertForbidden(() -> service.submission(student.getId(), classroom.getId(), practice.getId(), other.getId()));
        assertThat(service.submission(teacher.getId(), classroom.getId(), practice.getId(), other.getId()).studentId()).isEqualTo(otherStudent.getId());
    }

    // ================================================================ PRC-10 (teacher)

    @Test @DisplayName("TC-PRC-10-01 create stores defaults, starter code, sample/hidden split, starts as DRAFT")
    void tcPrc1001_create() {
        var input = new CreatePractice("Sum of two", "Add", null, null, Set.of(PracticeLanguage.JAVA, PracticeLanguage.PYTHON),
                Map.of(PracticeLanguage.JAVA, "class Main {}"), null, null, null,
                List.of(new TestcaseInput("1 2", "3", true, null), new TestcaseInput("5 5", "10", false, null)), null, null);

        var detail = service.create(teacher.getId(), classroom.getId(), input);

        assertThat(detail.status()).isEqualTo(PracticeStatus.DRAFT);
        assertThat(detail.timeLimitMs()).isEqualTo(2000);
        assertThat(detail.memoryLimitMb()).isEqualTo(256);
        assertThat(detail.problemType()).isEqualTo(PracticeProblemType.STANDARD_IO);
        assertThat(detail.testcaseVersion()).isEqualTo(1);
        assertThat(detail.starterCode()).containsEntry(PracticeLanguage.JAVA, "class Main {}");
        var saved = ArgumentCaptor.forClass(PracticeTestcaseEntity.class);
        verify(testcases, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PracticeTestcaseEntity::isSample).containsExactly(true, false);
        assertThat(saved.getAllValues()).extracting(PracticeTestcaseEntity::getVersion).containsOnly(1);
    }

    @Test @DisplayName("TC-PRC-10-01 starter code for a language that is not allowed is rejected")
    void tcPrc1001_starterCodeMustMatchLanguages() {
        var input = new CreatePractice("T", null, PracticeProblemType.FUNCTION_METHOD, null, Set.of(PracticeLanguage.JAVA),
                Map.of(PracticeLanguage.PYTHON, "pass"), null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(teacher.getId(), classroom.getId(), input))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("LANGUAGE_NOT_ALLOWED"));
    }

    @Test @DisplayName("TC-PRC-10-02 lesson link: archived lesson makes the practice unavailable")
    void tcPrc1002_lessonLink() {
        var lessonId = UUID.randomUUID();
        var update = new UpdatePractice(null, null, null, lessonId, null, null, null, null, null, null, null);

        var linked = service.update(teacher.getId(), classroom.getId(), practice.getId(), update);
        assertThat(linked.lessonId()).isEqualTo(lessonId);
        assertThat(linked.available()).isTrue();

        lessonArchived.set(true);
        assertThat(service.get(teacher.getId(), classroom.getId(), practice.getId()).available()).isFalse();
        assertThat(practice.getStatus()).isEqualTo(PracticeStatus.PUBLISHED);
    }

    @Test @DisplayName("TC-PRC-10-03 editing a testcase used by submissions bumps the version; old rows are untouched")
    void tcPrc1003_versioning() {
        when(submissions.existsByPracticeIdAndTestcaseVersion(practice.getId(), 1)).thenReturn(true);
        when(testcases.findByIdAndPracticeIdAndVersion(hidden.getId(), practice.getId(), 1)).thenReturn(Optional.of(hidden));

        var edited = service.updateTestcase(teacher.getId(), classroom.getId(), practice.getId(), hidden.getId(),
                new TestcaseInput("NEW-IN", "NEW-OUT", false, null), true);

        assertThat(practice.getTestcaseVersion()).isEqualTo(2);
        assertThat(edited.version()).isEqualTo(2);
        assertThat(edited.id()).isNotEqualTo(hidden.getId());
        assertThat(hidden.getInput()).isEqualTo("SECRET-IN");
        assertThat(hidden.getVersion()).isEqualTo(1);
        var copies = ArgumentCaptor.forClass(PracticeTestcaseEntity.class);
        verify(testcases, times(2)).save(copies.capture());
        assertThat(copies.getAllValues()).extracting(PracticeTestcaseEntity::getVersion).containsOnly(2);
        verify(submissions).markForRejudge(practice.getId(), 2, RejudgeStatus.PENDING_REJUDGE);
    }

    @Test @DisplayName("TC-PRC-10-03 without a rejudge request old submissions are not flagged; unused version edits in place")
    void tcPrc1003_noRejudgeAndInPlace() {
        when(testcases.findByIdAndPracticeIdAndVersion(hidden.getId(), practice.getId(), 1)).thenReturn(Optional.of(hidden));

        service.updateTestcase(teacher.getId(), classroom.getId(), practice.getId(), hidden.getId(), new TestcaseInput("A", "B", false, null), false);

        assertThat(practice.getTestcaseVersion()).isEqualTo(1);
        assertThat(hidden.getInput()).isEqualTo("A");
        verify(submissions, never()).markForRejudge(any(), anyInt(), any());

        when(submissions.existsByPracticeIdAndTestcaseVersion(practice.getId(), 1)).thenReturn(true);
        service.addTestcase(teacher.getId(), classroom.getId(), practice.getId(), new TestcaseInput("C", "D", false, null), false);
        assertThat(practice.getTestcaseVersion()).isEqualTo(2);
        verify(submissions, never()).markForRejudge(any(), anyInt(), any());
    }

    @Test @DisplayName("TC-PRC-10-04 a teacher who does not own the class, and students, get 403")
    void tcPrc1004_idor() {
        var create = new CreatePractice("T", null, null, null, Set.of(PracticeLanguage.JAVA), null, null, null, null, null, null, null);
        assertForbidden(() -> service.create(otherTeacher.getId(), classroom.getId(), create));
        assertForbidden(() -> service.update(otherTeacher.getId(), classroom.getId(), practice.getId(), new UpdatePractice("x", null, null, null, null, null, null, null, null, null, null)));
        assertForbidden(() -> service.changeStatus(otherTeacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.ARCHIVED)));
        assertForbidden(() -> service.delete(otherTeacher.getId(), classroom.getId(), practice.getId()));
        assertForbidden(() -> service.listTestcases(otherTeacher.getId(), classroom.getId(), practice.getId()));
        assertForbidden(() -> service.classSubmissions(otherTeacher.getId(), classroom.getId(), practice.getId(), null, null));
        assertForbidden(() -> service.create(student.getId(), classroom.getId(), create));
        assertForbidden(() -> service.listTestcases(student.getId(), classroom.getId(), practice.getId()));
        assertForbidden(() -> service.run(teacher.getId(), classroom.getId(), practice.getId(), run(null)));
        verify(practices, never()).save(any());
    }

    @Test @DisplayName("TC-PRC-10-05 deadline or maxScore > 0 is rejected as 400; maxScore 0 is fine")
    void tcPrc1005_ungradedAndNoDeadline() {
        var withDeadline = new CreatePractice("T", null, null, null, Set.of(PracticeLanguage.JAVA), null, null, null, null, null, Instant.parse("2026-12-01T00:00:00Z"), null);
        var withScore = new CreatePractice("T", null, null, null, Set.of(PracticeLanguage.JAVA), null, null, null, null, null, null, 10);
        for (var bad : List.of(withDeadline, withScore)) {
            assertThatThrownBy(() -> service.create(teacher.getId(), classroom.getId(), bad))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_SCOPE_VIOLATION"));
        }
        assertThatThrownBy(() -> service.update(teacher.getId(), classroom.getId(), practice.getId(),
                new UpdatePractice(null, null, null, null, null, null, null, null, null, null, 5)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_SCOPE_VIOLATION"));
        verify(practices, never()).save(any());

        var zero = new CreatePractice("T", null, null, null, Set.of(PracticeLanguage.JAVA), null, null, null, null, null, null, 0);
        assertThat(service.create(teacher.getId(), classroom.getId(), zero).status()).isEqualTo(PracticeStatus.DRAFT);
    }

    @Test @DisplayName("TC-PRC-10-06 archiving changes status only: submissions and completions are not touched")
    void tcPrc1006_archiveKeepsData() {
        var detail = service.changeStatus(teacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.ARCHIVED));

        assertThat(detail.status()).isEqualTo(PracticeStatus.ARCHIVED);
        verifyNoInteractions(submissions, completions);
        verify(testcases, never()).deleteByPracticeId(any());
        verify(practices, never()).delete(any());
    }

    @Test
    void publishNeedsSampleAndHiddenTestcaseOfCurrentVersion() {
        practice.setStatus(PracticeStatus.DRAFT);
        when(testcases.countByPracticeIdAndVersionAndSample(practice.getId(), 1, true)).thenReturn(1L);
        when(testcases.countByPracticeIdAndVersionAndSample(practice.getId(), 1, false)).thenReturn(0L);

        assertThatThrownBy(() -> service.changeStatus(teacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.PUBLISHED)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_NOT_READY"));
        assertThat(practice.getStatus()).isEqualTo(PracticeStatus.DRAFT);
    }

    @Test
    void statusCanOnlyMoveForwardAndOnlyDraftsAreDeleted() {
        practice.setStatus(PracticeStatus.ARCHIVED);
        assertThatThrownBy(() -> service.changeStatus(teacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.PUBLISHED)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_INVALID_STATUS_TRANSITION"));
        assertThatThrownBy(() -> service.delete(teacher.getId(), classroom.getId(), practice.getId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_NOT_DELETABLE"));
        practice.setStatus(PracticeStatus.DRAFT);
        service.delete(teacher.getId(), classroom.getId(), practice.getId());
        verify(practices).delete(practice);
    }

    @Test
    void archivedClassroomIsReadOnly() {
        classroom.setStatus(ClassroomStatus.ARCHIVED);
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("CLASSROOM_ARCHIVED"));
        verifyNoInteractions(judge);
    }

    @Test
    void unknownTestcaseIsNotFound() {
        assertThatThrownBy(() -> service.deleteTestcase(teacher.getId(), classroom.getId(), practice.getId(), UUID.randomUUID(), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================ review fixes

    @Test @DisplayName("SUBMIT/RUN call the judge with no transaction (JDBC connection) open, and write in a short transaction after")
    void judgeRunsOutsideAnyTransaction() {
        var seenOnSubmit = new AtomicInteger(-1);
        var seenOnRun = new AtomicInteger(-1);
        when(judge.submit(any())).thenAnswer(i -> {
            seenOnSubmit.set(openTransactions.get());
            return new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 2, 5, 1024, null);
        });
        when(judge.run(any())).thenAnswer(i -> {
            seenOnRun.set(openTransactions.get());
            return new PracticeJudgePort.RunResult(List.of(new PracticeJudgePort.CaseResult(0, PracticeVerdict.ACCEPTED, "3", "", 4)));
        });

        service.submit(student.getId(), classroom.getId(), practice.getId(), code());
        service.run(student.getId(), classroom.getId(), practice.getId(), run(null));

        assertThat(seenOnSubmit.get()).isZero();
        assertThat(seenOnRun.get()).isZero();
        assertThat(openTransactions.get()).isZero();
    }

    @Test @DisplayName("SUBMIT stores under the practice row lock and refuses a result judged against stale testcases (409)")
    void submitRechecksTestcasesUnderLock() {
        when(judge.submit(any())).thenAnswer(invocation -> {
            // the teacher edits a hidden testcase in place while the judge is running
            hidden.setExpectedOutput("CHANGED");
            return new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 2, 5, 1024, null);
        });
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_TESTCASES_CHANGED"));
        verify(practices).lockByIdAndClassroomId(practice.getId(), classroom.getId());
        verify(submissions, never()).saveAndFlush(any());
        verify(completions, never()).insertIfAbsent(any(), any(), any(), any());

        hidden.setExpectedOutput("SECRET-OUT");
        when(judge.submit(any())).thenAnswer(invocation -> {
            practice.setTestcaseVersion(2); // a new version opened while judging: the rejudge flag would be missed
            return new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 2, 5, 1024, null);
        });
        assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_TESTCASES_CHANGED"));
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test @DisplayName("SUBMIT: a student suspended or an archive made while judging is honoured when storing")
    void submitRechecksAccessWhenStoring() {
        when(judge.submit(any())).thenAnswer(invocation -> {
            student.setStatus(AccountStatus.SUSPENDED);
            return new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 2, 5, 1024, null);
        });
        assertForbidden(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()));
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test @DisplayName("testcase add/update/delete lock the practice row before versioning")
    void testcaseEditsTakeThePracticeLock() {
        when(testcases.findByIdAndPracticeIdAndVersion(hidden.getId(), practice.getId(), 1)).thenReturn(Optional.of(hidden));
        service.addTestcase(teacher.getId(), classroom.getId(), practice.getId(), new TestcaseInput("C", "D", false, null), false);
        service.updateTestcase(teacher.getId(), classroom.getId(), practice.getId(), hidden.getId(), new TestcaseInput("A", "B", false, null), false);
        service.deleteTestcase(teacher.getId(), classroom.getId(), practice.getId(), hidden.getId(), false);

        verify(practices, times(3)).lockByIdAndClassroomId(practice.getId(), classroom.getId());
        verify(practices, never()).findByIdAndClassroomId(any(), any());
    }

    @Test @DisplayName("INTERNAL_ERROR, no verdict or an inconsistent tests-passed count never become a submission")
    void unusableSubmitResultsAreJudgeFailures() {
        var bad = List.of(
                new PracticeJudgePort.SubmitResult(PracticeVerdict.INTERNAL_ERROR, 0, 1, 1, "oom"),
                new PracticeJudgePort.SubmitResult(PracticeVerdict.WRONG_ANSWER, -1, 1, 1, null),
                new PracticeJudgePort.SubmitResult(PracticeVerdict.WRONG_ANSWER, 3, 1, 1, null),
                new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 1, 1, 1, null));
        for (var result : bad) {
            when(judge.submit(any())).thenReturn(result);
            assertThatThrownBy(() -> service.submit(student.getId(), classroom.getId(), practice.getId(), code()))
                    .isInstanceOf(JudgeServiceUnavailableException.class);
        }
        verify(submissions, never()).saveAndFlush(any());
        verify(completions, never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test @DisplayName("RUN must return one result per case, with unique known indexes and no INTERNAL_ERROR")
    void incompleteRunResultsAreJudgeFailures() {
        member(otherStudent);
        var ok = new PracticeJudgePort.CaseResult(0, PracticeVerdict.ACCEPTED, "3", "", 4);
        var results = List.of(
                new PracticeJudgePort.RunResult(List.of()),
                new PracticeJudgePort.RunResult(List.of(ok, ok)),
                new PracticeJudgePort.RunResult(List.of(new PracticeJudgePort.CaseResult(1, PracticeVerdict.ACCEPTED, "3", "", 4))),
                new PracticeJudgePort.RunResult(List.of(new PracticeJudgePort.CaseResult(0, PracticeVerdict.INTERNAL_ERROR, "", "", 0))));
        for (var result : results) {
            when(judge.run(any())).thenReturn(result);
            assertThatThrownBy(() -> service.run(student.getId(), classroom.getId(), practice.getId(), run(null)))
                    .isInstanceOf(JudgeServiceUnavailableException.class);
        }

        // two sample testcases: both must come back, in any order, and are returned sorted by index
        var second = testcase("4 5", "9", true, 1, 1);
        stubVersion(1, sample, second, hidden);
        when(judge.run(any())).thenReturn(new PracticeJudgePort.RunResult(List.of(
                new PracticeJudgePort.CaseResult(1, PracticeVerdict.WRONG_ANSWER, "8", "", 4), new PracticeJudgePort.CaseResult(0, PracticeVerdict.ACCEPTED, "3", "", 4))));
        var response = service.run(otherStudent.getId(), classroom.getId(), practice.getId(), run(null));
        assertThat(response.cases()).extracting(RunCaseResult::index).containsExactly(0, 1);
        assertThat(response.cases()).extracting(RunCaseResult::expectedOutput).containsExactly("3", "9");
        assertThat(response.verdict()).isEqualTo(PracticeVerdict.WRONG_ANSWER);
    }

    @Test @DisplayName("negative maxScore is as invalid as a positive one")
    void negativeMaxScoreIsRejected() {
        var negative = new CreatePractice("T", null, null, null, Set.of(PracticeLanguage.JAVA), null, null, null, null, null, null, -10);
        assertThatThrownBy(() -> service.create(teacher.getId(), classroom.getId(), negative))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_SCOPE_VIOLATION"));
        assertThatThrownBy(() -> service.update(teacher.getId(), classroom.getId(), practice.getId(),
                new UpdatePractice(null, null, null, null, null, null, null, null, null, null, -1)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRACTICE_SCOPE_VIOLATION"));
    }

    @Test @DisplayName("PATCH: clearLessonId detaches the lesson; removing a language drops its starter code")
    void patchDetachesLessonAndPrunesStarterCode() {
        practice.setLessonId(UUID.randomUUID());
        practice.setAllowedLanguages(EnumSet.of(PracticeLanguage.JAVA, PracticeLanguage.PYTHON));
        practice.setStarterCode(new java.util.EnumMap<>(Map.of(PracticeLanguage.JAVA, "class Main {}", PracticeLanguage.PYTHON, "pass")));

        var detail = service.update(teacher.getId(), classroom.getId(), practice.getId(),
                new UpdatePractice(null, null, null, null, true, Set.of(PracticeLanguage.JAVA), null, null, null, null, null));

        assertThat(detail.lessonId()).isNull();
        assertThat(detail.allowedLanguages()).containsExactly(PracticeLanguage.JAVA);
        assertThat(detail.starterCode()).containsOnlyKeys(PracticeLanguage.JAVA);
        assertThatThrownBy(() -> service.update(teacher.getId(), classroom.getId(), practice.getId(),
                new UpdatePractice(null, null, null, UUID.randomUUID(), true, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("VALIDATION_FAILED"));
        // starter code sent together with the new language set is validated against the new set
        assertThatThrownBy(() -> service.update(teacher.getId(), classroom.getId(), practice.getId(),
                new UpdatePractice(null, null, null, null, null, Set.of(PracticeLanguage.JAVA), Map.of(PracticeLanguage.PYTHON, "pass"), null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("LANGUAGE_NOT_ALLOWED"));
    }

    @Test @DisplayName("student list filters archived-lesson practices in the query, not after paging")
    void studentListExcludesArchivedLessonsInSql() {
        var pageable = PageRequest.of(0, 2);
        var lesson = UUID.randomUUID();
        var other = new ClassroomPracticeEntity();
        other.setId(UUID.randomUUID());
        other.setClassroom(classroom);
        other.setTitle("Other");
        other.setStatus(PracticeStatus.PUBLISHED);
        other.setAllowedLanguages(Set.of(PracticeLanguage.JAVA));
        when(practices.findByClassroomIdAndStatus(classroom.getId(), PracticeStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(practice, other), pageable, 2));
        when(completions.findForStudent(eq(student.getId()), any())).thenReturn(List.of());

        // no archived lessons: the plain query, nothing excluded
        assertThat(service.list(student.getId(), classroom.getId(), pageable).getData().getItems()).hasSize(2);
        verify(practices, never()).findVisible(any(), any(), any(), any());

        archivedLessonIds.add(lesson);
        when(practices.findVisible(classroom.getId(), PracticeStatus.PUBLISHED, Set.of(lesson), pageable))
                .thenReturn(new PageImpl<>(List.of(practice), pageable, 1));
        var page = service.list(student.getId(), classroom.getId(), pageable);

        assertThat(page.getData().getItems()).extracting(PracticeSummary::id).containsExactly(practice.getId());
        assertThat(page.getData().getPagination().getTotalElements()).isEqualTo(1);
        verify(practices).findVisible(classroom.getId(), PracticeStatus.PUBLISHED, Set.of(lesson), pageable);
    }

    @Test @DisplayName("empty pages skip the completion lookups (no IN () query)")
    void emptyPagesSkipCompletionQueries() {
        var pageable = PageRequest.of(0, 2);
        when(practices.findByClassroomIdAndStatus(classroom.getId(), PracticeStatus.PUBLISHED, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(memberships.findByClassroomIdAndStatus(classroom.getId(), MembershipStatus.ACTIVE, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service.list(student.getId(), classroom.getId(), pageable).getData().getItems()).isEmpty();
        assertThat(service.progress(teacher.getId(), classroom.getId(), practice.getId(), pageable).getData().getItems()).isEmpty();

        verify(completions, never()).findForStudent(any(), any());
        verify(completions, never()).findForPractice(any(), any());
    }

    @Test @DisplayName("Admin reads practice data of any classroom, but never runs, submits, edits or sees hidden testcases")
    void adminIsReadOnly() {
        var admin = account(AccountRole.ADMIN);
        var other = submissionOf(student);
        when(submissions.findByIdAndPracticeId(other.getId(), practice.getId())).thenReturn(Optional.of(other));
        var pageable = PageRequest.of(0, 10);
        when(practices.findByClassroomId(classroom.getId(), pageable)).thenReturn(new PageImpl<>(List.of(practice), pageable, 1));
        when(submissions.findByPracticeId(practice.getId(), pageable)).thenReturn(new PageImpl<>(List.of(other), pageable, 1));
        when(memberships.findByClassroomIdAndStatus(classroom.getId(), MembershipStatus.ACTIVE, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service.list(admin.getId(), classroom.getId(), pageable).getData().getItems()).hasSize(1);
        assertThat(service.get(admin.getId(), classroom.getId(), practice.getId()).id()).isEqualTo(practice.getId());
        assertThat(service.get(admin.getId(), classroom.getId(), practice.getId()).sampleTestcases()).extracting(SampleTestcase::input).containsExactly("1 2");
        assertThat(service.classSubmissions(admin.getId(), classroom.getId(), practice.getId(), null, pageable).getData().getItems()).hasSize(1);
        assertThat(service.submission(admin.getId(), classroom.getId(), practice.getId(), other.getId()).studentId()).isEqualTo(student.getId());
        assertThat(service.progress(admin.getId(), classroom.getId(), practice.getId(), pageable).getData().getItems()).isEmpty();

        assertForbidden(() -> service.listTestcases(admin.getId(), classroom.getId(), practice.getId()));
        assertForbidden(() -> service.run(admin.getId(), classroom.getId(), practice.getId(), run(null)));
        assertForbidden(() -> service.submit(admin.getId(), classroom.getId(), practice.getId(), code()));
        assertForbidden(() -> service.mySubmissions(admin.getId(), classroom.getId(), practice.getId(), pageable));
        assertForbidden(() -> service.changeStatus(admin.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.ARCHIVED)));
        assertForbidden(() -> service.delete(admin.getId(), classroom.getId(), practice.getId()));
        assertForbidden(() -> service.addTestcase(admin.getId(), classroom.getId(), practice.getId(), new TestcaseInput("a", "b", false, null), false));
        verifyNoInteractions(judge);

        admin.setStatus(AccountStatus.SUSPENDED);
        assertForbidden(() -> service.get(admin.getId(), classroom.getId(), practice.getId()));
    }

    // ================================================================ audit (SRS 4.16)

    @Test @DisplayName("sensitive teacher actions are audited with states only: create, publish, archive, delete, version, rejudge")
    void sensitiveActionsAreAudited() {
        var draft = service.create(teacher.getId(), classroom.getId(), new CreatePractice("T", null, null, null, Set.of(PracticeLanguage.JAVA),
                null, null, null, null, List.of(new TestcaseInput("1", "2", true, null), new TestcaseInput("3", "4", false, null)), null, null));
        verify(audit).record(teacher.getId(), "TEACHER", "CLASSROOM_PRACTICE", draft.id().toString(), "PRACTICE_CREATE", null, "DRAFT", null, null);

        service.changeStatus(teacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.ARCHIVED));
        verify(audit).record(teacher.getId(), "TEACHER", "CLASSROOM_PRACTICE", practice.getId().toString(), "PRACTICE_ARCHIVE", "PUBLISHED", "ARCHIVED", null, null);

        practice.setStatus(PracticeStatus.DRAFT);
        service.changeStatus(teacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.PUBLISHED));
        verify(audit).record(teacher.getId(), "TEACHER", "CLASSROOM_PRACTICE", practice.getId().toString(), "PRACTICE_PUBLISH", "DRAFT", "PUBLISHED", null, null);

        practice.setStatus(PracticeStatus.DRAFT);
        service.delete(teacher.getId(), classroom.getId(), practice.getId());
        verify(audit).record(teacher.getId(), "TEACHER", "CLASSROOM_PRACTICE", practice.getId().toString(), "PRACTICE_DELETE", "DRAFT", null, null, null);

        practice.setStatus(PracticeStatus.PUBLISHED);
        when(submissions.existsByPracticeIdAndTestcaseVersion(practice.getId(), 1)).thenReturn(true);
        when(submissions.markForRejudge(practice.getId(), 2, RejudgeStatus.PENDING_REJUDGE)).thenReturn(3);
        service.addTestcase(teacher.getId(), classroom.getId(), practice.getId(), new TestcaseInput("C", "D", false, null), true);
        verify(audit).record(teacher.getId(), "TEACHER", "CLASSROOM_PRACTICE", practice.getId().toString(), "PRACTICE_TESTCASE_VERSION", "v1", "v2", null, null);
        verify(audit).record(teacher.getId(), "TEACHER", "CLASSROOM_PRACTICE", practice.getId().toString(), "PRACTICE_REJUDGE_REQUESTED", null, "submissions=3", null, null);
    }

    @Test @DisplayName("a student's RUN/SUBMIT and a rejected teacher action write no audit row; no audit module (mock profile) is fine")
    void auditIsLimitedAndOptional() {
        when(judge.submit(any())).thenReturn(new PracticeJudgePort.SubmitResult(PracticeVerdict.ACCEPTED, 2, 5, 1024, null));
        service.submit(student.getId(), classroom.getId(), practice.getId(), code());
        assertForbidden(() -> service.changeStatus(otherTeacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.ARCHIVED)));
        verifyNoInteractions(audit);

        var withoutAudit = new PracticeService(classrooms, memberships, accounts, practices, testcases, submissions, completions,
                judge, lessons, limiter, clock, transactionManager, auditProvider(null));
        assertThat(withoutAudit.changeStatus(teacher.getId(), classroom.getId(), practice.getId(), new ChangeStatus(PracticeStatus.ARCHIVED)).status())
                .isEqualTo(PracticeStatus.ARCHIVED);
    }

    // ---- helpers
    private static void assertForbidden(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("FORBIDDEN_OPERATION"));
    }

    private static org.springframework.beans.factory.ObjectProvider<AuditService> auditProvider(AuditService audit) {
        var factory = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        if (audit != null) factory.addBean("audit", audit);
        return factory.getBeanProvider(AuditService.class);
    }

    private static CodeRequest code() { return new CodeRequest(PracticeLanguage.JAVA, "class Main {}"); }
    private static RunInput run(String customInput) { return new RunInput(PracticeLanguage.JAVA, "class Main {}", customInput); }

    private void stubVersion(int version, PracticeTestcaseEntity... rows) {
        when(testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practice.getId(), version)).thenReturn(List.of(rows));
        when(testcases.findByPracticeIdAndVersionAndSampleTrueOrderByOrderIndexAsc(practice.getId(), version))
                .thenReturn(java.util.Arrays.stream(rows).filter(PracticeTestcaseEntity::isSample).toList());
    }

    private PracticeSubmissionEntity submissionOf(AccountEntity owner) {
        var s = new PracticeSubmissionEntity();
        s.setId(UUID.randomUUID());
        s.setPractice(practice);
        s.setStudent(owner);
        s.setLanguage(PracticeLanguage.JAVA);
        s.setVerdict(PracticeVerdict.WRONG_ANSWER);
        s.setTestcaseVersion(1);
        s.setCode("x");
        return s;
    }

    private AccountEntity account(AccountRole role) {
        var a = new AccountEntity();
        a.setId(UUID.randomUUID());
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        a.setUsername(role.name().toLowerCase() + a.getId());
        when(accounts.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private void member(AccountEntity who) {
        var m = new ClassroomMembershipEntity();
        m.setStatus(MembershipStatus.ACTIVE);
        m.setStudent(who);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), who.getId())).thenReturn(Optional.of(m));
    }

    private PracticeTestcaseEntity testcase(String in, String out, boolean isSample, int order, int version) {
        var t = new PracticeTestcaseEntity();
        t.setId(UUID.randomUUID());
        t.setPractice(practice);
        t.setVersion(version);
        t.setInput(in);
        t.setExpectedOutput(out);
        t.setSample(isSample);
        t.setOrderIndex(order);
        return t;
    }
}
