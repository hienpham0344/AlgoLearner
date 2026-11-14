package com.algolearner.platform.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.security.jwt-secret=account-flows-test-only-secret-at-least-32-bytes", "app.mail.from=sender@example.test"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AccountFlowsTest extends com.algolearner.platform.support.PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired RegistrationOtpRepository otps;
    @Autowired AuthSessionRepository sessions;
    @MockBean JavaMailSender sender;

    @Test
    void resetEmailsCodeThenConsumesGrantAndRevokesEverySession() throws Exception {
        var account = account();
        var login = login(account, true);
        String access = data(login).path("token").asText();
        var cookie = login.getResponse().getCookie("algolearner_refresh");
        requestReset(account);
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertTrue(mail.getValue().getSubject().toLowerCase().contains("password"));
        var match = Pattern.compile("\\b[0-9]{6}\\b").matcher(mail.getValue().getText());
        assertTrue(match.find());
        String verifyBody = json.writeValueAsString(java.util.Map.of("email", account.getEmail(),
                "code", match.group(), "purpose", "RESET_PASSWORD"));
        var verified = mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").doesNotExist()).andReturn();
        mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isBadRequest());
        String resetBody = json.writeValueAsString(java.util.Map.of("email", account.getEmail(),
                "resetToken", data(verified).path("resetToken").asText(), "newPassword", "ChangedPass123!", "confirmPassword", "ChangedPass123!"));
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        assertNotNull(cookie);
        mvc.perform(post("/api/v1/auth/refresh-token").cookie(cookie).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        assertTrue(passwords.matches("ChangedPass123!", accounts.findById(account.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    void rememberMeSetsPersistentCookieAndRefreshRotatesAndLogoutRevokes() throws Exception {
        var account = account();
        var normal = login(account, false);
        assertEquals(900, data(normal).path("expiresIn").asInt());
        Cookie normalCookie = normal.getResponse().getCookie("algolearner_refresh");
        assertNotNull(normalCookie);
        assertEquals(-1, normalCookie.getMaxAge());
        var remembered = login(account, true);
        Cookie cookie = remembered.getResponse().getCookie("algolearner_refresh");
        assertNotNull(cookie);
        assertEquals(30 * 86400, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertTrue(remembered.getResponse().getHeader("Set-Cookie").contains("SameSite=Strict"));
        assertFalse(data(remembered).has("refreshToken"));
        var refreshed = mvc.perform(post("/api/v1/auth/refresh-token").cookie(cookie).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/auth/refresh-token").cookie(cookie).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").cookie(refreshed.getResponse().getCookie("algolearner_refresh"))
                .header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + data(refreshed).path("token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void realProfileHasCompleteShapeAndPersistsEditsAndPrivacy() throws Exception {
        var account = account();
        String access = data(login(account, false)).path("token").asText();
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.joinedAt").isString())
                .andExpect(jsonPath("$.data.publicPracticeStatistics.solved").value(0))
                .andExpect(jsonPath("$.data.streak.currentStreak").value(0));
        mvc.perform(patch("/api/v1/users/me/profile").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"New Name\",\"shortBio\":\"Hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("New Name"));
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("New Name"));
        mvc.perform(patch("/api/v1/users/me/profile/privacy").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isPublic\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/" + account.getUsername() + "/profile"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shortBio").value("Hello"))
                .andExpect(jsonPath("$.data.email").doesNotExist());
        mvc.perform(patch("/api/v1/users/me/profile/privacy").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isPublic\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/" + account.getUsername() + "/profile")).andExpect(status().isNotFound());
    }

    @Test
    void changePasswordKeepsCurrentSessionAndRevokesOnlyOtherSessions() throws Exception {
        var account = account();
        String current = data(login(account, true)).path("token").asText();
        String other = data(login(account, false)).path("token").asText();
        String body = json.writeValueAsString(java.util.Map.of("currentPassword", "WrongPass123!",
                "newPassword", "UpdatedPass123!", "confirmPassword", "UpdatedPass123!"));
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + current)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + other)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + current)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("WrongPass123!", "ExamplePass123!"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + current)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + other)).andExpect(status().isUnauthorized());
        assertTrue(passwords.matches("UpdatedPass123!", accounts.findById(account.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    void resetOtpHasTenMinuteExpiryCooldownAndAttemptLimit() throws Exception {
        var account = account();
        requestReset(account);
        var otp = otps.findByAccountId(account.getId()).getFirst();
        assertEquals(600, java.time.Duration.between(otp.getSentAt(), otp.getExpiresAt()).toSeconds());
        mvc.perform(post("/api/v1/auth/resend-otp").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "purpose", "RESET_PASSWORD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(AuthService.RESET_REQUEST_MESSAGE));
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        var match = Pattern.compile("\\b[0-9]{6}\\b").matcher(mail.getValue().getText());
        assertTrue(match.find());
        String code = match.group();
        String wrongCode = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) verifyCode(account, wrongCode, "RESET_PASSWORD", 400);
        verifyCode(account, code, "RESET_PASSWORD", 400);
        assertEquals(5, otps.findById(otp.getId()).orElseThrow().getFailedAttempts());
        otp.setFailedAttempts(0); otp.setExpiresAt(java.time.Instant.now().minusSeconds(1)); otps.saveAndFlush(otp);
        verifyCode(account, code, "RESET_PASSWORD", 400);
    }

    @Test
    void resetRequiresCorrectPurposeAndUnexpiredGrantAndStrongPassword() throws Exception {
        var account = account();
        requestReset(account);
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        var match = Pattern.compile("\\b[0-9]{6}\\b").matcher(mail.getValue().getText());
        assertTrue(match.find());
        String code = match.group();
        verifyCode(account, code, "REGISTER", 400);
        var verified = verifyCode(account, code, "RESET_PASSWORD", 200);
        String grant = data(verified).path("resetToken").asText();
        var body = new java.util.HashMap<String, String>();
        body.put("email", account.getEmail()); body.put("resetToken", grant);
        body.put("newPassword", "abcdefgh"); body.put("confirmPassword", "abcdefgh");
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertTrue(passwords.matches("ExamplePass123!", accounts.findById(account.getId()).orElseThrow().getPasswordHash()));
        var otp = otps.findByAccountId(account.getId()).getFirst();
        assertNotEquals(grant, otp.getResetTokenHash());
        otp.setResetExpiresAt(java.time.Instant.now().minusSeconds(1)); otps.saveAndFlush(otp);
        body.put("newPassword", "UpdatedPass123!"); body.put("confirmPassword", "UpdatedPass123!");
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRequiresHeaderAndHonorsServerExpiry() throws Exception {
        var account = account();
        var login = login(account, false);
        var cookie = login.getResponse().getCookie("algolearner_refresh");
        mvc.perform(post("/api/v1/auth/refresh-token").cookie(cookie)).andExpect(status().isForbidden());
        var session = sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).getFirst();
        assertEquals(7, java.time.Duration.between(session.getCreatedAt().minusSeconds(1), session.getExpiresAt()).toDays());
        assertNotEquals(cookie.getValue(), session.getRefreshHash());
        session.setExpiresAt(java.time.Instant.now().minusSeconds(1)); sessions.saveAndFlush(session);
        mvc.perform(post("/api/v1/auth/refresh-token").cookie(cookie).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshAcceptsOriginalTokenOnlyOnce() throws Exception {
        var account = account();
        Cookie cookie = login(account, false).getResponse().getCookie("algolearner_refresh");
        assertNotNull(cookie);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> refresh = () -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/refresh-token").cookie(cookie)
                        .header("X-Requested-With", "XMLHttpRequest"))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(refresh);
            var second = pool.submit(refresh);
            start.countDown();
            var statuses = java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(code -> code == 200).count());
            assertEquals(1, statuses.stream().filter(code -> code == 401).count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void logoutWithBearerRevokesRotatedSessionWhenRequestCarriesOldCookie() throws Exception {
        var account = account();
        var firstLogin = login(account, false);
        var otherLogin = login(account, false);
        Cookie oldCookie = firstLogin.getResponse().getCookie("algolearner_refresh");
        assertNotNull(oldCookie);
        var refreshed = mvc.perform(post("/api/v1/auth/refresh-token").cookie(oldCookie)
                .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk()).andReturn();
        String refreshedAccess = data(refreshed).path("token").asText();
        mvc.perform(post("/api/v1/auth/logout").cookie(oldCookie)
                .header("Authorization", "Bearer " + refreshedAccess)
                .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refreshedAccess))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + data(otherLogin).path("token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void failedResetEmailRollsBackCodeAndReturnsGenericResponse() throws Exception {
        var account = account();
        doThrow(new org.springframework.mail.MailSendException("unavailable")).when(sender).send(any(SimpleMailMessage.class));
        var failed = mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail()))))
                .andExpect(status().isOk()).andReturn();
        assertTrue(otps.findByAccountId(account.getId()).isEmpty());
        var unknown = mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(data(unknown).path("message"), data(failed).path("message"));
    }

    @Test
    void resetCooldownIsPrivateForForgotAndResend() throws Exception {
        var account = account();
        String identity = json.writeValueAsString(java.util.Map.of("email", account.getEmail()));
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(identity)).andExpect(status().isOk());
        var cooldown = mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(identity)).andExpect(status().isOk()).andReturn();
        var unknown = mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(data(unknown).path("message"), data(cooldown).path("message"));
        mvc.perform(post("/api/v1/auth/resend-otp").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(),
                        "purpose", "RESET_PASSWORD"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(true));
        assertEquals(1, otps.findByAccountId(account.getId()).size());
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void passwordResetCanUseUsernameWithoutDisclosingRegisteredEmail() throws Exception {
        var account = account();
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("usernameOrEmail", account.getUsername()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").isEmpty());
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        var match = Pattern.compile("\\b[0-9]{6}\\b").matcher(mail.getValue().getText());
        assertTrue(match.find());
        var verified = mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("usernameOrEmail", account.getUsername(),
                        "code", match.group(), "purpose", "RESET_PASSWORD"))))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("usernameOrEmail", account.getUsername(),
                        "resetToken", data(verified).path("resetToken").asText(),
                        "newPassword", "UpdatedPass123!", "confirmPassword", "UpdatedPass123!"))))
                .andExpect(status().isOk());
        assertTrue(passwords.matches("UpdatedPass123!", accounts.findById(account.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    void dashboardsUsePersistedClassroomsAndEnforceOwnerScope() throws Exception {
        var teacher = account(); teacher.setRole(AccountRole.TEACHER); accounts.saveAndFlush(teacher);
        var otherTeacher = account(); otherTeacher.setRole(AccountRole.TEACHER); accounts.saveAndFlush(otherTeacher);
        var student = account();
        String teacherToken = data(login(teacher, false)).path("token").asText();
        String otherToken = data(login(otherTeacher, false)).path("token").asText();
        String studentToken = data(login(student, false)).path("token").asText();
        var created = mvc.perform(post("/api/v1/classrooms").header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Persisted classroom\",\"description\":\"\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated()).andReturn();
        String classId = data(created).path("id").asText();
        String code = data(created).path("classCode").asText();
        mvc.perform(post("/api/v1/classrooms/join").header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("classCode", code))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboards/student").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.classrooms.items[0].classroomId").value(classId));
        mvc.perform(get("/api/v1/dashboards/teacher").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.classrooms.totalElements").value(1))
                .andExpect(jsonPath("$.data.classroomStatistics.items[0].activeStudentCount").value(1));
        mvc.perform(get("/api/v1/dashboards/teacher").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.classrooms.totalElements").value(0));
        mvc.perform(get("/api/v1/dashboards/teacher").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    private MvcResult verifyCode(AccountEntity account, String code, String purpose, int statusCode) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "code", code, "purpose", purpose))))
                .andExpect(status().is(statusCode)).andReturn();
    }

    private AccountEntity account() {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("u" + account.getId().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setPasswordHash(passwords.encode("ExamplePass123!"));
        account.setRole(AccountRole.STUDENT);
        account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }

    private MvcResult login(AccountEntity account, boolean remember) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("usernameOrEmail", account.getEmail(), "password", "ExamplePass123!", "rememberMe", remember))))
                .andExpect(status().isOk()).andReturn();
    }

    private void requestReset(AccountEntity account) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail())))).andExpect(status().isOk());
    }

    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("data"); }
}
