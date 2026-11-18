package com.algolearner.platform.announcement;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=announcement-concurrency-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AnnouncementConcurrencyTest extends PostgresIntegrationTest {
    @Autowired AnnouncementService announcements;
    @Autowired ClassroomService classrooms;
    @Autowired ClassroomRepository classroomRepository;
    @Autowired AccountRepository accounts;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void formerOwnerCannotCreateWhileOwnershipTransferCommits() throws Exception {
        var oldOwner = teacher();
        var nextOwner = teacher();
        var classroom = classrooms.create(oldOwner.getId(), new ClassroomDtos.Create(
                "Announcement transfer", null, JoinMode.AUTO_JOIN, null, null));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var transfer = executor.submit(() -> transaction.executeWithoutResult(status -> {
                var entity = classrooms.lockWritable(classroom.id());
                entity.setOwner(nextOwner);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Transfer release timed out"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var create = executor.submit(() -> {
                try {
                    announcements.create(oldOwner.getId(), classroom.id(), new AnnouncementDtos.CreateRequest("Title", "Content"));
                    return true;
                } catch (com.algolearner.platform.common.exception.AppException denied) { return false; }
            });
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> create.get(1, TimeUnit.SECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally { release.countDown(); }
            transfer.get(10, TimeUnit.SECONDS);
            assertThat(create.get(10, TimeUnit.SECONDS)).isFalse();
        }
    }

    private AccountEntity teacher() {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("announcement-" + account.getId());
        account.setEmail(account.getUsername() + "@example.test");
        account.setFullName("Teacher");
        account.setPasswordHash("unused-test-fixture-hash");
        account.setRole(AccountRole.TEACHER);
        account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
}
