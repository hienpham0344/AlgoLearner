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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=assignment-timeout-integration-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AssignmentTimeoutIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final Instant EXPIRED_AT = NOW.minusSeconds(300);
    private static final Instant SAVED_AT = EXPIRED_AT.minusSeconds(30);

    @TestConfiguration
    static class TimeConfiguration {
        @Bean
        @Primary
        Clock timeoutClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private AssignmentService service;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AssignmentAttemptRepository attempts;
    @Autowired private AssignmentQuestionRepository questions;
    @Autowired private AssignmentQuestionOptionRepository options;
    @Autowired private AssignmentAnswerRepository answers;
    @Autowired private AccountRepository accounts;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomMembershipRepository memberships;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void schedulerAutoSubmitsPersistedDraftAtExpiryBeforeDeadlineAndIsIdempotent() {
        var fixture = fixture(TimeExpiredAction.AUTO_SUBMIT);
        var scheduler = new AssignmentTimeoutScheduler(attempts, service, clock);
        assertThat(attempts.findExpiredTimedAttemptIds(NOW, 100)).contains(fixture.attemptId());

        scheduler.expireTimedOutAttempts();
        var first = snapshot(fixture);
        assertThat(first.status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(first.submittedAt()).isEqualTo(EXPIRED_AT);
        assertThat(first.late()).isFalse();
        assertThat(first.finalScore()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(first.answerDraft()).isFalse();
        assertThat(first.answerScore()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(first.text()).isEqualTo("saved before expiry");
        assertThat(first.lastSavedAt()).isEqualTo(SAVED_AT);
        assertThat(first.attemptCount()).isEqualTo(1);

        scheduler.expireTimedOutAttempts();
        assertThat(snapshot(fixture)).isEqualTo(first);
        assertThat(attempts.findExpiredTimedAttemptIds(NOW, 100)).doesNotContain(fixture.attemptId());
    }

    @Test
    void schedulerLocksPersistedDraftWithoutCreatingOfficialSubmission() {
        var fixture = fixture(TimeExpiredAction.LOCK_WITHOUT_SUBMIT);
        var scheduler = new AssignmentTimeoutScheduler(attempts, service, clock);

        scheduler.expireTimedOutAttempts();

        var persisted = snapshot(fixture);
        assertThat(persisted.status()).isEqualTo(AttemptStatus.TIMED_OUT);
        assertThat(persisted.submittedAt()).isNull();
        assertThat(persisted.finalScore()).isNull();
        assertThat(persisted.answerDraft()).isTrue();
        assertThat(persisted.answerScore()).isNull();
        assertThat(persisted.text()).isEqualTo("saved before expiry");
        assertThat(persisted.attemptCount()).isEqualTo(1);
        assertThat(attempts.findExpiredTimedAttemptIds(NOW, 100)).doesNotContain(fixture.attemptId());
    }

    @Test
    void expiredAutosaveCommitsAutoSubmissionAndDoesNotPersistIncomingPayload() {
        var fixture = fixture(TimeExpiredAction.AUTO_SUBMIT);

        assertThatThrownBy(() -> service.saveDraftAnswer(fixture.studentId(), fixture.classroomId(),
                fixture.assignmentId(), fixture.attemptId(), new AssignmentDtos.SaveDraftAnswerRequest(
                        fixture.questionId(), "[]", "incoming after expiry", null, null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TIME_LIMIT_EXCEEDED.getCode());

        var persisted = snapshot(fixture);
        assertThat(persisted.status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(persisted.submittedAt()).isEqualTo(EXPIRED_AT);
        assertThat(persisted.finalScore()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(persisted.answerDraft()).isFalse();
        assertThat(persisted.text()).isEqualTo("saved before expiry");
        assertThat(persisted.selectedOptions()).isEqualTo(fixture.selectedOptions());
        assertThat(persisted.lastSavedAt()).isEqualTo(SAVED_AT);
    }

    @Test
    void expiredAutosaveCommitsLockWithoutSubmittingOrOverwritingDraft() {
        var fixture = fixture(TimeExpiredAction.LOCK_WITHOUT_SUBMIT);

        assertThatThrownBy(() -> service.saveDraftAnswer(fixture.studentId(), fixture.classroomId(),
                fixture.assignmentId(), fixture.attemptId(), new AssignmentDtos.SaveDraftAnswerRequest(
                        fixture.questionId(), "[]", "incoming after expiry", null, null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TIME_LIMIT_EXCEEDED.getCode());

        var persisted = snapshot(fixture);
        assertThat(persisted.status()).isEqualTo(AttemptStatus.TIMED_OUT);
        assertThat(persisted.submittedAt()).isNull();
        assertThat(persisted.finalScore()).isNull();
        assertThat(persisted.answerDraft()).isTrue();
        assertThat(persisted.text()).isEqualTo("saved before expiry");
        assertThat(persisted.selectedOptions()).isEqualTo(fixture.selectedOptions());
        assertThat(persisted.lastSavedAt()).isEqualTo(SAVED_AT);
    }

    private Fixture fixture(TimeExpiredAction action) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var teacher = account(AccountRole.TEACHER);
            var student = account(AccountRole.STUDENT);
            var classroom = new ClassroomEntity();
            classroom.setId(UUID.randomUUID());
            classroom.setOwner(teacher);
            classroom.setName("Timeout integration");
            classroom.setStatus(ClassroomStatus.ACTIVE);
            classroom.setClassCode(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            classroom.setJoinMode(JoinMode.AUTO_JOIN);
            classroom.setJoiningOpen(true);
            classrooms.saveAndFlush(classroom);

            var member = new ClassroomMembershipEntity();
            member.setId(UUID.randomUUID());
            member.setClassroom(classroom);
            member.setStudent(student);
            member.setStatus(MembershipStatus.ACTIVE);
            member.setJoinedAt(NOW.minusSeconds(3600));
            memberships.saveAndFlush(member);

            var assignment = assignments.saveAndFlush(AssignmentEntity.builder().id(UUID.randomUUID())
                    .classroom(classroom).title("Timed objective assignment")
                    .status(AssignmentStatus.PUBLISHED).startTime(NOW.minusSeconds(3600))
                    .deadline(NOW.minusSeconds(60)).allowLateSubmission(false)
                    .durationMinutes(10).timeExpiredAction(action).build());
            var question = questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID())
                    .assignment(assignment).questionType(QuestionType.SINGLE_CHOICE)
                    .title("Saved objective question").prompt("Select the correct option")
                    .points(BigDecimal.TEN).build());
            var option = options.saveAndFlush(AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID())
                    .question(question).content("Correct choice").isCorrect(true).build());
            var attempt = attempts.saveAndFlush(AssignmentAttemptEntity.builder().id(UUID.randomUUID())
                    .assignment(assignment).student(student).attemptNumber(1)
                    .status(AttemptStatus.IN_PROGRESS).startedAt(EXPIRED_AT.minusSeconds(600)).build());
            String selection = "[\"" + option.getId() + "\"]";
            var answer = answers.saveAndFlush(AssignmentAnswerEntity.builder().id(UUID.randomUUID())
                    .attempt(attempt).question(question).selectedOptionIds(selection)
                    .textAnswer("saved before expiry").isDraft(true).lastSavedAt(SAVED_AT).build());
            return new Fixture(student.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                    question.getId(), answer.getId(), selection);
        }));
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("timeout" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("unused-test-fixture-hash");
        return accounts.saveAndFlush(account);
    }

    private Snapshot snapshot(Fixture fixture) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var attempt = attempts.findById(fixture.attemptId()).orElseThrow();
            var answer = answers.findById(fixture.answerId()).orElseThrow();
            return new Snapshot(attempt.getStatus(), attempt.getSubmittedAt(), attempt.isLate(),
                    attempt.getFinalScore(), answer.isDraft(), answer.getScore(), answer.getTextAnswer(),
                    answer.getSelectedOptionIds(), answer.getLastSavedAt(),
                    attempts.countByAssignmentIdAndStudentId(fixture.assignmentId(), fixture.studentId()));
        }));
    }

    private record Fixture(UUID studentId, UUID classroomId, UUID assignmentId, UUID attemptId,
            UUID questionId, UUID answerId, String selectedOptions) { }

    private record Snapshot(AttemptStatus status, Instant submittedAt, boolean late, BigDecimal finalScore,
            boolean answerDraft, BigDecimal answerScore, String text, String selectedOptions,
            Instant lastSavedAt, long attemptCount) { }
}
