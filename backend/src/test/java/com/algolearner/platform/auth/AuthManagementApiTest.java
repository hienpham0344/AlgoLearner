package com.algolearner.platform.auth;

import com.algolearner.platform.audit.AccountAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.security.jwt-secret=auth-management-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"
})
@AutoConfigureMockMvc
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AuthManagementApiTest extends com.algolearner.platform.support.PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionRepository sessions;
    @Autowired AccountAuditRepository audits;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    @Test
    void sessionsArePrivateAndCanBeRevokedSeparatelyOrTogether() throws Exception {
        var owner = account(AccountRole.STUDENT);
        var outsider = account(AccountRole.STUDENT);
        String first = login(owner);
        String second = login(owner);
        String foreign = login(outsider);
        var listed = mvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2)).andReturn();
        var entries = json.readTree(listed.getResponse().getContentAsString()).path("data");
        String secondId = null;
        for (var entry : entries) if (!entry.path("current").asBoolean()) secondId = entry.path("id").asText();
        assertNotNull(secondId);
        mvc.perform(delete("/api/v1/auth/sessions/" + sessions.findByAccountIdAndRevokedAtIsNull(outsider.getId()).getFirst().getId())
                .header("Authorization", bearer(first))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/auth/sessions/" + secondId).header("Authorization", bearer(first)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(foreign))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", bearer(first)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(first))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(second))).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivationRequiresPasswordAndRevokesSessions() throws Exception {
        var owner = account(AccountRole.TEACHER);
        String token = login(owner);
        mvc.perform(post("/api/v1/auth/deactivate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", "wrong", "confirmed", true))))
                .andExpect(status().isBadRequest());
        assertEquals(AccountStatus.ACTIVE, accounts.findById(owner.getId()).orElseThrow().getStatus());
        mvc.perform(post("/api/v1/auth/deactivate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", "ExamplePass123!", "confirmed", true))))
                .andExpect(status().isNoContent());
        assertEquals(AccountStatus.DEACTIVATED, accounts.findById(owner.getId()).orElseThrow().getStatus());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminCanSuspendAndReasonIsShownUntilExpiryOrUnsuspend() throws Exception {
        var admin = account(AccountRole.ADMIN);
        var teacher = account(AccountRole.TEACHER);
        String adminToken = login(admin);
        String teacherToken = login(teacher);
        String path = "/api/v1/admin/accounts/" + teacher.getId();
        mvc.perform(post(path + "/suspend").header("Authorization", bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Policy violation\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/suspend").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path + "/suspend").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Policy violation\",\"endsAt\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("usernameOrEmail", teacher.getEmail(), "password", "ExamplePass123!"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Policy violation"));
        mvc.perform(post(path + "/unsuspend").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertTrue(audits.count() >= 2);
    }

    @Test
    void timedSuspensionEndsAndOnlyAdminCanReactivateDeactivatedAccount() throws Exception {
        var admin = account(AccountRole.ADMIN);
        var student = account(AccountRole.STUDENT);
        String adminToken = login(admin);
        String studentToken = login(student);
        String path = "/api/v1/admin/accounts/" + student.getId();
        String body = json.writeValueAsString(Map.of("reason", "Temporary review",
                "endsAt", java.time.Instant.now().plusSeconds(3600).toString()));
        mvc.perform(post(path + "/suspend").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        student = accounts.findById(student.getId()).orElseThrow();
        student.setSuspensionEndsAt(java.time.Instant.now().minusSeconds(1));
        accounts.saveAndFlush(student);
        String restored = login(student);
        assertEquals(AccountStatus.ACTIVE, accounts.findById(student.getId()).orElseThrow().getStatus());
        mvc.perform(post("/api/v1/auth/deactivate").header("Authorization", bearer(restored))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"ExamplePass123!\",\"confirmed\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(post(path + "/reactivate").header("Authorization", bearer(studentToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/reactivate").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertEquals(AccountStatus.ACTIVE, accounts.findById(student.getId()).orElseThrow().getStatus());
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("u" + account.getId().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setPasswordHash(passwords.encode("ExamplePass123!"));
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }

    private String login(AccountEntity account) throws Exception {
        MvcResult response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("usernameOrEmail", account.getEmail(),
                        "password", "ExamplePass123!"))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
