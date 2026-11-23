package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "logging.level.root=WARN", "debug=false", "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.security.jwt-secret=classroom-query-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ClassroomQueryCountTest extends PostgresIntegrationTest {
    @Autowired private ClassroomService service;
    @Autowired private AccountRepository accounts;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void studentClassListDoesNotLoadEachClassWithAnotherQuery() {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        for (int i = 0; i < 20; i++) {
            var classroom = service.create(teacher.getId(), new ClassroomDtos.Create(
                    "Class " + i, null, JoinMode.AUTO_JOIN, null, null));
            service.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(student.getUsername()));
        }

        long one = statementCount(() -> service.list(student.getId(), PageRequest.of(0, 1)));
        long twenty = statementCount(() -> service.list(student.getId(), PageRequest.of(0, 20)));
        assertThat(twenty).as("20 classes should not add a query per classroom").isLessThanOrEqualTo(one + 3);
    }

    @Test
    void memberListDoesNotLoadEachStudentWithAnotherQuery() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = service.create(teacher.getId(), new ClassroomDtos.Create(
                "Many members", null, JoinMode.AUTO_JOIN, null, null));
        for (int i = 0; i < 20; i++) {
            var student = account(AccountRole.STUDENT);
            service.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(student.getUsername()));
        }

        long one = statementCount(() -> service.activeMembers(teacher.getId(), classroom.id(), PageRequest.of(0, 1)));
        long twenty = statementCount(() -> service.activeMembers(teacher.getId(), classroom.id(), PageRequest.of(0, 20)));
        assertThat(twenty).as("20 members should not add a query per student").isLessThanOrEqualTo(one + 3);
    }

    @Test
    void pendingRequestListDoesNotLoadEachStudentWithAnotherQuery() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = service.create(teacher.getId(), new ClassroomDtos.Create(
                "Many requests", null, JoinMode.REQUIRE_APPROVAL, null, null));
        for (int i = 0; i < 20; i++) {
            var student = account(AccountRole.STUDENT);
            service.join(student.getId(), new ClassroomDtos.JoinByCode(classroom.classCode()));
        }

        long one = statementCount(() -> service.pendingRequests(teacher.getId(), classroom.id(), PageRequest.of(0, 1)));
        long twenty = statementCount(() -> service.pendingRequests(teacher.getId(), classroom.id(), PageRequest.of(0, 20)));
        assertThat(twenty).as("20 requests should not add a query per student").isLessThanOrEqualTo(one + 3);
    }

    private long statementCount(Runnable action) {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("query" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("unused-test-fixture-hash");
        return accounts.saveAndFlush(account);
    }
}
