package com.algolearner.platform.classroom;

import com.algolearner.platform.announcement.AnnouncementDtos;
import com.algolearner.platform.announcement.AnnouncementService;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.lesson.CompletionMode;
import com.algolearner.platform.lesson.LessonDtos;
import com.algolearner.platform.lesson.LessonService;
import com.algolearner.platform.quiz.QuizDtos;
import com.algolearner.platform.quiz.QuizService;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=classroom-acceptance-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ClassroomAcceptanceSafetyTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    static class TestClock extends Clock {
        private Instant value = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }

    @TestConfiguration
    static class Time {
        @Bean @Primary TestClock acceptanceClock() { return new TestClock(); }
    }

    @Autowired private ClassroomService classrooms;
    @Autowired private AccountRepository accounts;
    @Autowired private AnnouncementService announcements;
    @Autowired private LessonService lessons;
    @Autowired private QuizService quizzes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestClock clock;

    @BeforeEach void resetClock() { clock.value = NOW; }

    @ParameterizedTest
    @EnumSource(JoinMode.class)
    void expiredAndExactExpiryCodesCannotCreateMembershipOrPendingRequest(JoinMode mode) {
        var teacher = account(AccountRole.TEACHER, AccountStatus.ACTIVE);
        var student = account(AccountRole.STUDENT, AccountStatus.ACTIVE);
        var peer = account(AccountRole.STUDENT, AccountStatus.ACTIVE);
        var classroom = classroom(teacher, mode);
        classrooms.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(peer.getUsername()));
        var members = memberships(classroom.id());

        for (Instant expiry : List.of(NOW.minusSeconds(1), NOW)) {
            jdbc.update("UPDATE tbl_classroom SET code_expires_at=? WHERE id=?", Timestamp.from(expiry), classroom.id());
            assertThatThrownBy(() -> classrooms.join(student.getId(), new ClassroomDtos.JoinByCode(classroom.classCode())))
                    .isInstanceOf(AppException.class).hasFieldOrPropertyWithValue("errorCode", "CLASS_CODE_EXPIRED");
            assertThat(memberships(classroom.id())).isEqualTo(members);
            assertThat(requests(classroom.id())).isEmpty();
        }
    }

    static Stream<Arguments> invalidJoiners() {
        return Stream.of(
                Arguments.of(AccountRole.TEACHER, AccountStatus.ACTIVE),
                Arguments.of(AccountRole.ADMIN, AccountStatus.ACTIVE),
                Arguments.of(AccountRole.STUDENT, AccountStatus.PENDING_VERIFICATION),
                Arguments.of(AccountRole.STUDENT, AccountStatus.SUSPENDED),
                Arguments.of(AccountRole.STUDENT, AccountStatus.DEACTIVATED));
    }

    @ParameterizedTest
    @MethodSource("invalidJoiners")
    void invalidRoleOrInactiveJoinCannotCreateMembershipOrRequest(AccountRole role, AccountStatus status) {
        var teacher = account(AccountRole.TEACHER, AccountStatus.ACTIVE);
        var actor = account(role, status);
        var classroom = classroom(teacher, JoinMode.REQUIRE_APPROVAL);

        assertThatThrownBy(() -> classrooms.join(actor.getId(), new ClassroomDtos.JoinByCode(classroom.classCode())))
                .isInstanceOf(AppException.class).hasFieldOrPropertyWithValue("errorCode", "FORBIDDEN_OPERATION");
        assertThat(memberships(classroom.id())).isEmpty();
        assertThat(requests(classroom.id())).isEmpty();
    }

    @Test
    void nonTargetCannotAcceptOrDeclineInvitationOrChangeItsNotification() {
        var teacher = account(AccountRole.TEACHER, AccountStatus.ACTIVE);
        var target = account(AccountRole.STUDENT, AccountStatus.ACTIVE);
        var other = account(AccountRole.STUDENT, AccountStatus.ACTIVE);
        var classroom = classroom(teacher, JoinMode.AUTO_JOIN);
        UUID invitation = classrooms.invite(teacher.getId(), classroom.id(),
                new ClassroomDtos.Invite(target.getEmail(), NOW.plusSeconds(3600)));
        var notification = jdbc.queryForMap("SELECT id,recipient_id,read_at FROM tbl_notification WHERE classroom_invitation_id=?", invitation);

        assertThatThrownBy(() -> classrooms.acceptInvitation(other.getId(), invitation))
                .isInstanceOf(AppException.class).hasFieldOrPropertyWithValue("errorCode", "INVITATION_NOT_FOUND");
        assertThatThrownBy(() -> classrooms.declineInvitation(other.getId(), invitation))
                .isInstanceOf(AppException.class).hasFieldOrPropertyWithValue("errorCode", "INVITATION_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT status FROM tbl_classroom_invitation WHERE id=?", String.class, invitation))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForMap("SELECT id,recipient_id,read_at FROM tbl_notification WHERE classroom_invitation_id=?", invitation))
                .isEqualTo(notification);
        assertThat(memberships(classroom.id())).isEmpty();

        classrooms.acceptInvitation(target.getId(), invitation);
        assertThat(memberships(classroom.id())).hasSize(1);
        assertThat(memberships(classroom.id()).getFirst().get("student_id")).isEqualTo(target.getId());
    }

    @Test
    void studentSeesMultiplePinsInMostRecentlyPinnedOrderAndUnpinMovesItemOutOfPinnedGroup() {
        var teacher = account(AccountRole.TEACHER, AccountStatus.ACTIVE);
        var student = account(AccountRole.STUDENT, AccountStatus.ACTIVE);
        var classroom = classroom(teacher, JoinMode.AUTO_JOIN);
        classrooms.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(student.getUsername()));
        var first = announcements.create(teacher.getId(), classroom.id(), new AnnouncementDtos.CreateRequest("First", "Text"));
        var second = announcements.create(teacher.getId(), classroom.id(), new AnnouncementDtos.CreateRequest("Second", "Text"));
        var unpinned = announcements.create(teacher.getId(), classroom.id(), new AnnouncementDtos.CreateRequest("Unpinned", "Text"));
        announcements.pin(teacher.getId(), classroom.id(), second.id());
        clock.value = NOW.plusSeconds(1);
        announcements.pin(teacher.getId(), classroom.id(), first.id());

        var page = announcements.list(student.getId(), classroom.id(), PageRequest.of(0, 10)).getData().getItems();
        assertThat(page).extracting(AnnouncementDtos.AnnouncementView::id).containsExactly(first.id(), second.id(), unpinned.id());
        assertThat(page).extracting(AnnouncementDtos.AnnouncementView::isPinned).containsExactly(true, true, false);

        announcements.unpin(teacher.getId(), classroom.id(), first.id());
        var after = announcements.list(student.getId(), classroom.id(), PageRequest.of(0, 10)).getData().getItems();
        assertThat(after.getFirst().id()).isEqualTo(second.id());
        assertThat(after).filteredOn(item -> item.id().equals(first.id())).singleElement().satisfies(item -> {
            assertThat(item.isPinned()).isFalse();
            assertThat(item.pinnedAt()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "DEACTIVATED"})
    void restoredStudentUsesRetainedMembershipProgressAndOfficialSubmissionWithoutRejoining(AccountStatus inactive) {
        var f = learningFixture();
        var submitted = quizzes.submit(f.student(), f.classroom(), f.quiz(), f.attempt());
        var members = memberships(f.classroom());
        var history = completionHistory(f.lesson(), f.student());
        var official = jdbc.queryForMap("SELECT id,status,submitted_at,score FROM tbl_quiz_attempt WHERE id=?", submitted.id());

        status(f.student(), inactive);
        assertThatThrownBy(() -> classrooms.get(f.student(), f.classroom())).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> quizzes.start(f.student(), f.classroom(), f.quiz())).isInstanceOf(AppException.class);
        assertThat(memberships(f.classroom())).isEqualTo(members);
        assertThat(completionHistory(f.lesson(), f.student())).isEqualTo(history);
        assertThat(jdbc.queryForMap("SELECT id,status,submitted_at,score FROM tbl_quiz_attempt WHERE id=?", submitted.id())).isEqualTo(official);

        status(f.student(), AccountStatus.ACTIVE);
        assertThat(classrooms.get(f.student(), f.classroom()).id()).isEqualTo(f.classroom());
        assertThat(lessons.get(f.student(), f.classroom(), f.lesson()).isCompleted()).isTrue();
        assertThat(quizzes.history(f.student(), f.classroom(), f.quiz(), null, PageRequest.of(0, 10)).getData().getItems())
                .extracting(QuizDtos.AttemptView::id).containsExactly(submitted.id());
        assertThat(memberships(f.classroom())).isEqualTo(members);
        assertThat(completionHistory(f.lesson(), f.student())).isEqualTo(history);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "DEACTIVATED"})
    void inactiveTeacherKeepsOldContentReadableButBlocksNewQuizSubmissionUntilRestored(AccountStatus inactive) {
        var f = learningFixture();
        var admin = account(AccountRole.ADMIN, AccountStatus.ACTIVE);
        var members = memberships(f.classroom());
        var history = completionHistory(f.lesson(), f.student());
        status(f.teacher(), inactive);

        assertThat(classrooms.get(f.student(), f.classroom()).status()).isEqualTo("ACTIVE");
        assertThat(lessons.get(f.student(), f.classroom(), f.lesson()).content()).isEqualTo("Retained content");
        assertThat(lessons.get(f.student(), f.classroom(), f.lesson()).isCompleted()).isTrue();
        assertThat(classrooms.get(admin.getId(), f.classroom()).ownerId()).isEqualTo(f.teacher());
        assertThatThrownBy(() -> quizzes.submit(f.student(), f.classroom(), f.quiz(), f.attempt())).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> quizzes.start(f.student(), f.classroom(), f.quiz())).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM tbl_quiz_attempt WHERE id=?", String.class, f.attempt())).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM tbl_quiz_attempt WHERE id=?", Timestamp.class, f.attempt())).isNull();
        assertThat(memberships(f.classroom())).isEqualTo(members);
        assertThat(completionHistory(f.lesson(), f.student())).isEqualTo(history);

        status(f.teacher(), AccountStatus.ACTIVE);
        assertThat(quizzes.submit(f.student(), f.classroom(), f.quiz(), f.attempt()).submittedAt()).isNotNull();
        assertThat(classrooms.get(f.student(), f.classroom()).status()).isEqualTo("ACTIVE");
        assertThat(lessons.get(f.student(), f.classroom(), f.lesson()).isCompleted()).isTrue();
        assertThat(memberships(f.classroom())).isEqualTo(members);
        assertThat(completionHistory(f.lesson(), f.student())).isEqualTo(history);
    }

    private record Learning(UUID teacher, UUID student, UUID classroom, UUID lesson, UUID quiz, UUID attempt) { }

    private Learning learningFixture() {
        var teacher = account(AccountRole.TEACHER, AccountStatus.ACTIVE);
        var student = account(AccountRole.STUDENT, AccountStatus.ACTIVE);
        var classroom = classroom(teacher, JoinMode.AUTO_JOIN);
        classrooms.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(student.getUsername()));
        var lesson = lessons.create(teacher.getId(), classroom.id(),
                new LessonDtos.CreateLessonRequest("Retained", "Retained content", null, null, CompletionMode.MANUAL));
        lessons.publish(teacher.getId(), classroom.id(), lesson.id());
        lessons.completeLesson(student.getId(), classroom.id(), lesson.id());
        var question = new QuizDtos.Question(UUID.randomUUID(), "Choose", QuizDtos.QuestionType.SINGLE_CHOICE,
                QuizDtos.Scoring.ALL_OR_NOTHING, BigDecimal.ONE, List.of(
                        new QuizDtos.Option(UUID.randomUUID(), "Right", true),
                        new QuizDtos.Option(UUID.randomUUID(), "Wrong", false)));
        var quiz = quizzes.create(teacher.getId(), classroom.id(), new QuizDtos.Configure("Retained quiz", true,
                null, null, 600, 3, false, false, false, QuizDtos.ReleasePolicy.IMMEDIATE,
                QuizDtos.TimeoutMode.AUTO_SUBMIT, List.of(question)));
        quizzes.publish(teacher.getId(), classroom.id(), quiz.id());
        var attempt = quizzes.start(student.getId(), classroom.id(), quiz.id());
        return new Learning(teacher.getId(), student.getId(), classroom.id(), lesson.id(), quiz.id(), attempt.id());
    }

    private ClassroomDtos.View classroom(AccountEntity teacher, JoinMode mode) {
        return classrooms.create(teacher.getId(), new ClassroomDtos.Create("Acceptance", null, mode, null, null));
    }

    private List<Map<String, Object>> memberships(UUID classroom) {
        return jdbc.queryForList("SELECT id,student_id,status,joined_at,ended_at FROM tbl_classroom_membership WHERE classroom_id=? ORDER BY id", classroom);
    }

    private List<Map<String, Object>> requests(UUID classroom) {
        return jdbc.queryForList("SELECT id,student_id,status FROM tbl_classroom_join_request WHERE classroom_id=? ORDER BY id", classroom);
    }

    private List<Map<String, Object>> completionHistory(UUID lesson, UUID student) {
        return jdbc.queryForList("SELECT id,rule_version,source_fingerprint,is_completed FROM tbl_lesson_completion_history WHERE lesson_id=? AND student_id=? ORDER BY id", lesson, student);
    }

    private void status(UUID account, AccountStatus status) {
        jdbc.update("UPDATE tbl_account SET status=? WHERE id=?", status.name(), account);
    }

    private AccountEntity account(AccountRole role, AccountStatus status) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("acceptance_" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setPasswordHash("unused");
        account.setRole(role);
        account.setStatus(status);
        return accounts.saveAndFlush(account);
    }
}
