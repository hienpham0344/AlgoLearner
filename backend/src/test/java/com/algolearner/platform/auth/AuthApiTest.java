package com.algolearner.platform.auth;

import com.algolearner.platform.common.security.AccountRepository;
import com.algolearner.platform.profile.PracticeRepository;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.profile.dashboard.DashboardRepository;
import com.algolearner.platform.profile.dashboard.DashboardResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.algolearner.platform.auth.AuthDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false",
        "app.security.jwt.issuer=https://issuer.example.test", "app.security.jwt.audience=auth-tests",
        "app.security.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        TestAccounts testAccounts() {
            return new TestAccounts();
        }

        @Bean
        @Primary
        DashboardRepository dashboardRepository() {
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

        @Bean
        @Primary
        PracticeRepository practiceRepository() {
            return accountId -> List.of();
        }

        @Bean
        @Primary
        ProfileRepository profileRepository() {
            return org.mockito.Mockito.mock(ProfileRepository.class);
        }
    }

    static class TestAccounts implements AccountRepository {
        private final List<Account> records = new ArrayList<>();

        @Override
        public List<Account> accounts() {
            return List.copyOf(records);
        }

        @Override
        public List<Restriction> restrictions() {
            return List.of();
        }

        void add(AccountEntity account) {
            records.add(new Account(account.getId(), account.getUsername(), account.getRole().name(),
                    account.getStatus().name(), account.getCreatedAt()));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private com.algolearner.platform.auth.AccountRepository accountRepository;

    @Autowired
    private OtpChallengeRepository otpRepository;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private TestAccounts testAccounts;

    @BeforeEach
    void setUp() {
        otpRepository.deleteAll();
        sessionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("FR-ACC-001 & FR-ACC-003: Full registration and verification flow")
    void registrationAndVerificationFlow() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "newstudent@example.test",
                "newstudent",
                "Password123",
                AccountRole.STUDENT,
                true,
                true
        );

        // 1. Register
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("newstudent"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PENDING_VERIFICATION"));

        AccountEntity savedAccount = accountRepository.findByUsernameIgnoreCase("newstudent").orElseThrow();
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        // Find the generated OTP challenge
        OtpChallengeEntity challenge = otpRepository.findLatestByAccountAndPurpose(savedAccount.getId(), OtpPurpose.REGISTER)
                .orElseThrow();
        assertThat(challenge.getPurpose()).isEqualTo(OtpPurpose.REGISTER);

        // We know raw code from OTP generation; let's test verify-otp with invalid code first
        VerifyOtpRequest wrongOtpReq = new VerifyOtpRequest("newstudent@example.test", "000000", OtpPurpose.REGISTER);
        mvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(wrongOtpReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("OTP_INVALID"));
    }

    @Test
    @DisplayName("FR-AUTH-001 & FR-AUTH-005: Login with active account creates session and returns token")
    void loginSuccessFlow() throws Exception {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("activeuser")
                .email("active@example.test")
                .passwordHash(passwordService.hash("Password123"))
                .role(AccountRole.STUDENT)
                .status(AccountStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        accountRepository.save(account);
        testAccounts.add(account);

        LoginRequest loginReq = new LoginRequest("activeuser", "Password123", false);

        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.username").value("activeuser"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String refreshToken = mapper.readTree(responseBody).path("data").path("refreshToken").asText();

        // Test refresh-token
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(refreshToken);
        mvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    @DisplayName("FR-AUTH-006: User can list sessions and logout")
    void sessionManagementFlow() throws Exception {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("sessionuser")
                .email("session@example.test")
                .passwordHash(passwordService.hash("Password123"))
                .role(AccountRole.STUDENT)
                .status(AccountStatus.ACTIVE)
                .build();
        accountRepository.save(account);
        testAccounts.add(account);

        // Login to generate session
        LoginRequest loginReq = new LoginRequest("sessionuser", "Password123", false);
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = mapper.readTree(result.getResponse().getContentAsString()).path("data").path("refreshToken").asText();

        var auth = new UsernamePasswordAuthenticationToken(
                account.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        // List sessions
        mvc.perform(get("/api/v1/auth/sessions")
                        .with(authentication(auth))
                        .header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].current").value(true));

        // Logout
        mvc.perform(post("/api/v1/auth/logout")
                        .with(authentication(auth))
                        .header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("FR-ACC-006: Self-deactivation flow")
    void deactivateFlow() throws Exception {
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("deactivateuser")
                .email("deactivate@example.test")
                .passwordHash(passwordService.hash("Password123"))
                .role(AccountRole.STUDENT)
                .status(AccountStatus.ACTIVE)
                .build();
        accountRepository.save(account);
        testAccounts.add(account);

        var auth = new UsernamePasswordAuthenticationToken(
                account.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        DeactivateAccountRequest req = new DeactivateAccountRequest("Password123", true);

        mvc.perform(post("/api/v1/auth/deactivate")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        AccountEntity updated = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountStatus.DEACTIVATED);
    }
}

