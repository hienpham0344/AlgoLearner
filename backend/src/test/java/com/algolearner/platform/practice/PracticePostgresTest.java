package com.algolearner.platform.practice;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.JoinMode;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.practice.PracticeDtos.CodeRequest;
import com.algolearner.platform.practice.PracticeDtos.CreatePractice;
import com.algolearner.platform.practice.PracticeDtos.TestcaseInput;
import com.algolearner.platform.profile.PracticeRepository;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.profile.dashboard.DashboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real schema on PostgreSQL: Flyway migrates every V*.sql, Hibernate {@code ddl-auto: validate} checks the
 * entities against it, and the concurrency guarantees of SRS 4.8 are exercised with real transactions and row locks
 * (H2 cannot show these). Opt-in against a scratch database, never the shared one:
 * <pre>
 * mvn test -Dtest=PracticePostgresTest -Dpostgres.check=true -Dpostgres.url=jdbc:postgresql://localhost:5432/scratch
 *          -Dpostgres.username=... -Dpostgres.password=...
 * </pre>
 */
@EnabledIfSystemProperty(named = "postgres.check", matches = "true")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        // Two connections only: if SUBMIT held a transaction while judging, the 4th parallel request could not start.
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.connection-timeout=8000",
        "logging.level.root=WARN", "debug=false",
        "app.security.jwt.issuer=https://issuer.example.test", "app.security.jwt.audience=practice-tests",
        "app.security.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"})
@ActiveProfiles("test")
class PracticePostgresTest {
    private static final int PARALLEL = 4;
    /** Released once PARALLEL submissions are inside the judge at the same time. */
    private static volatile CountDownLatch insideJudge = new CountDownLatch(PARALLEL);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("postgres.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("postgres.username"));
        registry.add("spring.datasource.password", () -> System.getProperty("postgres.password", ""));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-15T03:30:00Z"), ZoneOffset.UTC); }
        @Bean @Primary com.algolearner.platform.common.security.AccountRepository securityAccounts() {
            return new com.algolearner.platform.common.security.AccountRepository() {
                @Override public List<Account> accounts() { return List.of(); }
                @Override public List<Restriction> restrictions() { return List.of(); }
            };
        }
        @Bean @Primary PracticeRepository practiceRepository() { return accountId -> List.of(); }
        @Bean @Primary ProfileRepository profileRepository() { return org.mockito.Mockito.mock(ProfileRepository.class); }
        @Bean @Primary DashboardRepository dashboardRepository() { return org.mockito.Mockito.mock(DashboardRepository.class); }
        @Bean @Primary PracticeJudgePort blockingJudge() {
            return new PracticeJudgePort() {
                @Override public RunResult run(RunRequest request) { throw new UnsupportedOperationException(); }
                @Override public SubmitResult submit(SubmitRequest request) {
                    insideJudge.countDown();
                    try {
                        if (!insideJudge.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("submissions did not overlap in the judge");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return new SubmitResult(PracticeVerdict.ACCEPTED, request.testcases().size(), 5, 1024, null);
                }
            };
        }
    }

    @Autowired PracticeService service;
    @Autowired AccountRepository accounts;
    @Autowired ClassroomRepository classrooms;
    @Autowired ClassroomMembershipRepository memberships;
    @Autowired PracticeSubmissionRepository submissions;
    @Autowired PracticeTestcaseRepository testcases;
    @Autowired ClassroomPracticeRepository practices;
    @Autowired JdbcTemplate jdbc;

    private AccountEntity teacher;
    private AccountEntity student;
    private ClassroomEntity classroom;
    private UUID practiceId;

    @BeforeEach
    void seed() {
        insideJudge = new CountDownLatch(PARALLEL);
        teacher = account("teacher", AccountRole.TEACHER);
        student = account("student", AccountRole.STUDENT);
        classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID());
        classroom.setOwner(teacher);
        classroom.setName("PG");
        classroom.setStatus(ClassroomStatus.ACTIVE);
        classroom.setClassCode(UUID.randomUUID().toString().substring(0, 8));
        classroom.setJoiningOpen(true);
        classroom.setJoinMode(JoinMode.AUTO_JOIN);
        classrooms.save(classroom);
        var membership = new ClassroomMembershipEntity();
        membership.setId(UUID.randomUUID());
        membership.setClassroom(classroom);
        membership.setStudent(student);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(Instant.parse("2026-11-01T00:00:00Z"));
        memberships.save(membership);
        var created = service.create(teacher.getId(), classroom.getId(), new CreatePractice("Sum", null, null, null,
                Set.of(PracticeLanguage.JAVA), null, null, null, PracticeStatus.PUBLISHED,
                List.of(new TestcaseInput("1 2", "3", true, null), new TestcaseInput("5 5", "10", false, null)), null, null));
        practiceId = created.id();
    }

    @Test
    void flywayMigratedAndEntitiesMatchTheSchema() {
        // reaching this line already means Flyway ran every migration and ddl-auto=validate accepted the entities
        List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'tbl_practice_submission'", String.class);
        assertThat(columns).contains("tests_passed", "total_tests");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isGreaterThanOrEqualTo(5);
        // the sandbox ceiling (5 s / 512 MB) is enforced by the schema as well as by the API
        for (String limits : List.of("time_limit_ms = 5001", "memory_limit_mb = 513")) {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                    jdbc.update("UPDATE tbl_classroom_practice SET " + limits + " WHERE id = ?", practiceId));
        }
    }

    @Test
    void parallelAcceptedSubmissionsNeitherHoldConnectionsWhileJudgingNorBreakCompletion() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < PARALLEL; i++) {
                calls.add(pool.submit(() -> service.submit(student.getId(), classroom.getId(), practiceId,
                        new CodeRequest(PracticeLanguage.JAVA, "class Main {}"))));
            }
            for (Future<?> call : calls) call.get(60, TimeUnit.SECONDS); // any DataIntegrityViolation / timeout surfaces here
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_practice_submission WHERE practice_id = ?", Integer.class, practiceId)).isEqualTo(PARALLEL);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_practice_completion WHERE student_id = ? AND practice_id = ?", Integer.class,
                student.getId(), practiceId)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT tests_passed || '/' || total_tests FROM tbl_practice_submission WHERE practice_id = ?", String.class, practiceId))
                .containsOnly("2/2");
        // the completion points at one of this practice's stored submissions
        assertThat(jdbc.queryForObject("SELECT first_accepted_submission_id FROM tbl_practice_completion WHERE practice_id = ?", UUID.class, practiceId))
                .isIn(jdbc.queryForList("SELECT id FROM tbl_practice_submission WHERE practice_id = ?", UUID.class, practiceId));
    }

    @Test
    void parallelTestcaseEditsVersionExactlyOnce() throws Exception {
        insideJudge = new CountDownLatch(1);
        service.submit(student.getId(), classroom.getId(), practiceId, new CodeRequest(PracticeLanguage.JAVA, "class Main {}"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CountDownLatch(1);
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int n = i;
                calls.add(pool.submit(() -> {
                    barrier.await();
                    return service.addTestcase(teacher.getId(), classroom.getId(), practiceId, new TestcaseInput("in" + n, "out" + n, false, null), false);
                }));
            }
            barrier.countDown();
            for (Future<?> call : calls) call.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        // v1 had a submission, so the first edit snapshots v2 (2 copied + 1 new); the second edit lands in v2 as well,
        // instead of both racing to create their own v2 copy.
        assertThat(practices.findById(practiceId).orElseThrow().getTestcaseVersion()).isEqualTo(2);
        assertThat(testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practiceId, 1)).hasSize(2);
        assertThat(testcases.findByPracticeIdAndVersionOrderByOrderIndexAsc(practiceId, 2)).hasSize(4);
    }

    private AccountEntity account(String name, AccountRole role) {
        var a = new AccountEntity();
        a.setId(UUID.randomUUID());
        a.setUsername(name + UUID.randomUUID().toString().substring(0, 8));
        a.setEmail(name + UUID.randomUUID() + "@example.test");
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        return accounts.save(a);
    }
}
