package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.security.AccountRepository;
import com.algolearner.platform.profile.PracticeRepository;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.profile.dashboard.DashboardRepository;
import com.algolearner.platform.profile.dashboard.DashboardResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false",
        "app.security.jwt.issuer=https://issuer.example.test", "app.security.jwt.audience=classroom-tests",
        "app.security.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
public class AssignmentApiTest extends com.algolearner.platform.support.PostgresIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC); }
        @Bean @Primary TestAccounts testAccounts() { return new TestAccounts(); }
        @Bean @Primary DashboardRepository dashboardRepository() {
            return org.mockito.Mockito.mock(DashboardRepository.class, invocation -> {
                Class<?> type = invocation.getMethod().getReturnType();
                if (type == DashboardResponse.Preview.class) return new DashboardResponse.Preview<>(List.of(), 0, false);
                if (type == DashboardResponse.Contributions.class) return new DashboardResponse.Contributions(0, 0);
                if (type == DashboardResponse.Totals.class) return new DashboardResponse.Totals(0, 0, 0, 0, 0, 0);
                if (type == DashboardResponse.Moderation.class) return new DashboardResponse.Moderation(0, 0, 0);
                if (List.class.isAssignableFrom(type)) return List.of();
                return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
            });
        }
        @Bean @Primary PracticeRepository practiceRepository() { return accountId -> List.of(); }
        @Bean @Primary ProfileRepository profileRepository() { return org.mockito.Mockito.mock(ProfileRepository.class); }
    }

    static class TestAccounts implements AccountRepository {
        private final List<Account> records = new ArrayList<>();
        @Override public List<Account> accounts() { return List.copyOf(records); }
        @Override public List<Restriction> restrictions() { return List.of(); }
        void add(AccountEntity account) {
            records.add(new Account(account.getId(), account.getUsername(), account.getRole().name(),
                    account.getStatus().name(), account.getCreatedAt()));
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private com.algolearner.platform.auth.AccountRepository accounts;
    @Autowired private TestAccounts testAccounts;
    @Autowired private ObjectMapper objectMapper;

    private AccountEntity teacher;
    private AccountEntity student;

    @BeforeEach
    void setup() {
        teacher = account("prof", AccountRole.TEACHER);
        student = account("learner", AccountRole.STUDENT);
    }

    @Test
    void assignmentAttemptAutosaveSubmitAndGradingFlow() throws Exception {
        // Teacher creates classroom
        String classRes = mvc.perform(post("/api/v1/classrooms")
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms Lab\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(classRes).path("data").path("id").asText();
        String code = objectMapper.readTree(classRes).path("data").path("classCode").asText();

        // Student joins classroom
        mvc.perform(post("/api/v1/classrooms/join")
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        // Teacher creates assignment
        String asgRes = mvc.perform(post("/api/v1/classrooms/{classId}/assignments", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Quiz 1\",\"durationMinutes\":30,\"maxAttempts\":1,\"scoringPolicy\":\"HIGHEST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String asgId = objectMapper.readTree(asgRes).path("data").path("id").asText();

        // Teacher adds Question
        String qRes = mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/questions", classId, asgId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"SINGLE_CHOICE\",\"title\":\"Stack principle\",\"prompt\":\"What is the principle of a stack?\",\"points\":10,\"options\":[{\"content\":\"LIFO\",\"isCorrect\":true},{\"content\":\"FIFO\",\"isCorrect\":false}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode qData = objectMapper.readTree(qRes).path("data");
        String qId = qData.path("id").asText();
        String correctOptId = qData.path("options").get(0).path("id").asText();

        // Teacher publishes assignment
        mvc.perform(patch("/api/v1/classrooms/{classId}/assignments/{asgId}/publish", classId, asgId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // Student starts attempt
        String attRes = mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/attempts", classId, asgId)
                        .with(actor(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();
        String attId = objectMapper.readTree(attRes).path("data").path("id").asText();

        // Student autosaves draft answer
        mvc.perform(put("/api/v1/classrooms/{classId}/assignments/{asgId}/attempts/{attId}/draft", classId, asgId, attId)
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + qId + "\",\"selectedOptionIds\":\"[\\\"" + correctOptId + "\\\"]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDraft").value(true));

        // Student retrieves active attempt (draft recovery)
        mvc.perform(get("/api/v1/classrooms/{classId}/assignments/{asgId}/attempts/active", classId, asgId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].isDraft").value(true));

        // Student submits official attempt
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/attempts/{attId}/submit", classId, asgId, attId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADED"))
                .andExpect(jsonPath("$.data.finalScore").value(org.hamcrest.Matchers.nullValue()));

        // A transport retry returns the same official attempt and does not create another evaluation.
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/attempts/{attId}/submit", classId, asgId, attId)
                .with(actor(student))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM tbl_assignment_evaluation WHERE attempt_id=?",Integer.class,UUID.fromString(attId))).isEqualTo(1);

        // Student cannot start another attempt because maxAttempts = 1
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/attempts", classId, asgId)
                        .with(actor(student)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ATTEMPT_LIMIT_EXCEEDED"));

        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/students/{studentId}/attempt-grants",classId,asgId,student.getId())
                .with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"extraAttempts\":1,\"reset\":false,\"reason\":\"Approved extra attempt\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/attempts",classId,asgId).with(actor(student)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.attemptNumber").value(2));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT extra_attempts FROM tbl_assignment_participation WHERE assignment_id=? AND student_id=?",Integer.class,
                UUID.fromString(asgId),student.getId())).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM tbl_audit_log WHERE actor_id=? AND action='GIVE_EXTRA_ATTEMPT'",Integer.class,teacher.getId())).isEqualTo(1);

        // Teacher views submissions
        mvc.perform(get("/api/v1/classrooms/{classId}/assignments/{asgId}/submissions", classId, asgId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(attId))
                .andExpect(jsonPath("$.data[0].finalScore").value(10.0));

        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/publish-grades", classId, asgId)
                        .with(actor(teacher)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/classrooms/{classId}/assignments/{asgId}", classId, asgId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myBestScore").value(10.0));
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{asgId}/submissions/{attId}/override-grade", classId, asgId, attId)
                .with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overriddenScore\":7,\"reason\":\"Review adjustment\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/classrooms/{classId}/assignments/{asgId}",classId,asgId).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.myBestScore").value(10.0));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM tbl_assignment_evaluation WHERE attempt_id=?",Integer.class,UUID.fromString(attId))).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT final_score FROM tbl_assignment_evaluation WHERE attempt_id=? AND kind='AUTO'",java.math.BigDecimal.class,UUID.fromString(attId)))
                .isEqualByComparingTo(java.math.BigDecimal.TEN);

    }

    private AccountEntity account(String username, AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername(username + UUID.randomUUID());
        account.setFullName("User " + username);
        account.setEmail(username + UUID.randomUUID() + "@example.test");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("unused-test-fixture-hash");
        AccountEntity saved = accounts.save(account);
        testAccounts.add(saved);
        return saved;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor actor(AccountEntity account) {
        return authentication(new UsernamePasswordAuthenticationToken(
                account.getId().toString(), "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
        ));
    }
}
