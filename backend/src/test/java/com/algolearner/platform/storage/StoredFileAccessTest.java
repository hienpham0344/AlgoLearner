package com.algolearner.platform.storage;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=file-access-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class StoredFileAccessTest extends PostgresIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired ClassroomService classrooms;
    @Autowired FileRecordService records;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void pendingFileCannotBindAndEssayRemainsPrivateToItsAuthorAndOwner() {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var stranger = account(AccountRole.STUDENT);
        var classroom = classrooms.create(teacher.getId(), new ClassroomDtos.Create("Files",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(stranger.getUsername()));
        var file = records.begin(student.getId(),classroom.id(),FilePurpose.ESSAY_SUBMISSION,pdf(),"a".repeat(64));
        assertThatThrownBy(() -> records.requireReady(student.getId(),classroom.id(),file.getId(),FilePurpose.ESSAY_SUBMISSION))
                .isInstanceOf(AppException.class);
        records.uploaded(file.getId());
        records.ready(student.getId(),file.getId());
        assertThat(records.requireReady(student.getId(),classroom.id(),file.getId(),FilePurpose.ESSAY_SUBMISSION).state()).isEqualTo(FileState.READY);
        assertThat(records.downloadable(teacher.getId(),file.getId()).getId()).isEqualTo(file.getId());
        assertThatThrownBy(() -> records.downloadable(stranger.getId(),file.getId())).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> records.requireReady(stranger.getId(),classroom.id(),file.getId(),FilePurpose.ESSAY_SUBMISSION))
                .isInstanceOf(AppException.class);
    }

    @Test
    void studentCannotUploadLessonMaterialAndRevokedMemberCannotFinalize() {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = classrooms.create(teacher.getId(),new ClassroomDtos.Create("Files",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        assertThatThrownBy(() -> records.begin(student.getId(),classroom.id(),FilePurpose.LESSON_MATERIAL,pdf(),"b".repeat(64)))
                .isInstanceOf(AppException.class);
        var file = records.begin(student.getId(),classroom.id(),FilePurpose.ESSAY_SUBMISSION,pdf(),"b".repeat(64));
        records.uploaded(file.getId());
        jdbc.update("UPDATE tbl_classroom_membership SET status='REMOVED' WHERE classroom_id=? AND student_id=?",classroom.id(),student.getId());
        assertThatThrownBy(() -> records.ready(student.getId(),file.getId())).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT state FROM tbl_stored_file WHERE id=?",String.class,file.getId())).isEqualTo("PENDING");
    }

    private ValidatedFile pdf() {
        return new ValidatedFile("notes.pdf","application/pdf",FileKind.PDF,"%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII));
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID()); account.setUsername("file_"+UUID.randomUUID().toString().replace("-",""));
        account.setEmail(account.getUsername()+"@example.test"); account.setPasswordHash("unused");
        account.setRole(role); account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
}
