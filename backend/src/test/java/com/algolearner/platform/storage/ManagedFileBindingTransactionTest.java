package com.algolearner.platform.storage;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=file-binding-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ManagedFileBindingTransactionTest extends PostgresIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired ClassroomService classrooms;
    @Autowired ManagedFileService files;
    @Autowired FileRecordService records;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @Test
    void cleanupCannotClaimFileBetweenReadyCheckAndReferenceCommit() throws Exception {
        var teacher = new AccountEntity();
        teacher.setId(UUID.randomUUID());
        teacher.setUsername("binding_" + UUID.randomUUID().toString().replace("-", ""));
        teacher.setEmail(teacher.getUsername() + "@example.test");
        teacher.setPasswordHash("unused");
        teacher.setRole(AccountRole.TEACHER);
        teacher.setStatus(AccountStatus.ACTIVE);
        accounts.saveAndFlush(teacher);
        var classroom = classrooms.create(teacher.getId(),
                new ClassroomDtos.Create("File binding", null, JoinMode.AUTO_JOIN, null, null));
        var file = records.begin(teacher.getId(), classroom.id(), FilePurpose.LESSON_MATERIAL,
                new ValidatedFile("notes.pdf", "application/pdf", FileKind.PDF, new byte[]{1}), "a".repeat(64));
        records.uploaded(file.getId());
        records.ready(teacher.getId(), file.getId());
        jdbc.update("UPDATE tbl_stored_file SET updated_at=? WHERE id=?",
                Timestamp.from(Instant.now().minusSeconds(172800)), file.getId());
        UUID lesson = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_lesson(id,classroom_id,title) VALUES (?,?,?)", lesson, classroom.id(), "Materials");

        var tx = new TransactionTemplate(manager);
        tx.setTimeout(10);
        var cleanupStarted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = new AtomicReference<Future<FileRecordService.ProcessingTicket>>();
            tx.executeWithoutResult(status -> {
                // This is the same aggregate/file lock order used by LessonService.addMaterial.
                classrooms.lockReadable(classroom.id());
                files.requireReady(teacher.getId(), classroom.id(), file.getId(), FilePurpose.LESSON_MATERIAL);
                cleanup.set(executor.submit(() -> {
                    cleanupStarted.countDown();
                    return records.claimDeletion(file.getId());
                }));
                try {
                    assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
                    // A correct binding holds its file lock until the reference is committed.
                    // A premature claim is collected below and asserted after both transactions finish.
                    cleanup.get().get(500, TimeUnit.MILLISECONDS);
                } catch (TimeoutException expected) {
                    // Cleanup is waiting for the binding transaction to commit.
                } catch (Exception error) {
                    throw new IllegalStateException("Concurrent cleanup failed", error);
                }
                jdbc.update("""
                        INSERT INTO tbl_lesson_material(id,lesson_id,file_name,file_url,file_type,file_size,stored_file_id,material_state)
                        VALUES (?,?,'notes.pdf','/api/v1/files/content','PDF',1,?,'READY')
                        """, UUID.randomUUID(), lesson, file.getId());
            });
            assertThat(cleanup.get().get(5, TimeUnit.SECONDS))
                    .as("cleanup must recheck references after the binding transaction commits")
                    .isNull();
            assertThat(jdbc.queryForObject("SELECT state FROM tbl_stored_file WHERE id=?", String.class, file.getId()))
                    .isEqualTo("READY");
        }
    }
}
