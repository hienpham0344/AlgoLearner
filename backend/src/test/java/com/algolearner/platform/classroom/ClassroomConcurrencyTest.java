package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=classroom-concurrency-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ClassroomConcurrencyTest extends PostgresIntegrationTest {
    @Autowired private ClassroomService service;
    @Autowired private AccountRepository accounts;
    @Autowired private ClassroomMembershipRepository memberships;
    @Autowired private EntityManager entityManager;
    @SpyBean private ClassroomInvitationRepository invitations;

    @Test
    void simultaneousCodeJoinAndInvitationAcceptanceCreateOneMembership() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var classroom = service.create(teacher.getId(), new ClassroomDtos.Create(
                "Concurrent joining", null, JoinMode.AUTO_JOIN, null, null));

        for (int round = 0; round < 10; round++) {
            var student = account(AccountRole.STUDENT);
            UUID invitationId = service.invite(teacher.getId(), classroom.id(),
                    new ClassroomDtos.Invite(student.getEmail(), Instant.now().plusSeconds(3600)));
            var barrier = new CyclicBarrier(2);
            try (var executor = Executors.newFixedThreadPool(2)) {
                Callable<Throwable> join = () -> attempt(barrier, () -> service.join(student.getId(),
                        new ClassroomDtos.JoinByCode(classroom.classCode())));
                Callable<Throwable> accept = () -> attempt(barrier, () -> service.acceptInvitation(student.getId(), invitationId));
                var joinResult = executor.submit(join);
                var acceptResult = executor.submit(accept);
                Throwable joinFailure = joinResult.get(20, TimeUnit.SECONDS);
                Throwable acceptFailure = acceptResult.get(20, TimeUnit.SECONDS);
                if (joinFailure != null) {
                    assertThat(joinFailure).isInstanceOf(AppException.class);
                    assertThat(((AppException) joinFailure).getErrorCode()).isEqualTo("ALREADY_MEMBER");
                }
                assertThat(acceptFailure).isNull();
                var membership = memberships.findByClassroomIdAndStudentId(classroom.id(), student.getId()).orElseThrow();
                assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
            }
        }
    }

    @Test
    void removingStudentWinsOverInvitationAcceptanceThatReadAnOldPendingState() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = service.create(teacher.getId(), new ClassroomDtos.Create(
                "Removal race", null, JoinMode.AUTO_JOIN, null, null));
        service.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(student.getUsername()));
        UUID invitationId = service.invite(teacher.getId(), classroom.id(),
                new ClassroomDtos.Invite(student.getEmail(), Instant.now().plusSeconds(3600)));

        var invitationRead = new CountDownLatch(1);
        var releaseAccept = new CountDownLatch(1);
        doAnswer(call -> {
            var result = Optional.ofNullable(entityManager.find(ClassroomInvitationEntity.class, invitationId));
            invitationRead.countDown();
            if (!releaseAccept.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Invitation read barrier timed out");
            return result;
        }).when(invitations).findById(invitationId);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var accept = executor.submit(() -> attempt(() -> service.acceptInvitation(student.getId(), invitationId)));
            if (!invitationRead.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Acceptance did not read invitation", accept.get(1, TimeUnit.SECONDS));
            }
            var remove = executor.submit(() -> service.removeStudent(teacher.getId(), classroom.id(), student.getId()));
            boolean removeCommittedBeforeAcceptContinued;
            try {
                remove.get(2, TimeUnit.SECONDS);
                removeCommittedBeforeAcceptContinued = true;
            } catch (TimeoutException blockedByPairLock) {
                removeCommittedBeforeAcceptContinued = false;
            } finally {
                releaseAccept.countDown();
            }
            Throwable acceptFailure = accept.get(20, TimeUnit.SECONDS);
            remove.get(20, TimeUnit.SECONDS);

            if (removeCommittedBeforeAcceptContinued) {
                assertThat(acceptFailure).isInstanceOf(AppException.class);
                assertThat(((AppException) acceptFailure).getErrorCode()).isEqualTo("INVITATION_UNAVAILABLE");
            }
            assertThat(memberships.findByClassroomIdAndStudentId(classroom.id(), student.getId()).orElseThrow().getStatus())
                    .isEqualTo(MembershipStatus.REMOVED);
        } finally {
            releaseAccept.countDown();
        }
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("concurrent" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("unused-test-fixture-hash");
        return accounts.saveAndFlush(account);
    }

    private static Throwable attempt(CyclicBarrier barrier, Runnable action) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
            action.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private static Throwable attempt(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }
}
