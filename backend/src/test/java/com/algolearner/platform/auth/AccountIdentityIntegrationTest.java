package com.algolearner.platform.auth;

import static org.junit.jupiter.api.Assertions.*;
import com.algolearner.platform.common.exception.AppException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"app.security.jwt-secret=identity-test-only-secret-at-least-32-bytes",
        "app.mail.from=identity-test@example.test"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
@Transactional
class AccountIdentityIntegrationTest extends com.algolearner.platform.support.PostgresIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired AccountIdentityResolver resolver;
    @Autowired AuthService auth;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @MockBean JavaMailSender sender;

    @Test
    void realDatabasePreservesLegacyNamespaceCollisionAndResolvesExplicitly() {
        String shared = "legacy-" + UUID.randomUUID().toString().substring(0, 20) + "@test.dev";
        var emailOwner = save(shared, "owner" + UUID.randomUUID());
        var usernameOwner = save(UUID.randomUUID().toString().substring(0, 20) + "@test.dev", shared);
        assertThrows(AccountIdentityResolver.AmbiguousIdentityException.class, () -> resolver.resolve(shared, IdentityType.AUTO));
        assertEquals(emailOwner.getId(), resolver.resolve(shared.toUpperCase(), IdentityType.EMAIL).orElseThrow().getId());
        assertEquals(usernameOwner.getId(), resolver.resolve(shared, IdentityType.USERNAME).orElseThrow().getId());
        emailOwner.setPasswordHash(passwords.encode("EmailPassword123!"));
        usernameOwner.setPasswordHash(passwords.encode("UsernamePassword123!"));
        assertEquals(emailOwner.getId(), auth.login(new AuthDtos.LoginRequest(shared, "EmailPassword123!", false, IdentityType.EMAIL)).user().id());
        assertEquals(usernameOwner.getId(), auth.login(new AuthDtos.LoginRequest(shared, "UsernamePassword123!", false, IdentityType.USERNAME)).user().id());
    }

    @Test
    void registrationUsesPostgresAdvisoryLocksAndRejectsCrossNamespaceConflict() {
        String legacy = "reserved-" + UUID.randomUUID().toString().substring(0, 20) + "@test.dev";
        save(UUID.randomUUID().toString().substring(0, 20) + "@test.dev", legacy);
        var request = new AuthDtos.RegisterRequest(legacy, "new" + UUID.randomUUID(), "Password123!", "Password123!",
                AccountRole.STUDENT, true, true);
        var error = assertThrows(AppException.class, () -> auth.register(request));
        assertEquals("ACCOUNT_ALREADY_EXISTS", error.getErrorCode());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentCaseVariantsCreateExactlyOneAccount() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String email = "race-" + suffix + "@test.dev";
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int index = 0; index < 2; index++) {
                int variant = index;
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    try {
                        auth.register(new AuthDtos.RegisterRequest(variant == 0 ? email : email.toUpperCase(),
                                "race" + suffix + variant, "Password123!", "Password123!", AccountRole.STUDENT, true, true));
                        return "CREATED";
                    } catch (AppException error) { return error.getErrorCode(); }
                }));
            }
            start.countDown();
            var outcomes = new java.util.ArrayList<String>();
            for (var future : futures) outcomes.add(future.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count(), outcomes.toString());
            assertEquals(1, outcomes.stream().filter("ACCOUNT_ALREADY_EXISTS"::equals).count(), outcomes.toString());
            assertTrue(accounts.findByEmailIgnoreCase(email).isPresent());
        }
    }

    @Test
    void legacyAutoLoginReturnsConflictWithoutChangingCounters() throws Exception {
        String shared = "api-" + UUID.randomUUID().toString().substring(0, 20) + "@test.dev";
        var first = save(shared, "a" + UUID.randomUUID());
        var second = save(UUID.randomUUID() + "@test.dev", shared);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("usernameOrEmail", shared, "password", "wrong"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("AMBIGUOUS_IDENTITY"));
        assertEquals(0, accounts.findById(first.getId()).orElseThrow().getFailedLoginAttempts());
        assertEquals(0, accounts.findById(second.getId()).orElseThrow().getFailedLoginAttempts());
    }

    private AccountEntity save(String email, String username) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID()); account.setEmail(email); account.setUsername(username);
        account.setStatus(AccountStatus.ACTIVE); account.setRole(AccountRole.STUDENT);
        account.setPasswordHash("identity-fixture-hash");
        return accounts.saveAndFlush(account);
    }
}
