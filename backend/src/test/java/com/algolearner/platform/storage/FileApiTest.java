package com.algolearner.platform.storage;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=file-api-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class FileApiTest extends PostgresIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired ClassroomService classrooms;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean PrivateObjectStorage objects;
    @MockitoBean MalwareScanner scanner;
    private final HashMap<String,byte[]> blobs = new HashMap<>();

    @BeforeEach
    void storageFixture() throws Exception {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            blobs.put(call.getArgument(0),call.getArgument(2)); return null;
        }).when(objects).upload(anyString(),anyString(),any());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            ((java.io.OutputStream)call.getArgument(1)).write(blobs.get(call.getArgument(0))); return null;
        }).when(objects).download(anyString(),any());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            blobs.remove(call.getArgument(0)); return null;
        }).when(objects).delete(anyString());
    }

    @Test
    void essayUploadAndDownloadKeepBlobPrivateFromClassmates() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var peer = account(AccountRole.STUDENT);
        UUID classroom = classroom(teacher,student,peer);
        byte[] bytes = LearningFileFixtures.pdf();
        var uploaded = http.perform(multipart("/api/v1/classrooms/{id}/files",classroom)
                        .file(new MockMultipartFile("file","essay.pdf","application/pdf",bytes))
                        .param("purpose","ESSAY_SUBMISSION").with(identity(student)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.state").value("READY"))
                .andExpect(jsonPath("$.data.objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.url").doesNotExist()).andReturn();
        UUID file = UUID.fromString(json.readTree(uploaded.getResponse().getContentAsString()).at("/data/id").asText());
        http.perform(get("/api/v1/files/{id}",file).with(identity(peer))).andExpect(status().isForbidden());
        http.perform(get("/api/v1/files/{id}/content",file).with(identity(peer))).andExpect(status().isForbidden());
        var download = http.perform(get("/api/v1/files/{id}/content",file).with(identity(teacher)))
                .andExpect(request().asyncStarted()).andReturn();
        http.perform(asyncDispatch(download)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Security-Policy","sandbox; default-src 'none'"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void scannerOutageKeepsPendingUploadUnavailableForDownload() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        UUID classroom = classroom(teacher,student);
        doThrow(new StorageException("FILE_SCANNER_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE,"Scanner unavailable"))
                .when(scanner).requireClean(any());
        http.perform(multipart("/api/v1/classrooms/{id}/files",classroom)
                        .file(new MockMultipartFile("file","essay.pdf","application/pdf",LearningFileFixtures.pdf()))
                        .param("purpose","ESSAY_SUBMISSION").with(identity(student)))
                .andExpect(status().isServiceUnavailable());
        UUID file = jdbc.queryForObject("SELECT id FROM tbl_stored_file WHERE uploader_id=?",UUID.class,student.getId());
        http.perform(get("/api/v1/files/{id}",file).with(identity(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("PENDING"));
        http.perform(get("/api/v1/files/{id}/content",file).with(identity(student))).andExpect(status().isConflict());
        verify(objects,never()).download(anyString(),any());
    }

    @Test
    void unauthorizedOrMalformedUploadCreatesNoStoredFile() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        UUID classroom = classroom(teacher,student);
        http.perform(multipart("/api/v1/classrooms/{id}/files",classroom)
                        .file(new MockMultipartFile("file","notes.pdf","application/pdf",LearningFileFixtures.pdf()))
                        .param("purpose","LESSON_MATERIAL").with(identity(student)))
                .andExpect(status().isForbidden());
        http.perform(multipart("/api/v1/classrooms/{id}/files",classroom)
                        .file(new MockMultipartFile("file","notes.pdf","application/pdf",new byte[]{1,2,3}))
                        .param("purpose","LESSON_MATERIAL").with(identity(teacher)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_FILE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_stored_file WHERE classroom_id=?",Integer.class,classroom)).isZero();
        verify(objects,never()).upload(anyString(),anyString(),any());
    }

    @Test
    void deleteUnreferencedFileRemovesBlobAndRecordAndGuardsOwnershipAndReferences() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        UUID classroom = classroom(teacher, student);
        byte[] bytes = LearningFileFixtures.pdf();

        var firstUpload = http.perform(multipart("/api/v1/classrooms/{id}/files", classroom)
                        .file(new MockMultipartFile("file", "attached.pdf", "application/pdf", bytes))
                        .param("purpose", "LESSON_MATERIAL").with(identity(teacher)))
                .andExpect(status().isCreated()).andReturn();
        UUID attachedFileId = UUID.fromString(json.readTree(firstUpload.getResponse().getContentAsString()).at("/data/id").asText());

        // Non-uploader gets 403; unknown file gets 404
        http.perform(delete("/api/v1/files/{id}", attachedFileId).with(identity(student)))
                .andExpect(status().isForbidden());
        http.perform(delete("/api/v1/files/{id}", UUID.randomUUID()).with(identity(teacher)))
                .andExpect(status().isNotFound());

        // Bind to tbl_lesson_material -> DELETE returns 409 Conflict and preserves file & blob
        UUID lessonId = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_lesson(id,classroom_id,title) VALUES (?,?,?)", lessonId, classroom, "Materials");
        jdbc.update("""
                INSERT INTO tbl_lesson_material(id,lesson_id,file_name,file_url,file_type,file_size,stored_file_id,material_state)
                VALUES (?,?,'attached.pdf','/api/v1/files/content','PDF',?,?,'READY')
                """, UUID.randomUUID(), lessonId, bytes.length, attachedFileId);
        http.perform(delete("/api/v1/files/{id}", attachedFileId).with(identity(teacher)))
                .andExpect(status().isConflict());
        verify(objects, never()).delete(anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_stored_file WHERE id=?", Integer.class, attachedFileId)).isEqualTo(1);

        // Unreferenced file uploaded by teacher -> DELETE returns 204, deletes blob outside tx, deletes DB row
        var secondUpload = http.perform(multipart("/api/v1/classrooms/{id}/files", classroom)
                        .file(new MockMultipartFile("file", "orphan.pdf", "application/pdf", bytes))
                        .param("purpose", "LESSON_MATERIAL").with(identity(teacher)))
                .andExpect(status().isCreated()).andReturn();
        UUID orphanFileId = UUID.fromString(json.readTree(secondUpload.getResponse().getContentAsString()).at("/data/id").asText());
        String objectKey = jdbc.queryForObject("SELECT object_key FROM tbl_stored_file WHERE id=?", String.class, orphanFileId);

        http.perform(delete("/api/v1/files/{id}", orphanFileId).with(identity(teacher)))
                .andExpect(status().isNoContent());
        verify(objects).delete(objectKey);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_stored_file WHERE id=?", Integer.class, orphanFileId)).isZero();
        http.perform(get("/api/v1/files/{id}", orphanFileId).with(identity(teacher)))
                .andExpect(status().isNotFound());
    }

    private UUID classroom(AccountEntity teacher,AccountEntity... students) {
        var classroom = classrooms.create(teacher.getId(),new ClassroomDtos.Create("Files",null,JoinMode.AUTO_JOIN,null,null));
        for (var student : students) classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        return classroom.id();
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());account.setUsername("fileapi_"+UUID.randomUUID().toString().replace("-",""));
        account.setEmail(account.getUsername()+"@example.test");account.setPasswordHash("unused");
        account.setRole(role);account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor identity(AccountEntity account) {
        return jwt().jwt(token -> token.subject(account.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_"+account.getRole().name()));
    }
}
