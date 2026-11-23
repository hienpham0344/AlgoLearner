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
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=assignment-authorization-tests-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
@Transactional
class AssignmentAuthorizationAcceptanceTest extends PostgresIntegrationTest {
    @Autowired private AssignmentService service;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AssignmentAttemptRepository attempts;
    @Autowired private AssignmentRevisionRepository revisions;
    @Autowired private AccountRepository accounts;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomMembershipRepository memberships;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper json;
    @Autowired private MockMvc mvc;

    @Test
    void studentCannotStartDraftAndNoAttemptIsCreated() throws Exception {
        var seed = seed(GradePublishMode.MANUAL);
        assertThat(assignments.findById(seed.assignmentId()).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.DRAFT);

        mvc.perform(post(path(seed) + "/attempts").with(actor(seed.student())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_CLOSED"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_attempt WHERE assignment_id=?",
                Long.class, seed.assignmentId())).isZero();
    }

    @Test
    void otherTeacherCannotGrantAttemptsOrMutateAllowanceAndAudit() throws Exception {
        var seed = seed(GradePublishMode.MANUAL);
        var otherTeacher = account(AccountRole.TEACHER);
        long auditBefore = auditCount(otherTeacher.getId());

        mvc.perform(post(path(seed) + "/students/" + seed.student().getId() + "/attempt-grants")
                        .with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extraAttempts\":2,\"reset\":false,\"reason\":\"Unauthorized grant\"}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_participation WHERE assignment_id=? AND student_id=?",
                Long.class, seed.assignmentId(), seed.student().getId())).isZero();
        assertThat(auditCount(otherTeacher.getId())).isEqualTo(auditBefore);
    }

    @Test
    void studentAndOtherTeacherCannotChangeEitherShuffleFlag() throws Exception {
        var seed = seed(GradePublishMode.MANUAL);
        var otherTeacher = account(AccountRole.TEACHER);
        for (var denied : List.of(seed.student(), otherTeacher)) {
            long auditBefore = auditCount(denied.getId());
            mvc.perform(put(path(seed)).with(actor(denied)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Unauthorized shuffle\",\"shuffleQuestions\":true,\"shuffleAnswers\":true}"))
                    .andExpect(status().isForbidden());
            entityManager.flush();
            var stored = jdbc.queryForMap("SELECT shuffle_questions,shuffle_answers,title FROM tbl_assignment WHERE id=?",
                    seed.assignmentId());
            assertThat(stored.get("shuffle_questions")).isEqualTo(false);
            assertThat(stored.get("shuffle_answers")).isEqualTo(false);
            assertThat(stored.get("title")).isEqualTo("Authorization acceptance");
            assertThat(auditCount(denied.getId())).isEqualTo(auditBefore);
        }
    }

    @Test
    void immediateAutoOnlyObjectiveGradePublishesWithoutTeacherRelease() {
        var seed = seed(GradePublishMode.IMMEDIATE);
        var question = service.addQuestion(seed.teacher().getId(), seed.classroom().getId(), seed.assignmentId(),
                new AssignmentDtos.AddQuestionRequest(QuestionType.SINGLE_CHOICE, "Objective", "Choose the correct answer",
                        BigDecimal.TEN, GradingPolicy.ALL_OR_NOTHING, null, null, null, null,
                        List.of(new AssignmentDtos.OptionRequest("Correct", true), new AssignmentDtos.OptionRequest("Wrong", false))));
        service.publish(seed.teacher().getId(), seed.classroom().getId(), seed.assignmentId());
        var attempt = service.startAttempt(seed.student().getId(), seed.classroom().getId(), seed.assignmentId());
        service.saveDraftAnswer(seed.student().getId(), seed.classroom().getId(), seed.assignmentId(), attempt.id(),
                new AssignmentDtos.SaveDraftAnswerRequest(question.id(), null, null, null, null,
                        0L, UUID.randomUUID(), null, List.of(question.options().getFirst().id())));

        var submitted = service.submitAttempt(seed.student().getId(), seed.classroom().getId(), seed.assignmentId(), attempt.id());
        entityManager.flush();
        var stored = attempts.findById(attempt.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(stored.getCurrentEvaluationId()).isNotNull().isEqualTo(stored.getPublishedEvaluationId());
        assertThat(submitted.finalScore()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT complete FROM tbl_assignment_evaluation WHERE id=?",
                Boolean.class, stored.getPublishedEvaluationId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT final_score FROM tbl_assignment_evaluation WHERE id=?",
                BigDecimal.class, stored.getPublishedEvaluationId())).isEqualByComparingTo("10");
        assertThat(service.ownOfficialAttempt(seed.student().getId(), seed.classroom().getId(), seed.assignmentId(), attempt.id())
                .finalScore()).isEqualByComparingTo("10");
    }

    @Test
    void teacherAuthorsAllThreeEssayFormatsAndAttemptsKeepTheirFrozenFormats() throws Exception {
        var seed = seed(GradePublishMode.MANUAL);
        var ids = new ArrayList<UUID>();
        for (var format : List.of(EssayFormat.TEXT_ONLY, EssayFormat.FILE_ONLY, EssayFormat.TEXT_AND_FILE)) {
            var request = essay(format);
            String response = mvc.perform(post(path(seed) + "/questions").with(actor(seed.teacher()))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.essayFormat").value(format.name()))
                    .andReturn().getResponse().getContentAsString();
            UUID questionId = UUID.fromString(json.readTree(response).path("data").path("id").asText());
            ids.add(questionId);
            assertThat(jdbc.queryForObject("SELECT essay_format FROM tbl_assignment_question WHERE id=?",
                    String.class, questionId)).isEqualTo(format.name());
        }
        service.publish(seed.teacher().getId(), seed.classroom().getId(), seed.assignmentId());
        var attempt = service.startAttempt(seed.student().getId(), seed.classroom().getId(), seed.assignmentId());
        assertThat(attempt.questions()).extracting(AssignmentDtos.QuestionView::essayFormat)
                .containsExactly(EssayFormat.TEXT_ONLY, EssayFormat.FILE_ONLY, EssayFormat.TEXT_AND_FILE);
        var revision = revisions.findById(attempt.revisionId()).orElseThrow();
        assertThat(AssignmentSnapshotPolicy.decode(revision.getContent()).questions())
                .extracting(AssignmentSnapshotPolicy.Question::essayFormat)
                .containsExactly(EssayFormat.TEXT_ONLY, EssayFormat.FILE_ONLY, EssayFormat.TEXT_AND_FILE);

        mvc.perform(put(path(seed) + "/questions/" + ids.get(1)).with(actor(seed.teacher()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(essay(EssayFormat.TEXT_ONLY))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.essayFormat").value("TEXT_ONLY"));
        assertThat(service.getActiveAttempt(seed.student().getId(), seed.classroom().getId(), seed.assignmentId()).questions())
                .extracting(AssignmentDtos.QuestionView::essayFormat)
                .containsExactly(EssayFormat.TEXT_ONLY, EssayFormat.FILE_ONLY, EssayFormat.TEXT_AND_FILE);
    }

    private AssignmentDtos.AddQuestionRequest essay(EssayFormat format) {
        return new AssignmentDtos.AddQuestionRequest(QuestionType.ESSAY, "Essay " + format, "Explain your reasoning",
                BigDecimal.TEN, null, format, null, null, null, List.of());
    }

    private record Seed(AccountEntity teacher, AccountEntity student, ClassroomEntity classroom, UUID assignmentId) { }

    private Seed seed(GradePublishMode mode) {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID()); classroom.setName("Authorization acceptance"); classroom.setOwner(teacher);
        classroom.setClassCode(UUID.randomUUID().toString().substring(0, 20));
        classroom.setStatus(ClassroomStatus.ACTIVE); classroom.setJoinMode(JoinMode.AUTO_JOIN);
        classroom = classrooms.saveAndFlush(classroom);
        var member = new ClassroomMembershipEntity();
        member.setId(UUID.randomUUID()); member.setClassroom(classroom); member.setStudent(student);
        member.setStatus(MembershipStatus.ACTIVE); member.setJoinedAt(Instant.now()); memberships.saveAndFlush(member);
        var request = new AssignmentDtos.CreateAssignmentRequest("Authorization acceptance", null, AssignmentType.GRADED,
                null, null, false, null, null, TimeExpiredAction.AUTO_SUBMIT, 1, ScoringPolicy.HIGHEST,
                false, false, mode, ViewAnswersMode.NEVER, CodeFeedbackLevel.SUMMARY);
        return new Seed(teacher, student, classroom, service.create(teacher.getId(), classroom.getId(), request).id());
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity(); account.setId(UUID.randomUUID());
        account.setUsername("asga-" + UUID.randomUUID()); account.setEmail(account.getUsername() + "@example.test");
        account.setRole(role); account.setStatus(AccountStatus.ACTIVE); account.setPasswordHash("unused-test-fixture");
        return accounts.saveAndFlush(account);
    }

    private long auditCount(UUID actor) {
        return jdbc.queryForObject("SELECT count(*) FROM tbl_audit_log WHERE actor_id=?", Long.class, actor);
    }

    private String path(Seed seed) {
        return "/api/v1/classrooms/" + seed.classroom().getId() + "/assignments/" + seed.assignmentId();
    }

    private RequestPostProcessor actor(AccountEntity actor) {
        return authentication(new UsernamePasswordAuthenticationToken(actor.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole()))));
    }
}
