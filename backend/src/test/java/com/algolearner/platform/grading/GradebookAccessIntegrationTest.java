package com.algolearner.platform.grading;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=gradebook-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class GradebookAccessIntegrationTest extends PostgresIntegrationTest {
    @Autowired GradebookAccess access;
    @Autowired ClassroomService classrooms;
    @Autowired AccountRepository accounts;
    @Autowired GradebookRepository gradebooks;
    @Autowired GradeSchemeRevisionRepository revisions;
    @Autowired GradeSchemeItemRepository items;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MockMvc mvc;

    @Test
    void createsGradebookWithDefaultsAndNoRevision() {
        UUID classroomId = classroom(account(AccountRole.TEACHER));
        var gradebook = gradebook(classroomId);
        assertThat(gradebook.isShowOverall()).isFalse();
        assertThat(gradebook.getActiveSchemeRevisionId()).isNull();
        assertThat(gradebook.getVersion()).isZero();
        assertThat(revisions.existsForClassroom(classroomId)).isFalse();
    }

    @Test
    void rollsBackClassroomAndGradebookAfterFlushingBoth() {
        var teacher = account(AccountRole.TEACHER);
        var createdId = new AtomicReference<UUID>();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            createdId.set(classroom(teacher));
            gradebooks.flush();
            assertThat(gradebook(createdId.get()).getId()).isNotNull();
            throw new IllegalStateException("Abort classroom creation");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Abort classroom creation");
        assertThat(createdId.get()).isNotNull();
        assertThat(gradebooks.findByClassroomId(createdId.get())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM tbl_classroom WHERE id=?)",
                Boolean.class, createdId.get())).isFalse();
    }

    @Test
    void hardDeleteWithUnappliedRevisionReturnsConflict() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        UUID classroomId = classroom(teacher);
        var gradebook = gradebook(classroomId);
        saveRevision(teacher, gradebook, GradeSchemeRevisionEntity.Mode.RAW);

        mvc.perform(delete("/api/v1/classrooms/{id}", classroomId).with(authentication(principal(teacher))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLASS_HAS_RETAINED_DATA"))
                .andExpect(jsonPath("$.message").value("Archive this classroom to preserve its gradebook revisions"));
        assertThat(gradebook(classroomId).getActiveSchemeRevisionId()).isNull();
        assertThat(revisions.existsForClassroom(classroomId)).isTrue();
    }

    @Test
    void rejectsForeignClassItemAndStudentAndRevokesOldOwnerAfterTransfer() {
        var teacher = account(AccountRole.TEACHER);
        var otherTeacher = account(AccountRole.TEACHER);
        var admin = account(AccountRole.ADMIN);
        var student = account(AccountRole.STUDENT);
        var otherStudent = account(AccountRole.STUDENT);
        UUID first = classroom(teacher);
        UUID second = classroom(otherTeacher);
        classrooms.directAdd(teacher.getId(), first, new ClassroomDtos.DirectAdd(student.getUsername()));
        classrooms.directAdd(otherTeacher.getId(), second, new ClassroomDtos.DirectAdd(otherStudent.getUsername()));
        UUID assignment = UUID.randomUUID();
        UUID quiz = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title) VALUES (?,?,'Assignment')", assignment, first);
        jdbc.update("""
                INSERT INTO tbl_quiz(id,classroom_id,title,graded,max_attempts,questions_json)
                VALUES (?,?,'Quiz',true,1,'[]'::jsonb)
                """, quiz, second);

        var teacherPrincipal = principal(teacher);
        var studentPrincipal = principal(student);
        assertThat(access.requireTeacherRead(teacherPrincipal, first)).isEqualTo(teacher.getId());
        assertThatThrownBy(() -> access.requireTeacherRead(teacherPrincipal, second)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> access.requireTeacherWrite(teacherPrincipal, first))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(access.requireStudentRead(studentPrincipal, first, student.getId())).isEqualTo(student.getId());
        assertThatThrownBy(() -> access.requireStudentRead(studentPrincipal, second, student.getId()))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> access.requireStudentRead(studentPrincipal, first, otherStudent.getId()))
                .isInstanceOf(AppException.class);

        transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(teacherPrincipal, first);
            access.requireActiveStudentInClass(first, student.getId());
            access.requireItemInClass(first, SourceType.ASSIGNMENT, assignment);
        });
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(teacherPrincipal, first);
            access.requireActiveStudentInClass(first, otherStudent.getId());
        })).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(teacherPrincipal, first);
            access.requireItemInClass(first, SourceType.QUIZ, quiz);
        })).isInstanceOf(AppException.class);
        transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(principal(otherTeacher), second);
            access.requireItemInClass(second, SourceType.QUIZ, quiz);
        });

        classrooms.leave(student.getId(), first);
        assertThatThrownBy(() -> access.requireStudentRead(studentPrincipal, first, student.getId()))
                .isInstanceOf(AppException.class);
        classrooms.transferOwner(admin.getId(), first, otherTeacher.getId());
        assertThatThrownBy(() -> access.requireTeacherRead(teacherPrincipal, first)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> access.requireTeacherWrite(teacherPrincipal, first)))
                .isInstanceOf(AppException.class);
        transactions.executeWithoutResult(status -> access.requireTeacherWrite(principal(otherTeacher), first));
        classrooms.archive(otherTeacher.getId(), first);
        assertThat(access.requireTeacherRead(principal(otherTeacher), first)).isEqualTo(otherTeacher.getId());
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> access.requireTeacherWrite(principal(otherTeacher), first)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void persistsScopedSchemeAndRejectsDuplicateItemAndForeignActiveRevision() {
        var teacher = account(AccountRole.TEACHER);
        var gradebook = gradebook(classroom(teacher));
        var otherGradebook = gradebook(classroom(teacher));
        UUID sourceId = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title) VALUES (?,?,'Assignment')",
                sourceId, gradebook.getClassroomId());

        var revision = saveRevision(teacher, gradebook, GradeSchemeRevisionEntity.Mode.WEIGHTED);
        items.saveAndFlush(item(revision.getId(), sourceId));
        gradebook.setActiveSchemeRevisionId(revision.getId());
        gradebooks.saveAndFlush(gradebook);

        assertThat(gradebooks.findByClassroomId(gradebook.getClassroomId()).orElseThrow().getActiveSchemeRevisionId())
                .isEqualTo(revision.getId());
        assertThat(revisions.findByIdAndGradebookId(revision.getId(), otherGradebook.getId())).isEmpty();
        assertThat(items.findAllByRevisionId(revision.getId())).singleElement()
                .satisfies(item -> assertThat(item.getWeight()).isEqualByComparingTo("100.0000"));
        assertThatThrownBy(() -> items.saveAndFlush(item(revision.getId(), sourceId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tbl_grade_scheme_revision SET mode='RAW' WHERE id=?", revision.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tbl_grade_scheme_item WHERE revision_id=?", revision.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        otherGradebook.setActiveSchemeRevisionId(revision.getId());
        assertThatThrownBy(() -> gradebooks.saveAndFlush(otherGradebook))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emptyGradebookDoesNotPreventExistingClassroomDeletion() {
        var teacher = account(AccountRole.TEACHER);
        UUID classroomId = classroom(teacher);
        assertThat(gradebook(classroomId).getId()).isNotNull();
        classrooms.delete(teacher.getId(), classroomId);
        assertThat(gradebooks.findByClassroomId(classroomId)).isEmpty();
    }

    private GradebookEntity gradebook(UUID classroomId) {
        return gradebooks.findByClassroomId(classroomId).orElseThrow();
    }

    private GradeSchemeRevisionEntity saveRevision(AccountEntity teacher, GradebookEntity gradebook,
                                                    GradeSchemeRevisionEntity.Mode mode) {
        var revision = new GradeSchemeRevisionEntity();
        revision.setId(UUID.randomUUID());
        revision.setGradebookId(gradebook.getId());
        revision.setRevisionNo(1);
        revision.setMode(mode);
        revision.setCreatedBy(teacher.getId());
        return revisions.saveAndFlush(revision);
    }

    private static GradeSchemeItemEntity item(UUID revisionId, UUID sourceId) {
        var item = new GradeSchemeItemEntity();
        item.setId(UUID.randomUUID());
        item.setRevisionId(revisionId);
        item.setSourceType(SourceType.ASSIGNMENT);
        item.setSourceId(sourceId);
        item.setWeight(new BigDecimal("100.0000"));
        return item;
    }

    private UUID classroom(AccountEntity teacher) {
        return classrooms.create(teacher.getId(), new ClassroomDtos.Create("Gradebook", null, JoinMode.AUTO_JOIN, null, null)).id();
    }

    private static UsernamePasswordAuthenticationToken principal(AccountEntity account) {
        return new UsernamePasswordAuthenticationToken(account.getId().toString(), "",
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("grd_" + account.getId().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setPasswordHash("unused");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
}
