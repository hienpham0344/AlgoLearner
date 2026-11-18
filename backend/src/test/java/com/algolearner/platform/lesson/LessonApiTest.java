package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.security.AccountRepository;
import com.algolearner.platform.profile.PracticeRepository;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.profile.dashboard.DashboardRepository;
import com.algolearner.platform.profile.dashboard.DashboardResponse;
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
public class LessonApiTest extends com.algolearner.platform.support.PostgresIntegrationTest {

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
    private AccountEntity otherTeacher;
    private AccountEntity student;

    @BeforeEach
    void setup() {
        teacher = account("prof", AccountRole.TEACHER);
        otherTeacher = account("other", AccountRole.TEACHER);
        student = account("learner", AccountRole.STUDENT);
    }

    @Test
    void lessonLifecycleAndProtectionFlow() throws Exception {
        // Teacher creates classroom
        String classRes = mvc.perform(post("/api/v1/classrooms")
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Data Structures\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(classRes).path("data").path("id").asText();
        String code = objectMapper.readTree(classRes).path("data").path("classCode").asText();

        // Student joins
        mvc.perform(post("/api/v1/classrooms/join")
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        // Teacher creates lesson (Draft)
        String lessonRes = mvc.perform(post("/api/v1/classrooms/{classId}/lessons", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Binary Trees\",\"content\":\"Tree traversal methods\",\"completionMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String lessonId = objectMapper.readTree(lessonRes).path("data").path("id").asText();

        // Student cannot view Draft lesson -> 403
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isForbidden());

        // Teacher adds material
        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/materials", classId, lessonId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"trees.pdf\",\"fileUrl\":\"https://storage.example.test/trees.pdf\",\"fileType\":\"PDF\",\"fileSize\":1048576}"))
                .andExpect(status().isBadRequest());

        // Teacher publishes lesson
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/publish", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // Student can now view published lesson
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Binary Trees"));

        // Student marks lesson as completed
        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/complete", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isNoContent());

        // Teacher tries hard delete -> BLOCKED because student progress exists
        mvc.perform(delete("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_HARD_DELETE_LESSON"));

        // Teacher archives lesson -> succeeds
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/archive", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // Student can no longer access archived lesson
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isForbidden());

        // Teacher restores lesson
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/restore", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
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
