package com.algolearner.platform.integration;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.ClassroomRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
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
public class ClassroomModulesCrossIntegrationTest extends com.algolearner.platform.support.PostgresIntegrationTest {

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
    void fullClassroomSemesterFlow_Announcements_Lessons_Assignments() throws Exception {
        // 1. Teacher creates Classroom
        String classRes = mvc.perform(post("/api/v1/classrooms")
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms & Data Structures\",\"description\":\"Semester 1\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode classData = objectMapper.readTree(classRes).path("data");
        String classId = classData.path("id").asText();
        String classCode = classData.path("classCode").asText();

        // 2. Student joins Classroom via Class Code
        mvc.perform(post("/api/v1/classrooms/join")
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + classCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("JOINED"));

        // 3. Teacher creates a Lesson in the classroom (Module 4.5)
        String lessonRes = mvc.perform(post("/api/v1/classrooms/{classId}/lessons", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lesson 1: Graph Theory\",\"content\":\"# Graphs\\nIntroduction to BFS and DFS.\",\"completionMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Lesson 1: Graph Theory"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        String lessonId = objectMapper.readTree(lessonRes).path("data").path("id").asText();

        // Teacher publishes the Lesson
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/publish", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 4. Teacher creates an Assignment (Module 4.6)
        String asgRes = mvc.perform(post("/api/v1/classrooms/{classId}/assignments", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Assignment 1: Graph Traversal\",\"durationMinutes\":60,\"maxAttempts\":2,\"scoringPolicy\":\"HIGHEST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Assignment 1: Graph Traversal"))
                .andReturn().getResponse().getContentAsString();

        String assignmentId = objectMapper.readTree(asgRes).path("data").path("id").asText();

        // Teacher adds a Single Choice question to Assignment
        String q1Res = mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{assignmentId}/questions", classId, assignmentId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"SINGLE_CHOICE\",\"title\":\"BFS Time Complexity\",\"prompt\":\"What is the time complexity of BFS with adjacency list?\",\"points\":10,\"options\":[{\"content\":\"O(V + E)\",\"isCorrect\":true},{\"content\":\"O(V^2)\",\"isCorrect\":false}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode q1Data = objectMapper.readTree(q1Res).path("data");
        String q1Id = q1Data.path("id").asText();
        String opt1Id = q1Data.path("options").get(0).path("id").asText();

        // Teacher adds an Essay question to Assignment
        String q2Res = mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{assignmentId}/questions", classId, assignmentId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"ESSAY\",\"title\":\"DFS Application\",\"prompt\":\"Explain how DFS is used in cycle detection.\",\"points\":10,\"essayFormat\":\"TEXT_ONLY\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String q2Id = objectMapper.readTree(q2Res).path("data").path("id").asText();

        // Teacher links Assignment to Lesson
        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/items", classId, lessonId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"ASSIGNMENT\",\"itemId\":\"" + assignmentId + "\"}"))
                .andExpect(status().isCreated());

        // Teacher publishes Assignment
        mvc.perform(patch("/api/v1/classrooms/{classId}/assignments/{assignmentId}/publish", classId, assignmentId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 5. Teacher creates and pins an Announcement (Module 4.4)
        String annRes = mvc.perform(post("/api/v1/classrooms/{classId}/announcements", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Welcome!\",\"content\":\"Lesson 1 and Assignment 1 are now live!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Welcome!"))
                .andReturn().getResponse().getContentAsString();

        String announcementId = objectMapper.readTree(annRes).path("data").path("id").asText();

        // Teacher pins the announcement
        mvc.perform(patch("/api/v1/classrooms/{classId}/announcements/{annId}/pin", classId, announcementId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPinned").value(true));

        // 6. Student lists announcements -> verifies pinned announcement is visible
        mvc.perform(get("/api/v1/classrooms/{classId}/announcements", classId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(announcementId))
                .andExpect(jsonPath("$.data.items[0].isPinned").value(true));

        // 7. Student reads Lesson
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Lesson 1: Graph Theory"));

        // 8. Student starts Assignment attempt (Module 4.6)
        String attemptRes = mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{assignmentId}/attempts", classId, assignmentId)
                        .with(actor(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();

        String attemptId = objectMapper.readTree(attemptRes).path("data").path("id").asText();

        // 9. Student autosaves draft answers
        mvc.perform(put("/api/v1/classrooms/{classId}/assignments/{assignmentId}/attempts/{attemptId}/draft", classId, assignmentId, attemptId)
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + q1Id + "\",\"selectedOptionIds\":\"[\\\"" + opt1Id + "\\\"]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDraft").value(true));

        mvc.perform(put("/api/v1/classrooms/{classId}/assignments/{assignmentId}/attempts/{attemptId}/draft", classId, assignmentId, attemptId)
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + q2Id + "\",\"textAnswer\":\"DFS detects back-edges in directed graph.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDraft").value(true));

        // 10. Student submits official attempt
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{assignmentId}/attempts/{attemptId}/submit", classId, assignmentId, attemptId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.autoScore").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get("/api/v1/classrooms/{classId}/assignments/{assignmentId}/submissions", classId, assignmentId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].autoScore").value(10.0));

        // 11. Student marks Lesson complete (Module 4.5)
        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/complete", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isNoContent());

        // 12. Teacher grades the essay question and overrides final grade
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{assignmentId}/submissions/{attemptId}/grade-essay", classId, assignmentId, attemptId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + q2Id + "\",\"score\":9.5,\"feedback\":\"Good explanation!\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{assignmentId}/submissions/{attemptId}/override-grade", classId, assignmentId, attemptId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overriddenScore\":20.0,\"reason\":\"Bonus for neatness\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/classrooms/{classId}/assignments/{assignmentId}/publish-grades", classId, assignmentId)
                        .with(actor(teacher)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/classrooms/{classId}/assignments/{assignmentId}", classId, assignmentId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myBestScore").value(20.0));

        // 13. Teacher attempts hard delete on Lesson 1 -> BLOCKED because linked items/submissions/progress exist!
        mvc.perform(delete("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_HARD_DELETE_LESSON"));

        // 14. Teacher archives Lesson 1 instead -> succeeds
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/archive", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    private AccountEntity account(String username, AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername(username + UUID.randomUUID());
        account.setFullName("User " + username);
        account.setEmail(username + UUID.randomUUID() + "@example.test");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
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
