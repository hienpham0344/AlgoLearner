package com.algolearner.platform.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.algolearner.platform.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Remaining SRS 4.1/4.2 retention and principal-bound authorization acceptance cases. */
@SpringBootTest(properties = {
        "logging.level.root=WARN",
        "app.security.jwt-secret=auth-profile-authorization-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"internal-auth", "test"})
@Import(AuthProfileAuthorizationAcceptanceTest.TimeFixture.class)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AuthProfileAuthorizationAcceptanceTest extends com.algolearner.platform.support.PostgresIntegrationTest {
    private static final Instant START = Instant.parse("2026-11-18T00:00:00Z");
    private static final String PASSWORD = "example123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired RegistrationOtpRepository otps;
    @Autowired AuthSessionRepository sessions;
    @Autowired NotificationService notifications;
    @Autowired PasswordEncoder passwords;
    @Autowired TestClock clock;
    @MockBean JavaMailSender sender;

    @BeforeEach
    void resetTime() { clock.now = START; }

    @Test
    void pendingAccountSurvivesThirtyOneDaysAndCanRequestFreshRegisterOtpWithoutLosingIdentity() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = suffix + "@test.dev";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "username", "pending" + suffix,
                                "password", PASSWORD, "confirmPassword", PASSWORD,
                                "termsAccepted", true, "privacyAccepted", true))))
                .andExpect(status().isCreated());
        var account = accounts.findByEmailIgnoreCase(email).orElseThrow();
        account.setFullName("Retained pending profile");
        account.setShortBio("Profile data must survive verification delay");
        account = accounts.saveAndFlush(account);
        var before = identity(account);
        var initialOtp = otps.findByAccountId(account.getId()).getFirst();
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        var code = Pattern.compile("\\b[0-9]{6}\\b").matcher(sent.getValue().getText());
        assertTrue(code.find());

        clock.now = START.plus(Duration.ofDays(31));
        mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "code", code.group(), "purpose", "REGISTER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));
        mvc.perform(post("/api/v1/auth/resend-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "purpose", "REGISTER"))))
                .andExpect(status().isOk());
        verify(sender, times(2)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        var retained = accounts.findById(account.getId()).orElseThrow();
        assertTrue(before.equals(identity(retained)), "Elapsed time and OTP renewal must preserve the exact pending identity and profile");
        assertEquals(AccountStatus.PENDING_VERIFICATION, retained.getStatus());
        assertEquals(account.getId(), accounts.findByEmailIgnoreCase(email).orElseThrow().getId());
        assertTrue(passwords.matches(PASSWORD, retained.getPasswordHash()));
        assertTrue(sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).isEmpty());
        var renewed = otps.findByAccountId(account.getId());
        assertEquals(2, renewed.size());
        assertNotNull(otps.findById(initialOtp.getId()).orElseThrow().getUsedAt());
        var fresh = renewed.stream().filter(otp -> !otp.getId().equals(initialOtp.getId())).findFirst().orElseThrow();
        assertEquals(clock.now, fresh.getSentAt());
        assertEquals(clock.now.plusSeconds(600), fresh.getExpiresAt());
        assertNull(fresh.getUsedAt());
    }

    @Test
    void injectedPrivacyTargetIsRejectedAndValidUpdateChangesOnlyAuthenticatedPrincipal() throws Exception {
        var owner = student();
        var peer = student();
        String token = login(owner);
        for (String targetField : new String[] {"accountId", "targetId"}) {
            mvc.perform(patch("/api/v1/users/me/profile/privacy").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("isPublic", true, targetField, peer.getId()))))
                    .andExpect(status().isBadRequest());
            assertFalse(accounts.findById(owner.getId()).orElseThrow().isProfilePublic());
            assertFalse(accounts.findById(peer.getId()).orElseThrow().isProfilePublic());
        }
        mvc.perform(patch("/api/v1/users/me/profile/privacy").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPublic\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(owner.getUsername()))
                .andExpect(jsonPath("$.data.isPublic").value(true));
        assertTrue(accounts.findById(owner.getId()).orElseThrow().isProfilePublic());
        assertFalse(accounts.findById(peer.getId()).orElseThrow().isProfilePublic());
        mvc.perform(get("/api/v1/users/" + owner.getUsername() + "/profile")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/" + peer.getUsername() + "/profile")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", "Bearer " + login(peer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isPublic").value(false));
    }

    @Test
    void studentDashboardNotificationCountAndItemsRemainScopedToTheirRecipient() throws Exception {
        var owner = student();
        var peer = student();
        Set<String> ownerIds = new LinkedHashSet<>();
        Set<String> peerIds = new LinkedHashSet<>();
        for (int index = 0; index < 3; index++) {
            ownerIds.add(notifications.notifyStudent(owner, "SYSTEM", "Owner notice " + index,
                    "Private owner message " + index, null, null, null).getId().toString());
        }
        for (int index = 0; index < 2; index++) {
            peerIds.add(notifications.notifyStudent(peer, "SYSTEM", "Peer notice " + index,
                    "Private peer message " + index, null, null, null).getId().toString());
        }
        String ownerToken = login(owner);
        var limited = dashboardNotifications(ownerToken, 1, peer.getId());
        assertEquals(3, limited.path("totalElements").asLong());
        assertTrue(limited.path("hasMore").asBoolean());
        assertEquals(1, limited.path("items").size());
        assertTrue(ownerIds.containsAll(notificationIds(limited)));

        var own = dashboardNotifications(ownerToken, 20, peer.getId());
        assertEquals(3, own.path("totalElements").asLong());
        assertFalse(own.path("hasMore").asBoolean());
        assertEquals(ownerIds, notificationIds(own));
        for (var item : own.path("items")) {
            assertTrue(item.path("title").asText().startsWith("Owner notice "));
            assertTrue(item.path("message").asText().startsWith("Private owner message "));
        }
        var theirs = dashboardNotifications(login(peer), 20, owner.getId());
        assertEquals(2, theirs.path("totalElements").asLong());
        assertFalse(theirs.path("hasMore").asBoolean());
        assertEquals(peerIds, notificationIds(theirs));
        for (var item : theirs.path("items")) {
            assertTrue(item.path("title").asText().startsWith("Peer notice "));
            assertTrue(item.path("message").asText().startsWith("Private peer message "));
        }
    }

    private JsonNode dashboardNotifications(String token, int size, UUID spoofedAccount) throws Exception {
        var result = mvc.perform(get("/api/v1/dashboards/student").header("Authorization", "Bearer " + token)
                        .param("previewSize", Integer.toString(size)).param("accountId", spoofedAccount.toString()))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("recentNotifications");
    }

    private Set<String> notificationIds(JsonNode preview) {
        Set<String> result = new LinkedHashSet<>();
        for (var item : preview.path("items")) result.add(item.path("notificationId").asText());
        return result;
    }

    private AccountEntity student() {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("student" + account.getId().toString().replace("-", ""));
        account.setEmail(account.getId() + "@test.dev");
        account.setRole(AccountRole.STUDENT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(passwords.encode(PASSWORD));
        return accounts.saveAndFlush(account);
    }

    private String login(AccountEntity account) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("usernameOrEmail", account.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private PendingIdentity identity(AccountEntity account) {
        return new PendingIdentity(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
                account.getPasswordHash(), account.getFullName(), account.getShortBio(), account.getCreatedAt());
    }

    private record PendingIdentity(UUID id, String username, String email, AccountRole role, String passwordHash,
            String fullName, String shortBio, Instant createdAt) { }

    @TestConfiguration
    static class TimeFixture {
        @Bean @Primary TestClock authorizationAcceptanceClock() { return new TestClock(); }
    }

    static final class TestClock extends Clock {
        private volatile Instant now = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
