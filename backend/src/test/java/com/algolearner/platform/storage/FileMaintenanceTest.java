package com.algolearner.platform.storage;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.algolearner.platform.common.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=file-maintenance-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class FileMaintenanceTest extends PostgresIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired ClassroomService classrooms;
    @Autowired ManagedFileService files;
    @Autowired FileRecordService records;
    @Autowired FileMaintenanceService maintenance;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockBean PrivateObjectStorage objects;
    @MockBean MalwareScanner scanner;
    private final HashMap<String, byte[]> blobs = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            blobs.put(call.getArgument(0), call.getArgument(2)); return null;
        }).when(objects).upload(anyString(),anyString(),any());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            ((java.io.OutputStream)call.getArgument(1)).write(blobs.get(call.getArgument(0))); return null;
        }).when(objects).download(anyString(),any());
    }

    @Test
    void scannerOutageCanRecoverPersistedPrivateUploadWithoutHoldingDatabaseTransaction() {
        var teacher = teacher();
        var classroom = classrooms.create(teacher.getId(),new ClassroomDtos.Create("Files",null,JoinMode.AUTO_JOIN,null,null));
        doThrow(new StorageException("FILE_SCAN_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE,"Unavailable")).doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return null;
        }).when(scanner).requireClean(any());
        assertThatThrownBy(() -> files.upload(teacher.getId(),classroom.id(),FilePurpose.LESSON_MATERIAL,pdf())).isInstanceOf(AppException.class);
        UUID fileId = jdbc.queryForObject("SELECT id FROM tbl_stored_file WHERE uploader_id=?",UUID.class,teacher.getId());
        jdbc.update("UPDATE tbl_stored_file SET updated_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(300)),fileId);
        maintenance.recoverPending();
        assertThat(jdbc.queryForObject("SELECT state FROM tbl_stored_file WHERE id=?",String.class,fileId)).isEqualTo("READY");
    }

    @Test
    void cleanupKeepsReferencedFilesAndRemovesOnlyExpiredOrphans() {
        var teacher = teacher();
        var classroom = classrooms.create(teacher.getId(),new ClassroomDtos.Create("Files",null,JoinMode.AUTO_JOIN,null,null));
        var bound = files.upload(teacher.getId(),classroom.id(),FilePurpose.LESSON_MATERIAL,pdf());
        var orphan = files.upload(teacher.getId(),classroom.id(),FilePurpose.LESSON_MATERIAL,pdf());
        UUID lesson = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_lesson(id,classroom_id,title) VALUES (?,?,?)",lesson,classroom.id(),"Attached");
        jdbc.update("INSERT INTO tbl_lesson_material(id,lesson_id,file_name,file_url,file_type,file_size,stored_file_id,material_state) VALUES (?,?,?,?,?,?,?,'READY')",
                UUID.randomUUID(),lesson,bound.name(),"/api/v1/files/"+bound.id()+"/content","PDF",bound.size(),bound.id());
        jdbc.update("UPDATE tbl_stored_file SET updated_at=? WHERE uploader_id=?",Timestamp.from(Instant.now().minusSeconds(172800)),teacher.getId());
        maintenance.cleanOrphans();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_stored_file WHERE id=?",Integer.class,bound.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_stored_file WHERE id=?",Integer.class,orphan.id())).isZero();
    }

    @Test
    void cleanupDoesNotStarveOrphansBehindAFullBatchOfReferencedFiles() {
        var teacher = teacher();
        var classroom = classrooms.create(teacher.getId(),new ClassroomDtos.Create("Retention",null,JoinMode.AUTO_JOIN,null,null));
        var orphan = files.upload(teacher.getId(),classroom.id(),FilePurpose.LESSON_MATERIAL,pdf());
        UUID lesson = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_lesson(id,classroom_id,title) VALUES (?,?,?)",lesson,classroom.id(),"Attached");
        for (int index = 0; index < 25; index++) {
            UUID file = copyFile(orphan.id(),true,0,null);
            jdbc.update("INSERT INTO tbl_lesson_material(id,lesson_id,file_name,file_url,file_type,file_size,stored_file_id,material_state) VALUES (?,?,?,?,?,?,?,'READY')",
                    UUID.randomUUID(),lesson,orphan.name(),"/api/v1/files/"+file+"/content","PDF",orphan.size(),file);
        }
        jdbc.update("UPDATE tbl_stored_file SET updated_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(172800)),orphan.id());
        maintenance.cleanOrphans();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_stored_file WHERE id=?",Integer.class,orphan.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_stored_file WHERE classroom_id=?",Integer.class,classroom.id())).isEqualTo(25);
    }

    @Test
    void recoveryDoesNotStarveBehindUnuploadedExhaustedOrCurrentlyLeasedFiles() {
        var teacher = teacher();
        var classroom = classrooms.create(teacher.getId(),new ClassroomDtos.Create("Recovery",null,JoinMode.AUTO_JOIN,null,null));
        doThrow(new StorageException("FILE_SCANNER_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE,"Scanner unavailable"))
                .when(scanner).requireClean(any());
        assertThatThrownBy(() -> files.upload(teacher.getId(),classroom.id(),FilePurpose.LESSON_MATERIAL,pdf())).isInstanceOf(AppException.class);
        UUID recoverable = jdbc.queryForObject("SELECT id FROM tbl_stored_file WHERE classroom_id=?",UUID.class,classroom.id());
        for (int index = 0; index < 25; index++) {
            copyFile(recoverable,index % 3 != 0,index % 3 == 1 ? 3 : 0,
                    index % 3 == 2 ? Instant.now().plusSeconds(600) : null);
        }
        jdbc.update("UPDATE tbl_stored_file SET updated_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(300)),recoverable);
        doNothing().when(scanner).requireClean(any());
        maintenance.recoverPending();
        assertThat(jdbc.queryForObject("SELECT state FROM tbl_stored_file WHERE id=?",String.class,recoverable)).isEqualTo("READY");
    }

    private UUID copyFile(UUID template,boolean uploaded,int attempts,Instant leasedUntil) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_stored_file(id,uploader_id,classroom_id,object_key,file_name,media_type,kind,
                    byte_size,sha256,purpose,state,object_uploaded,scan_attempts,processing_until,updated_at)
                SELECT ?,uploader_id,classroom_id,?,file_name,media_type,kind,byte_size,sha256,purpose,state,?,?,?,?
                FROM tbl_stored_file WHERE id=?
                """,id,UUID.randomUUID()+"/"+UUID.randomUUID(),uploaded,attempts,
                leasedUntil == null ? null : Timestamp.from(leasedUntil),Timestamp.from(Instant.now().minusSeconds(259200)),template);
        return id;
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file","notes.pdf","application/pdf",LearningFileFixtures.pdf());
    }

    private AccountEntity teacher() {
        var teacher = new AccountEntity();teacher.setId(UUID.randomUUID());teacher.setUsername("files_"+UUID.randomUUID().toString().replace("-",""));
        teacher.setEmail(teacher.getUsername()+"@example.test");teacher.setPasswordHash("unused");teacher.setRole(AccountRole.TEACHER);teacher.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(teacher);
    }
}
