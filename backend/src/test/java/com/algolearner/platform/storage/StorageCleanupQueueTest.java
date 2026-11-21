package com.algolearner.platform.storage;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.profile.AvatarStorage;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"logging.level.root=WARN","debug=false","app.security.jwt-secret=avatar-cleanup-test-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class StorageCleanupQueueTest extends PostgresIntegrationTest {
    @Autowired StorageCleanupQueue queue;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PlatformTransactionManager manager;
    @TempDir Path directory;

    @BeforeEach void isolateJobs() { jdbc.update("delete from tbl_storage_cleanup_job"); }

    static String key() { return UUID.randomUUID()+"/"+UUID.randomUUID(); }
    static <T> ObjectProvider<T> provider(Class<T> type,T value) {
        var beans=new StaticListableBeanFactory();beans.addBean("fixture",value);return beans.getBeanProvider(type);
    }
    String status(String key) {
        return jdbc.queryForObject("select status from tbl_storage_cleanup_job where object_key=?",String.class,key);
    }
    StorageCleanupWorker worker(StorageCleanupQueue queue,PrivateObjectStorage objects) {
        return new StorageCleanupWorker(queue,objects,2,60);
    }
    void expireLease(UUID id) {
        jdbc.update("update tbl_storage_cleanup_job set lease_until=current_timestamp-interval '1 second' where id=?",id);
    }

    @Test void failedAvatarDeletionSurvivesRollbackAndRetryUsesNoDatabaseTransaction() {
        var objects=mock(PrivateObjectStorage.class);var key=key();
        doThrow(new IllegalStateException("provider unavailable")).when(objects).delete(key);
        var avatars=new AvatarStorage(directory.toString(),5_242_880L,16_000_000L,"image/png",
                provider(PrivateObjectStorage.class,objects),provider(MalwareScanner.class,bytes->{}),
                provider(StorageCleanupQueue.class,queue));
        new TransactionTemplate(manager).executeWithoutResult(tx->{avatars.delete(key);tx.setRollbackOnly();});
        assertThat(status(key)).isEqualTo("PENDING");
        // A new queue instance sees the committed job, independently of the original request.
        var restarted=new StorageCleanupQueue(jdbc,manager);restarted.enqueue(key);restarted.enqueue(key);
        assertThat(jdbc.queryForObject("select count(*) from tbl_storage_cleanup_job where object_key=?",Long.class,key)).isEqualTo(1);
        reset(objects);
        doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return null;})
                .when(objects).delete(key);
        new TransactionTemplate(manager).executeWithoutResult(tx->assertThat(worker(restarted,objects).runOnce()).isTrue());
        assertThat(status(key)).isEqualTo("COMPLETED");verify(objects).delete(key);
    }

    @Test void activeAvatarIsNeverDeletedAndArbitraryKeysAreRejected() {
        var key=key();var objects=mock(PrivateObjectStorage.class);
        var account=new AccountEntity();account.setId(UUID.randomUUID());account.setUsername("cleanup_"+account.getId().toString().replace("-",""));
        account.setEmail(account.getUsername()+"@example.test");account.setPasswordHash("unused");account.setRole(AccountRole.STUDENT);
        account.setStatus(AccountStatus.ACTIVE);account.setAvatarKey(key);accounts.saveAndFlush(account);
        queue.enqueue(key);assertThat(worker(queue,objects).runOnce()).isTrue();verifyNoInteractions(objects);
        assertThat(status(key)).isEqualTo("CANCELLED");
        var avatars=new AvatarStorage(directory.toString(),1000,1000,"image/png",provider(PrivateObjectStorage.class,objects),
                provider(MalwareScanner.class,bytes->{}),provider(StorageCleanupQueue.class,queue));
        avatars.delete(key);verifyNoInteractions(objects);
        assertThatThrownBy(()->queue.enqueue("https://example.test/object")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->queue.enqueue("00000000-0000-0000-0000-000000000000/../../private")).isInstanceOf(IllegalArgumentException.class);
        // Once the user replaces the avatar, cancellation must not prevent later compensation.
        jdbc.update("update tbl_account set avatar_key=null where id=?",account.getId());
        queue.enqueue(key);assertThat(status(key)).isEqualTo("PENDING");
        assertThat(worker(queue,objects).runOnce()).isTrue();verify(objects).delete(key);
    }

    @Test void retriesUseBackoffAndStopInVisibleFailedState() {
        var key=key();var objects=mock(PrivateObjectStorage.class);doThrow(new IllegalStateException("do not persist provider details")).when(objects).delete(key);
        queue.enqueue(key);var worker=worker(queue,objects);assertThat(worker.runOnce()).isTrue();
        assertThat(jdbc.queryForObject("select attempts from tbl_storage_cleanup_job where object_key=?",Integer.class,key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select available_at>current_timestamp+interval '50 seconds' from tbl_storage_cleanup_job where object_key=?",Boolean.class,key)).isTrue();
        assertThat(worker.runOnce()).isFalse();
        jdbc.update("update tbl_storage_cleanup_job set available_at=current_timestamp-interval '1 second' where object_key=?",key);
        assertThat(worker.runOnce()).isTrue();assertThat(status(key)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error_code from tbl_storage_cleanup_job where object_key=?",String.class,key)).isEqualTo("PRIVATE_AVATAR_DELETE_FAILED");
        queue.enqueue(key);assertThat(worker.runOnce()).isFalse();verify(objects,times(2)).delete(key);
    }

    @Test void lockedJobIsSkippedByASecondWorker() throws Exception {
        String first=key(),second=key();queue.enqueue(first);queue.enqueue(second);
        var objects=mock(PrivateObjectStorage.class);
        try(var executor=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                jdbc.queryForObject("select id from tbl_storage_cleanup_job where object_key=? for update",UUID.class,first);
                try { assertThat(executor.submit(()->worker(queue,objects).runOnce()).get(5,TimeUnit.SECONDS)).isTrue(); }
                catch(Exception failure) { throw new AssertionError("Another worker must skip the locked job",failure); }
                verify(objects).delete(second);verify(objects,never()).delete(first);
            });
        }
        assertThat(status(first)).isEqualTo("PENDING");assertThat(status(second)).isEqualTo("COMPLETED");
    }

    @Test void expiredLeaseCanBeReclaimedButStaleTokenCannotAcknowledgeOrRetryIt() {
        var key=key();queue.enqueue(key);var stale=queue.claim(3,120).orElseThrow();
        assertThat(queue.claim(3,120)).isEmpty();expireLease(stale.id());
        var fresh=queue.claim(3,120).orElseThrow();assertThat(fresh.token()).isNotEqualTo(stale.token());
        assertThat(fresh.attempts()).isEqualTo(2);assertThat(queue.ownsLease(stale)).isFalse();
        queue.finish(stale,false);queue.fail(stale,3,1);assertThat(status(key)).isEqualTo("PROCESSING");
        queue.finish(fresh,false);assertThat(status(key)).isEqualTo("COMPLETED");
    }

    @Test void repeatedWorkerCrashesReachTheFailedStateAfterTheAttemptLimit() {
        var key=key();queue.enqueue(key);var first=queue.claim(2,120).orElseThrow();expireLease(first.id());
        var second=queue.claim(2,120).orElseThrow();expireLease(second.id());
        assertThat(queue.claim(2,120)).isEmpty();assertThat(status(key)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error_code from tbl_storage_cleanup_job where object_key=?",String.class,key)).isEqualTo("CLEANUP_LEASE_EXPIRED");
    }
}
