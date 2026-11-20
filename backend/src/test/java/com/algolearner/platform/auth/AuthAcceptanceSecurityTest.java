package com.algolearner.platform.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** SRS 4.1 abuse cases against real transactions; only time and SMTP transport are fixtures. */
@SpringBootTest(properties = {
        "app.security.jwt-secret=auth-acceptance-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"internal-auth", "test"})
@Import(AuthAcceptanceSecurityTest.TimeFixture.class)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AuthAcceptanceSecurityTest extends com.algolearner.platform.support.PostgresIntegrationTest {
    private static final String PASSWORD = "example123";
    private static final Instant START = Instant.parse("2026-11-18T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionRepository sessions;
    @Autowired RegistrationOtpRepository otps;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder tokens;
    @Autowired TestClock clock;
    @MockBean JavaMailSender sender;

    @BeforeEach
    void resetTime() { clock.now = START; }

    @Test
    void publicRegistrationCannotCreateAdminOrSendOtp() throws Exception {
        var body = registration();
        body.put("role", "ADMIN");
        rejectedRegistration(body);
    }

    @ParameterizedTest
    @CsvSource({"termsAccepted,false", "privacyAccepted,false", "termsAccepted,missing", "privacyAccepted,missing"})
    void registrationRequiresEachPolicyConsent(String field, String value) throws Exception {
        var body = registration();
        if (value.equals("missing")) body.remove(field); else body.put(field, false);
        rejectedRegistration(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "12345678", "abcdefgh"})
    void invalidRegistrationPasswordCannotCreateAccount(String password) throws Exception {
        var body = registration();
        body.put("password", password);
        body.put("confirmPassword", password);
        rejectedRegistration(body);
    }

    @Test
    void omittedRolePersistsPendingStudentWithLowercaseAlphanumericPassword() throws Exception {
        var body = registration();
        body.remove("role");
        send("/register", body).andExpect(status().isCreated());
        var saved = accounts.findByEmailIgnoreCase((String) body.get("email")).orElseThrow();
        assertEquals(AccountRole.STUDENT, saved.getRole());
        assertEquals(AccountStatus.PENDING_VERIFICATION, saved.getStatus());
        assertTrue(passwords.matches(PASSWORD, saved.getPasswordHash()));
        assertTrue(saved.getPasswordHash().startsWith("$2a$12$"), "Password must use BCrypt strength 12");
        assertEquals(1, otps.findByAccountId(saved.getId()).size());
        assertTrue(sessions.findByAccountIdAndRevokedAtIsNull(saved.getId()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "username"})
    void duplicateEmailOrUsernameCannotCreateAnotherAccount(String field) throws Exception {
        var original = account(AccountStatus.ACTIVE);
        var body = registration();
        body.put(field, field.equals("email") ? original.getEmail() : original.getUsername());
        long before = accounts.count();
        send("/register", body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_ALREADY_EXISTS"));
        assertEquals(before, accounts.count());
        assertTrue(otps.findByAccountId(original.getId()).isEmpty());
        verifyNoInteractions(sender);
    }

    @Test
    void loginRejectsRoleInjectionAndBindsNormalSessionToStoredStudent() throws Exception {
        var account = account(AccountStatus.ACTIVE);
        send("/login", Map.of("usernameOrEmail", account.getEmail(), "password", PASSWORD, "role", "ADMIN"))
                .andExpect(status().isBadRequest());
        assertTrue(sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).isEmpty());
        var result = send("/login", Map.of("usernameOrEmail", account.getEmail(), "password", PASSWORD, "rememberMe", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.expiresIn").value(900)).andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
        var jwt = tokens.decode(token);
        assertEquals(account.getId().toString(), jwt.getSubject());
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
        var session = sessions.findById(UUID.fromString(jwt.getClaimAsString("sid"))).orElseThrow();
        assertEquals(START.plus(Duration.ofDays(30)), session.getExpiresAt());
        assertTrue(session.isRememberMe());
        assertEquals(AccountRole.STUDENT, accounts.findById(account.getId()).orElseThrow().getRole());
        mvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"PENDING_VERIFICATION", "SUSPENDED", "DEACTIVATED"})
    void restrictedStatusCannotCreateLoginSession(AccountStatus state) throws Exception {
        var account = account(state);
        login(account, PASSWORD).andExpect(status().is(state == AccountStatus.SUSPENDED ? 403 : 401));
        assertTrue(sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).isEmpty());
        assertEquals(state, accounts.findById(account.getId()).orElseThrow().getStatus());
    }

    @Test
    void fifthFailureLocksForExactlyFifteenMinutesAndBoundaryAllowsLogin() throws Exception {
        var account = account(AccountStatus.ACTIVE);
        lockWithFiveFailures(account);
        var locked = accounts.findById(account.getId()).orElseThrow();
        assertEquals(5, locked.getFailedLoginAttempts());
        assertEquals(START.plus(Duration.ofMinutes(15)), locked.getLoginLockedUntil());
        assertTrue(sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).isEmpty());
        clock.now = START.plusSeconds(900).minusMillis(1);
        login(account, PASSWORD).andExpect(status().isLocked());
        assertEquals(5, accounts.findById(account.getId()).orElseThrow().getFailedLoginAttempts());
        clock.now = START.plusSeconds(900);
        login(account, PASSWORD).andExpect(status().isOk());
        var released = accounts.findById(account.getId()).orElseThrow();
        assertNull(released.getLoginLockedUntil());
        assertEquals(0, released.getFailedLoginAttempts());
        assertEquals(1, sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).size());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "DEACTIVATED"})
    void expiredAutomaticLockPreservesManualRestriction(AccountStatus state) throws Exception {
        var account = account(AccountStatus.ACTIVE);
        lockWithFiveFailures(account);
        account = accounts.findById(account.getId()).orElseThrow();
        account.setStatus(state);
        account.setSuspensionReason("Manual restriction remains effective");
        account.setSuspensionEndsAt(null);
        accounts.saveAndFlush(account);
        clock.now = START.plusSeconds(900);
        login(account, PASSWORD).andExpect(status().is(state == AccountStatus.SUSPENDED ? 403 : 401));
        var saved = accounts.findById(account.getId()).orElseThrow();
        assertEquals(state, saved.getStatus());
        assertNull(saved.getLoginLockedUntil());
        assertEquals("Manual restriction remains effective", saved.getSuspensionReason());
        assertNull(saved.getSuspensionEndsAt());
        assertTrue(sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"wrong123,updated123,updated123", "example123,abc123,abc123",
            "example123,abcdefgh,abcdefgh", "example123,12345678,12345678",
            "example123,updated123,different123"})
    void failedPasswordChangePreservesStoredHashAndEverySession(String current, String next, String confirm)
            throws Exception {
        var account = account(AccountStatus.ACTIVE);
        String first = accessToken(login(account, PASSWORD));
        String second = accessToken(login(account, PASSWORD));
        String hashBefore = accounts.findById(account.getId()).orElseThrow().getPasswordHash();
        var before = sessionState(account.getId());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "currentPassword", current, "newPassword", next, "confirmPassword", confirm))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        assertTrue(hashBefore.equals(accounts.findById(account.getId()).orElseThrow().getPasswordHash()),
                "Rejected password change must preserve the exact stored hash");
        assertTrue(before.equals(sessionState(account.getId())), "Rejected password change must preserve every session");
        for (String token : new String[] {first, second}) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        }
    }

    @Test
    void registerOtpExpiresAtExactlyTenMinutesWithoutActivatingAccount() throws Exception {
        var pending = registerPending();
        var otp = otps.findByAccountId(pending.account().getId()).getFirst();
        assertEquals(Duration.ofMinutes(10), Duration.between(otp.getSentAt(), otp.getExpiresAt()));
        clock.now = START.plusSeconds(600);
        verify(pending, pending.code(), "REGISTER").andExpect(status().isBadRequest());
        assertPendingWithoutSessions(pending.account());
        assertNull(otps.findById(otp.getId()).orElseThrow().getUsedAt());
    }

    @Test
    void fiveWrongRegisterOtpsExhaustCodeWithoutActivatingAccount() throws Exception {
        var pending = registerPending();
        String wrong = pending.code().equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) verify(pending, wrong, "REGISTER").andExpect(status().isBadRequest());
        assertEquals(5, otps.findByAccountId(pending.account().getId()).getFirst().getFailedAttempts());
        verify(pending, pending.code(), "REGISTER").andExpect(status().isBadRequest());
        assertPendingWithoutSessions(pending.account());
    }

    @Test
    void registerOtpCannotCreateResetGrantAndRemainsSingleUseForActivation() throws Exception {
        var pending = registerPending();
        verify(pending, pending.code(), "RESET_PASSWORD").andExpect(status().isBadRequest());
        assertPendingWithoutSessions(pending.account());
        assertNull(otps.findByAccountId(pending.account().getId()).getFirst().getResetTokenHash());
        verify(pending, pending.code(), "REGISTER").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.lifecycleStatus").value("ACTIVE"));
        verify(pending, pending.code(), "REGISTER").andExpect(status().isBadRequest());
        assertEquals(1, sessions.findByAccountIdAndRevokedAtIsNull(pending.account().getId()).size());
    }

    @Test
    void retryAfterHeaderReflectsRemainingLockoutAndOtpCooldownSeconds() throws Exception {
        var account = account(AccountStatus.ACTIVE);
        for (int attempt = 0; attempt < 4; attempt++) {
            login(account, "wrong123").andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("Retry-After"));
        }
        login(account, "wrong123")
                .andExpect(status().isLocked())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_TEMP_LOCKED"));
        clock.now = START.plusSeconds(299).plusMillis(500);
        login(account, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(header().string("Retry-After", "601"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_TEMP_LOCKED"));
        clock.now = START.plusSeconds(900).minusMillis(1);
        login(account, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_TEMP_LOCKED"));

        clock.now = START;
        var pending = registerPending();
        send("/resend-otp", Map.of("email", pending.account().getEmail(), "purpose", "REGISTER"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value("OTP_RATE_LIMITED"));
        clock.now = START.plusSeconds(15).plusMillis(200);
        send("/resend-otp", Map.of("email", pending.account().getEmail(), "purpose", "REGISTER"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.errorCode").value("OTP_RATE_LIMITED"));
    }

    @Test
    void preActivationLoginAttemptsAndLockAreClearedOnOtpVerification() throws Exception {
        var pending = registerPending();
        for (int attempt = 0; attempt < 4; attempt++) {
            login(pending.account(), PASSWORD).andExpect(status().isUnauthorized());
        }
        login(pending.account(), PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(header().string("Retry-After", "900"));
        var lockedPending = accounts.findById(pending.account().getId()).orElseThrow();
        assertEquals(5, lockedPending.getFailedLoginAttempts());
        assertNotNull(lockedPending.getLoginLockedUntil());

        verify(pending, pending.code(), "REGISTER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.lifecycleStatus").value("ACTIVE"));
        var activated = accounts.findById(pending.account().getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, activated.getStatus());
        assertEquals(0, activated.getFailedLoginAttempts());
        assertNull(activated.getLoginLockedUntil());

        login(pending.account(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void paddedShortUsernameIsRejectedWhileTrimmedValidUsernameIsPersisted() throws Exception {
        var paddedShort = registration();
        paddedShort.put("username", "  ab  ");
        rejectedRegistration(paddedShort);

        var paddedValid = registration();
        String expectedUsername = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        paddedValid.put("username", "  " + expectedUsername + "  ");
        send("/register", paddedValid).andExpect(status().isCreated());
        var saved = accounts.findByEmailIgnoreCase((String) paddedValid.get("email")).orElseThrow();
        assertEquals(expectedUsername, saved.getUsername());
    }

    @Test
    void staleBearerHeaderIsIgnoredOnPublicAuthEndpointsButEnforcedOnLogoutAndProtectedRoutes() throws Exception {
        String staleHeader = "Bearer stale.invalid.jwt";
        mvc.perform(get("/api/v1/status").header("Authorization", staleHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var body = registration();
        mvc.perform(post("/api/v1/auth/register").header("Authorization", staleHeader)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated());
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(sender).send(message.capture());
        var matcher = Pattern.compile("\\b[0-9]{6}\\b").matcher(message.getValue().getText());
        assertTrue(matcher.find());
        String email = (String) body.get("email");

        mvc.perform(post("/api/v1/auth/resend-otp").header("Authorization", staleHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "purpose", "REGISTER"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        mvc.perform(post("/api/v1/auth/verify-otp").header("Authorization", staleHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "code", matcher.group(), "purpose", "REGISTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.lifecycleStatus").value("ACTIVE"));

        var loginResult = mvc.perform(post("/api/v1/auth/login").header("Authorization", staleHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("usernameOrEmail", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        var refreshCookie = loginResult.getResponse().getCookie("algolearner_refresh");
        assertNotNull(refreshCookie);

        var refreshed = mvc.perform(post("/api/v1/auth/refresh-token").header("Authorization", staleHeader)
                        .header("X-Requested-With", "XMLHttpRequest").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();
        String validToken = json.readTree(refreshed.getResponse().getContentAsString()).path("data").path("token").asText();

        mvc.perform(post("/api/v1/auth/forgot-password").header("Authorization", staleHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/reset-password").header("Authorization", staleHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "resetToken", "invalid-grant",
                                "newPassword", "Updated123", "confirmPassword", "Updated123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", staleHeader)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", staleHeader))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", staleHeader))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + validToken)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isUnauthorized());
    }

    private void rejectedRegistration(Map<String, Object> body) throws Exception {
        long before = accounts.count();
        send("/register", body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        assertEquals(before, accounts.count());
        assertFalse(accounts.existsByEmailIgnoreCase((String) body.get("email")));
        verifyNoInteractions(sender);
    }

    private void lockWithFiveFailures(AccountEntity account) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) login(account, "wrong123").andExpect(status().isUnauthorized());
        login(account, "wrong123").andExpect(status().isLocked());
    }

    private Map<UUID, SessionState> sessionState(UUID accountId) {
        var result = new LinkedHashMap<UUID, SessionState>();
        for (var session : sessions.findByAccountIdAndRevokedAtIsNull(accountId)) {
            result.put(session.getId(), new SessionState(session.getRefreshHash(), session.getExpiresAt(), session.getRevokedAt()));
        }
        return result;
    }

    private AccountEntity account(AccountStatus state) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setEmail(account.getId() + "@test.dev");
        account.setUsername("user" + account.getId().toString().replace("-", ""));
        account.setRole(AccountRole.STUDENT);
        account.setStatus(state);
        account.setPasswordHash(passwords.encode(PASSWORD));
        return accounts.saveAndFlush(account);
    }

    private Map<String, Object> registration() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return new LinkedHashMap<>(Map.of("email", suffix + "@test.dev", "username", "new" + suffix,
                "password", PASSWORD, "confirmPassword", PASSWORD, "role", "STUDENT",
                "termsAccepted", true, "privacyAccepted", true));
    }

    private PendingRegistration registerPending() throws Exception {
        var body = registration();
        send("/register", body).andExpect(status().isCreated());
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(sender).send(message.capture());
        var matcher = Pattern.compile("\\b[0-9]{6}\\b").matcher(message.getValue().getText());
        assertTrue(matcher.find(), "Fixture mail must contain a six-digit OTP");
        return new PendingRegistration(accounts.findByEmailIgnoreCase((String) body.get("email")).orElseThrow(), matcher.group());
    }

    private void assertPendingWithoutSessions(AccountEntity account) {
        assertEquals(AccountStatus.PENDING_VERIFICATION, accounts.findById(account.getId()).orElseThrow().getStatus());
        assertTrue(sessions.findByAccountIdAndRevokedAtIsNull(account.getId()).isEmpty());
    }

    private ResultActions verify(PendingRegistration pending, String code, String purpose) throws Exception {
        return send("/verify-otp", Map.of("email", pending.account().getEmail(), "code", code, "purpose", purpose));
    }

    private ResultActions login(AccountEntity account, String password) throws Exception {
        return send("/login", Map.of("usernameOrEmail", account.getEmail(), "password", password));
    }

    private String accessToken(ResultActions result) throws Exception {
        JsonNode body = json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.path("data").path("token").asText();
    }

    private ResultActions send(String path, Map<String, ?> body) throws Exception {
        return mvc.perform(post("/api/v1/auth" + path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private record PendingRegistration(AccountEntity account, String code) { }
    private record SessionState(String refreshHash, Instant expiresAt, Instant revokedAt) { }

    @TestConfiguration
    static class TimeFixture {
        @Bean @Primary TestClock acceptanceClock() { return new TestClock(); }
    }

    static final class TestClock extends Clock {
        private volatile Instant now = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
