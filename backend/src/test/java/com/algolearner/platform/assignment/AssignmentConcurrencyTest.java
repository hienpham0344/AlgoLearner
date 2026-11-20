package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.JoinMode;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.support.PostgresIntegrationTest;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.aop.framework.Advised;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=assignment-concurrency-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AssignmentConcurrencyTest extends PostgresIntegrationTest {
    @Autowired private AssignmentService service;
    @Autowired private AccountRepository accounts;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomMembershipRepository memberships;
    @Autowired private AssignmentQuestionRepository questions;
    @Autowired private AssignmentAttemptRepository attempts;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AssignmentAnswerRepository answers;
    private final List<Seed> seeds = new ArrayList<>();
    private MethodInterceptor assignmentAdvice;
    private MethodInterceptor answerAdvice;
    private volatile MethodInterceptor assignmentGate = invocation -> invocation.proceed();
    private volatile MethodInterceptor answerGate = invocation -> invocation.proceed();

    @BeforeEach
    void interceptRealRepositoryQueries() {
        assignmentAdvice = invocation -> assignmentGate.invoke(invocation);
        answerAdvice = invocation -> answerGate.invoke(invocation);
        ((Advised) assignments).addAdvice(0, assignmentAdvice);
        ((Advised) answers).addAdvice(0, answerAdvice);
    }

    @AfterEach
    void removeOnlyThisTestsCommittedFixtures() {
        ((Advised) assignments).removeAdvice(assignmentAdvice);
        ((Advised) answers).removeAdvice(answerAdvice);
        // The test harness drops this class's isolated schema. Immutable revision/evaluation history
        // deliberately prevents deleting committed aggregate fixtures piecemeal.
    }

    @Test
    void simultaneousStartsResumeTheSameAttemptInsteadOfCreatingTwo() throws Exception {
        var seed = seed();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.startAttempt(seed.studentId(), seed.classroomId(), seed.assignmentId());
            });
            var second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.startAttempt(seed.studentId(), seed.classroomId(), seed.assignmentId());
            });

            var firstAttempt = first.get(20, TimeUnit.SECONDS);
            var secondAttempt = second.get(20, TimeUnit.SECONDS);
            assertThat(firstAttempt.id()).isEqualTo(secondAttempt.id());
            assertThat(firstAttempt.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
            assertThat(secondAttempt.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
            assertThat(attempts.countByAssignmentIdAndStudentId(seed.assignmentId(), seed.studentId())).isEqualTo(1);
        } finally {
            stop(executor);
        }
    }

    @Test
    void submitWaitsForTheAutosaveTransactionAndIncludesItsCommittedAnswer() throws Exception {
        var seed = seed();
        var attempt = startWithDraft(seed);
        var autosaveReachedWrite = new CountDownLatch(1);
        var releaseAutosave = new CountDownLatch(1);
        var submitReachedLock = new CountDownLatch(1);
        var submitThread = new AtomicReference<Thread>();

        answerGate = call -> {
            if (call.getMethod().getName().equals("save")) {
                AssignmentAnswerEntity answer = (AssignmentAnswerEntity) call.getArguments()[0];
                if (answer.getAttempt().getId().equals(attempt.id()) && answer.isDraft()
                        && "autosaved before submit".equals(answer.getTextAnswer())) {
                    autosaveReachedWrite.countDown();
                    await(releaseAutosave, "Autosave release");
                }
            }
            return call.proceed();
        };
        assignmentGate = call -> {
            if (call.getMethod().getName().equals("findForUpdate")
                    && Thread.currentThread() == submitThread.get()) submitReachedLock.countDown();
            return call.proceed();
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var autosave = executor.submit(() -> service.saveDraftAnswer(seed.studentId(), seed.classroomId(),
                    seed.assignmentId(), attempt.id(), draft(seed, "autosaved before submit")));
            awaitWorker(autosaveReachedWrite, "Autosave write", autosave);
            var submit = executor.submit(() -> {
                submitThread.set(Thread.currentThread());
                return service.submitAttempt(seed.studentId(), seed.classroomId(), seed.assignmentId(), attempt.id());
            });
            await(submitReachedLock, "Submit lock query");
            assertThatThrownBy(() -> submit.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            releaseAutosave.countDown();
            autosave.get(20, TimeUnit.SECONDS);
            assertThat(submit.get(20, TimeUnit.SECONDS).status()).isEqualTo(AttemptStatus.SUBMITTED);
            assertOfficialAnswer(attempt.id(), "autosaved before submit");
        } finally {
            releaseAutosave.countDown();
            stop(executor);
        }
    }

    @Test
    void autosaveWaitingBehindSubmitCannotOverwriteTheOfficialAnswer() throws Exception {
        var seed = seed();
        var attempt = startWithDraft(seed);
        var submitReachedAnswers = new CountDownLatch(1);
        var releaseSubmit = new CountDownLatch(1);
        var autosaveReachedLock = new CountDownLatch(1);
        var autosaveThread = new AtomicReference<Thread>();
        var pauseFirstAnswerRead = new AtomicBoolean(true);

        answerGate = call -> {
            if (call.getMethod().getName().equals("findByAttemptId")
                    && attempt.id().equals(call.getArguments()[0])
                    && pauseFirstAnswerRead.compareAndSet(true, false)) {
                submitReachedAnswers.countDown();
                await(releaseSubmit, "Submit release");
            }
            return call.proceed();
        };
        assignmentGate = call -> {
            if (call.getMethod().getName().equals("findForUpdate")
                    && Thread.currentThread() == autosaveThread.get()) autosaveReachedLock.countDown();
            return call.proceed();
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var submit = executor.submit(() -> service.submitAttempt(
                    seed.studentId(), seed.classroomId(), seed.assignmentId(), attempt.id()));
            awaitWorker(submitReachedAnswers, "Submit answer read", submit);
            var autosave = executor.submit(() -> {
                autosaveThread.set(Thread.currentThread());
                try {
                    service.saveDraftAnswer(seed.studentId(), seed.classroomId(), seed.assignmentId(), attempt.id(),
                            draft(seed, "must not overwrite official answer"));
                    return null;
                } catch (ApiException failure) {
                    return failure;
                }
            });
            await(autosaveReachedLock, "Autosave lock query");
            assertThatThrownBy(() -> autosave.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            releaseSubmit.countDown();
            assertThat(submit.get(20, TimeUnit.SECONDS).status()).isEqualTo(AttemptStatus.SUBMITTED);
            assertThat(autosave.get(20, TimeUnit.SECONDS)).isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTEMPT_ALREADY_SUBMITTED.getCode());
            assertOfficialAnswer(attempt.id(), "initial saved answer");
        } finally {
            releaseSubmit.countDown();
            stop(executor);
        }
    }

    private Seed seed() {
        var seed = new TransactionTemplate(transactions).execute(status -> {
            var teacher = account(AccountRole.TEACHER);
            var student = account(AccountRole.STUDENT);
            var classroom = new ClassroomEntity();
            classroom.setId(UUID.randomUUID());
            classroom.setOwner(teacher);
            classroom.setName("Assignment concurrency");
            classroom.setClassCode(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            classroom.setStatus(ClassroomStatus.ACTIVE);
            classroom.setJoinMode(JoinMode.AUTO_JOIN);
            classroom.setJoiningOpen(true);
            classrooms.saveAndFlush(classroom);
            var membership = new ClassroomMembershipEntity();
            membership.setId(UUID.randomUUID());
            membership.setClassroom(classroom);
            membership.setStudent(student);
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setJoinedAt(Instant.now());
            memberships.saveAndFlush(membership);
            var assignment = assignments.saveAndFlush(AssignmentEntity.builder().id(UUID.randomUUID())
                    .classroom(classroom).title("Concurrent assignment").status(AssignmentStatus.PUBLISHED)
                    .durationMinutes(60).maxAttempts(1).build());
            var question = questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID())
                    .assignment(assignment).questionType(QuestionType.ESSAY).title("Concurrent essay")
                    .prompt("Describe the algorithm").points(BigDecimal.TEN).build());
            return new Seed(teacher.getId(), student.getId(), classroom.getId(), assignment.getId(), question.getId());
        });
        seeds.add(seed);
        return seed;
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("asgconcurrent" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setStatus(AccountStatus.ACTIVE);
        account.setRole(role);
        account.setPasswordHash("unused-test-fixture-hash");
        return accounts.saveAndFlush(account);
    }

    private AssignmentDtos.AttemptView startWithDraft(Seed seed) {
        var attempt = service.startAttempt(seed.studentId(), seed.classroomId(), seed.assignmentId());
        service.saveDraftAnswer(seed.studentId(), seed.classroomId(), seed.assignmentId(), attempt.id(),
                draft(seed, "initial saved answer"));
        return attempt;
    }

    private AssignmentDtos.SaveDraftAnswerRequest draft(Seed seed, String text) {
        return new AssignmentDtos.SaveDraftAnswerRequest(seed.questionId(), null, text, null, null);
    }

    private void assertOfficialAnswer(UUID attemptId, String expectedText) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var persistedAttempt = attempts.findById(attemptId).orElseThrow();
            assertThat(persistedAttempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
            assertThat(persistedAttempt.getSubmittedAt()).isNotNull();
            var persistedAnswers = answers.findByAttemptId(attemptId);
            assertThat(persistedAnswers).hasSize(1);
            assertThat(persistedAnswers.getFirst().getTextAnswer()).isEqualTo(expectedText);
            assertThat(persistedAnswers.getFirst().isDraft()).isFalse();
        });
    }

    private static void await(CountDownLatch latch, String description) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError(description + " timed out");
    }

    private static void awaitWorker(CountDownLatch latch, String description, Future<?> worker) throws Exception {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            if (worker.isDone()) worker.get(1, TimeUnit.SECONDS);
            throw new AssertionError(description + " timed out");
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrency workers did not terminate");
    }

    private record Seed(UUID teacherId, UUID studentId, UUID classroomId, UUID assignmentId, UUID questionId) { }
}
