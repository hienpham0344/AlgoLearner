package com.algolearner.platform.announcement;

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
public class AnnouncementApiTest extends com.algolearner.platform.support.PostgresIntegrationTest {

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
    void announcementCrudAndPinningFlow() throws Exception {
        // Teacher creates classroom
        String classRes = mvc.perform(post("/api/v1/classrooms")
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Math 101\",\"joinMode\":\"AUTO_JOIN\"}"))
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

        // Teacher creates Announcement
        String annRes = mvc.perform(post("/api/v1/classrooms/{classId}/announcements", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Midterm Schedule\",\"content\":\"Exam will take place next Friday.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Midterm Schedule"))
                .andReturn().getResponse().getContentAsString();
        String annId = objectMapper.readTree(annRes).path("data").path("id").asText();

        // Other teacher tries to update announcement -> 403 Forbidden (IDOR prevention)
        mvc.perform(put("/api/v1/classrooms/{classId}/announcements/{annId}", classId, annId)
                        .with(actor(otherTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked Title\",\"content\":\"Hacked Content\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));

        // Student tries to update announcement -> 403 Forbidden
        mvc.perform(put("/api/v1/classrooms/{classId}/announcements/{annId}", classId, annId)
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked Title\",\"content\":\"Hacked Content\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));

        // Teacher updates announcement -> 200 OK
        mvc.perform(put("/api/v1/classrooms/{classId}/announcements/{annId}", classId, annId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Midterm Schedule Updated\",\"content\":\"Room changed to 301.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Midterm Schedule Updated"));

        // Teacher pins announcement
        mvc.perform(patch("/api/v1/classrooms/{classId}/announcements/{annId}/pin", classId, annId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPinned").value(true));

        // Student views list -> Pinned item visible
        mvc.perform(get("/api/v1/classrooms/{classId}/announcements", classId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].isPinned").value(true));

        // Teacher unpins announcement
        mvc.perform(patch("/api/v1/classrooms/{classId}/announcements/{annId}/unpin", classId, annId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPinned").value(false));

        // Teacher deletes announcement -> 204 No Content
        mvc.perform(delete("/api/v1/classrooms/{classId}/announcements/{annId}", classId, annId)
                        .with(actor(teacher)))
                .andExpect(status().isNoContent());

        // Verify announcement is gone
        mvc.perform(get("/api/v1/classrooms/{classId}/announcements/{annId}", classId, annId)
                        .with(actor(student)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ANNOUNCEMENT_NOT_FOUND"));
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
