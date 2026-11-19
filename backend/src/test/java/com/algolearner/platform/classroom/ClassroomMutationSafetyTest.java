package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import static org.mockito.Mockito.doAnswer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=classroom-mutation-test-only-secret-at-least-32-bytes", "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ClassroomMutationSafetyTest extends PostgresIntegrationTest {
    @Autowired ClassroomService service;
    @Autowired AccountRepository accounts;
    @SpyBean ClassroomRepository classrooms;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void formerOwnerCannotOverwriteTransferredOwnershipFromManagedSnapshot() throws Exception {
        var oldOwner = account(AccountRole.TEACHER);
        var newOwner = account(AccountRole.TEACHER);
        var admin = account(AccountRole.ADMIN);
        var classroom = service.create(oldOwner.getId(), new ClassroomDtos.Create("Before", null, JoinMode.AUTO_JOIN, null, null));
        var loaded = new CountDownLatch(1);
        var transferred = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var staleUpdate = executor.submit(() -> catchThrowable(() -> transactions.executeWithoutResult(status -> {
                entityManager.find(ClassroomEntity.class, classroom.id());
                loaded.countDown(); await(transferred);
                service.update(oldOwner.getId(), classroom.id(), new ClassroomDtos.Update("Stale overwrite", null));
            })));
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            service.transferOwner(admin.getId(), classroom.id(), newOwner.getId());
            transferred.countDown();
            assertThat(staleUpdate.get(10, TimeUnit.SECONDS)).isInstanceOf(AppException.class);
        } finally { transferred.countDown(); }
        assertThat(service.get(admin.getId(), classroom.id()).ownerId()).isEqualTo(newOwner.getId());
        assertThat(service.get(admin.getId(), classroom.id()).name()).isEqualTo("Before");
    }

    @Test
    void joinRechecksCodeAfterRotationCommits() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = service.create(teacher.getId(), new ClassroomDtos.Create("Code race", null, JoinMode.AUTO_JOIN, null, null));
        var resolved = new CountDownLatch(1);
        var rotated = new CountDownLatch(1);
        doAnswer(call -> {
            var result = java.util.Optional.ofNullable(entityManager.createQuery(
                    "select c from ClassroomEntity c where c.classCode = :code", ClassroomEntity.class)
                    .setParameter("code", classroom.classCode()).getResultStream().findFirst().orElse(null));
            resolved.countDown(); await(rotated); return result;
        }).when(classrooms).findByClassCode(classroom.classCode());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var join = executor.submit(() -> catchThrowable(() -> service.join(student.getId(),
                    new ClassroomDtos.JoinByCode(classroom.classCode()))));
            assertThat(resolved.await(10, TimeUnit.SECONDS)).isTrue();
            service.regenerateCode(teacher.getId(), classroom.id());
            rotated.countDown();
            assertThat(join.get(10, TimeUnit.SECONDS)).isInstanceOf(AppException.class)
                    .extracting(error -> ((AppException) error).getErrorCode()).isEqualTo("CLASS_CODE_NOT_FOUND");
        } finally { rotated.countDown(); }
        assertThat(jdbc.queryForObject("select count(*) from tbl_classroom_membership where classroom_id = ?", Integer.class, classroom.id())).isZero();
    }

    @Test
    void ambiguousDirectAddDoesNotCreateMembership() {
        var teacher = account(AccountRole.TEACHER);
        var first = account(AccountRole.STUDENT);
        var second = account(AccountRole.STUDENT);
        second.setEmail("ambiguous_" + UUID.randomUUID().toString().substring(0, 8) + "@example.test"); accounts.saveAndFlush(second);
        first.setUsername(second.getEmail()); accounts.saveAndFlush(first);
        var classroom = service.create(teacher.getId(), new ClassroomDtos.Create("Identity", null, JoinMode.AUTO_JOIN, null, null));
        assertThatThrownBy(() -> service.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(first.getUsername())))
                .isInstanceOf(AppException.class).extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo("AMBIGUOUS_IDENTITY");
        assertThat(jdbc.queryForObject("select count(*) from tbl_classroom_membership where classroom_id = ?", Integer.class, classroom.id())).isZero();
    }

    @Test
    void ownerTransferAuditCommitsAndRollsBackWithOwnership() {
        var oldOwner = account(AccountRole.TEACHER);
        var newOwner = account(AccountRole.TEACHER);
        var admin = account(AccountRole.ADMIN);
        var classroom = service.create(oldOwner.getId(), new ClassroomDtos.Create("Audit", null, JoinMode.AUTO_JOIN, null, null));
        transactions.executeWithoutResult(status -> {
            service.transferOwner(admin.getId(), classroom.id(), newOwner.getId());
            status.setRollbackOnly();
        });
        assertThat(service.get(admin.getId(), classroom.id()).ownerId()).isEqualTo(oldOwner.getId());
        assertThat(auditCount(classroom.id())).isZero();
        service.transferOwner(admin.getId(), classroom.id(), newOwner.getId());
        assertThat(auditCount(classroom.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select before_state from tbl_audit_log where target_id = ?", String.class, classroom.id().toString())).contains(oldOwner.getId().toString());
        assertThat(jdbc.queryForObject("select after_state from tbl_audit_log where target_id = ?", String.class, classroom.id().toString())).contains(newOwner.getId().toString());
    }

    private int auditCount(UUID id) { return jdbc.queryForObject("select count(*) from tbl_audit_log where target_id = ?", Integer.class, id.toString()); }
    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity(); account.setId(UUID.randomUUID());
        account.setUsername("safety_" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test"); account.setPasswordHash("unused");
        account.setRole(role); account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race barrier timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
