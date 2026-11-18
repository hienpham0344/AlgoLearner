package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=lesson-safety-test-only-secret-at-least-32-bytes", "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class LessonSafetyTest extends PostgresIntegrationTest {
    static final java.util.concurrent.atomic.AtomicReference<LessonProgressSource> SOURCE = new java.util.concurrent.atomic.AtomicReference<>();
    @org.springframework.boot.test.context.TestConfiguration
    static class Sources {
        @org.springframework.context.annotation.Bean
        @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
        LessonProgressProvider testQuizSource() { return new LessonProgressProvider() {
            public LinkedItemType itemType() { return LinkedItemType.QUIZ; }
            public boolean existsInClassroom(UUID itemId, UUID classroomId) { return true; }
            public LessonProgressSource progress(UUID itemId, UUID classroomId, UUID studentId) { return SOURCE.get(); }
        }; }
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.algolearner.platform.storage.StoredFileRepository storedFiles;
    @Autowired LessonService service;
    @Autowired ClassroomService classroomService;
    @Autowired AccountRepository accounts;

    @Test
    void restoreDraftNeverPublishesUnreviewedLesson() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Safety", null, JoinMode.AUTO_JOIN, null, null));
        var lesson = service.create(teacher.getId(), classroom.id(), new LessonDtos.CreateLessonRequest("Draft", null, null, null, CompletionMode.MANUAL));
        service.archive(teacher.getId(), classroom.id(), lesson.id());
        service.archive(teacher.getId(), classroom.id(), lesson.id());
        assertThat(service.restore(teacher.getId(), classroom.id(), lesson.id()).status()).isEqualTo(LessonStatus.DRAFT);
    }

    @Test
    void archivedLessonCannotPublishWithoutRestore() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Safety", null, JoinMode.AUTO_JOIN, null, null));
        var lesson = service.create(teacher.getId(), classroom.id(), new LessonDtos.CreateLessonRequest("Draft", null, null, null, CompletionMode.MANUAL));
        service.archive(teacher.getId(), classroom.id(), lesson.id());
        assertThatThrownBy(() -> service.publish(teacher.getId(), classroom.id(), lesson.id())).isInstanceOf(AppException.class);
        assertThat(service.get(teacher.getId(), classroom.id(), lesson.id()).status()).isEqualTo(LessonStatus.ARCHIVED);
    }

    @Test
    void autoRuleRejectsUnlinkedItemAndEmptyConditions() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Rules", null, JoinMode.AUTO_JOIN, null, null));
        var lesson = service.create(teacher.getId(), classroom.id(), new LessonDtos.CreateLessonRequest("Rules", null, null, null, CompletionMode.AUTO_ITEMS));
        assertThatThrownBy(() -> service.configureCompletionRule(teacher.getId(), classroom.id(), lesson.id(),
                new LessonCompletionRule(LessonCompletionRule.Operator.ALL, java.util.List.of()))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.configureCompletionRule(teacher.getId(), classroom.id(), lesson.id(),
                new LessonCompletionRule(LessonCompletionRule.Operator.ALL, java.util.List.of(new LessonCompletionRule.Condition(
                        LinkedItemType.ASSIGNMENT, UUID.randomUUID(), LessonCompletionRule.Requirement.SUBMITTED, null)))))
                .isInstanceOf(AppException.class);
    }

    @Test
    void publishedGradeAndRejudgeRecomputeCompletionWithoutLosingHistory() {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Progress", null, JoinMode.AUTO_JOIN, null, null));
        classroomService.directAdd(teacher.getId(), classroom.id(), new ClassroomDtos.DirectAdd(student.getUsername()));
        var lesson = service.create(teacher.getId(), classroom.id(), new LessonDtos.CreateLessonRequest("Rules", null, null, null, CompletionMode.AUTO_ITEMS));
        UUID quiz = UUID.randomUUID();
        service.linkItem(teacher.getId(), classroom.id(), lesson.id(), new LessonDtos.LinkItemRequest(LinkedItemType.QUIZ,quiz));
        SOURCE.set(new LessonProgressSource(true,false,java.math.BigDecimal.valueOf(90),UUID.randomUUID(),"unpublished1"));
        service.configureCompletionRule(teacher.getId(), classroom.id(), lesson.id(), new LessonCompletionRule(LessonCompletionRule.Operator.ALL,
                java.util.List.of(new LessonCompletionRule.Condition(LinkedItemType.QUIZ,quiz,LessonCompletionRule.Requirement.SCORE_AT_LEAST,java.math.BigDecimal.valueOf(80)))));
        assertThat(service.get(teacher.getId(),classroom.id(),lesson.id()).isCompleted()).isNull();
        assertThat(completed(lesson.id(),student.getId())).isFalse();
        SOURCE.set(new LessonProgressSource(true,true,java.math.BigDecimal.valueOf(90),UUID.randomUUID(),"published1"));
        service.recomputeForItem(classroom.id(),LinkedItemType.QUIZ,quiz,student.getId());
        assertThat(completed(lesson.id(),student.getId())).isTrue();
        service.recomputeForItem(classroom.id(),LinkedItemType.QUIZ,quiz,student.getId());
        assertThat(history(lesson.id())).isEqualTo(2);
        SOURCE.set(new LessonProgressSource(true,true,java.math.BigDecimal.valueOf(50),UUID.randomUUID(),"published2"));
        service.recomputeForItem(classroom.id(),LinkedItemType.QUIZ,quiz,student.getId());
        assertThat(completed(lesson.id(),student.getId())).isFalse();
        assertThat(history(lesson.id())).isEqualTo(3);
    }
    @Test
    void anyRuleDiffersFromAllAndRuleRevisionsPreserveHistory() {
        var teacher = account(AccountRole.TEACHER); var student = account(AccountRole.STUDENT);
        var classroom = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Operators",null,JoinMode.AUTO_JOIN,null,null));
        classroomService.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        var lesson = service.create(teacher.getId(),classroom.id(),new LessonDtos.CreateLessonRequest("Operators",null,null,null,CompletionMode.AUTO_ITEMS));
        UUID quiz = UUID.randomUUID();
        service.linkItem(teacher.getId(),classroom.id(),lesson.id(),new LessonDtos.LinkItemRequest(LinkedItemType.QUIZ,quiz));
        SOURCE.set(new LessonProgressSource(true,false,java.math.BigDecimal.valueOf(99),null,"official1"));
        var conditions = java.util.List.of(
                new LessonCompletionRule.Condition(LinkedItemType.QUIZ,quiz,LessonCompletionRule.Requirement.SUBMITTED,null),
                new LessonCompletionRule.Condition(LinkedItemType.QUIZ,quiz,LessonCompletionRule.Requirement.SCORE_AT_LEAST,java.math.BigDecimal.valueOf(80)));
        service.configureCompletionRule(teacher.getId(),classroom.id(),lesson.id(),new LessonCompletionRule(LessonCompletionRule.Operator.ALL,conditions));
        assertThat(completed(lesson.id(),student.getId())).isFalse();
        var any = new LessonCompletionRule(LessonCompletionRule.Operator.ANY,conditions);
        service.configureCompletionRule(teacher.getId(),classroom.id(),lesson.id(),any);
        assertThat(completed(lesson.id(),student.getId())).isTrue();
        service.configureCompletionRule(teacher.getId(),classroom.id(),lesson.id(),any);
        assertThat(service.completionRule(teacher.getId(),classroom.id(),lesson.id()).version()).isEqualTo(2);
        assertThat(history(lesson.id())).isEqualTo(2);
        assertThatThrownBy(() -> service.unlinkItem(teacher.getId(),classroom.id(),lesson.id(),quiz)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.studentProgress(student.getId(),classroom.id(),lesson.id(),org.springframework.data.domain.PageRequest.of(0,10)))
                .isInstanceOf(AppException.class);
        assertThat(service.studentProgress(teacher.getId(),classroom.id(),lesson.id(),org.springframework.data.domain.PageRequest.of(0,10)).getData().getItems()).hasSize(1);
    }

    @Test
    void managedAttachmentUsesVerifiedMetadataAndRejectsWrongClass() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Materials",null,JoinMode.AUTO_JOIN,null,null));
        var other = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Other",null,JoinMode.AUTO_JOIN,null,null));
        var lesson = service.create(teacher.getId(),classroom.id(),new LessonDtos.CreateLessonRequest("Files",null,null,null,CompletionMode.MANUAL));
        var file = new com.algolearner.platform.storage.StoredFileEntity();
        file.setId(UUID.randomUUID()); file.setUploaderId(teacher.getId()); file.setClassroomId(classroom.id());
        file.setObjectKey(UUID.randomUUID().toString()); file.setName("verified.pdf"); file.setMediaType("application/pdf");
        file.setKind(com.algolearner.platform.storage.FileKind.PDF); file.setSize(100); file.setSha256("a".repeat(64));
        file.setPurpose(com.algolearner.platform.storage.FilePurpose.LESSON_MATERIAL);
        file.setState(com.algolearner.platform.storage.FileState.READY); file.setObjectUploaded(true);
        storedFiles.saveAndFlush(file);
        var material = service.addMaterial(teacher.getId(),classroom.id(),lesson.id(),
                new LessonDtos.AddMaterialRequest(file.getId(),"untrusted.zip","https://untrusted.test",MaterialFileType.ZIP,999));
        assertThat(material.fileUrl()).isEqualTo("/api/v1/files/"+file.getId()+"/content");
        assertThat(material.fileName()).isEqualTo("verified.pdf"); assertThat(material.fileSize()).isEqualTo(100);
        assertThat(material.fileUrl()).doesNotContain("untrusted");
        var otherLesson = service.create(teacher.getId(),other.id(),new LessonDtos.CreateLessonRequest("Other",null,null,null,CompletionMode.MANUAL));
        assertThatThrownBy(() -> service.addMaterial(teacher.getId(),other.id(),otherLesson.id(),
                new LessonDtos.AddMaterialRequest(file.getId(),null,null,null,0))).isInstanceOf(AppException.class);
    }

    @Test
    void switchingFromAutoItemsToManualAllowsUnlinkingWhilePreservingRuleHistory() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroomService.create(teacher.getId(), new ClassroomDtos.Create("Switch mode", null, JoinMode.AUTO_JOIN, null, null));
        var lesson = service.create(teacher.getId(), classroom.id(), new LessonDtos.CreateLessonRequest("Switch", null, null, null, CompletionMode.AUTO_ITEMS));
        UUID quiz = UUID.randomUUID();
        service.linkItem(teacher.getId(), classroom.id(), lesson.id(), new LessonDtos.LinkItemRequest(LinkedItemType.QUIZ, quiz));
        SOURCE.set(new LessonProgressSource(false, false, null, null, "empty"));
        service.configureCompletionRule(teacher.getId(), classroom.id(), lesson.id(),
                new LessonCompletionRule(LessonCompletionRule.Operator.ALL,
                        java.util.List.of(new LessonCompletionRule.Condition(
                                LinkedItemType.QUIZ, quiz, LessonCompletionRule.Requirement.SUBMITTED, null))));

        assertThatThrownBy(() -> service.unlinkItem(teacher.getId(), classroom.id(), lesson.id(), quiz))
                .isInstanceOf(AppException.class);

        var updated = service.update(teacher.getId(), classroom.id(), lesson.id(),
                new LessonDtos.UpdateLessonRequest("Switch", null, null, null, CompletionMode.MANUAL));
        assertThat(updated.completionMode()).isEqualTo(CompletionMode.MANUAL);

        service.unlinkItem(teacher.getId(), classroom.id(), lesson.id(), quiz);

        var afterUnlink = service.get(teacher.getId(), classroom.id(), lesson.id());
        assertThat(afterUnlink.linkedItems()).isEmpty();
        assertThat(afterUnlink.canDelete()).isFalse();
        assertThatThrownBy(() -> service.delete(teacher.getId(), classroom.id(), lesson.id()))
                .isInstanceOf(AppException.class);
    }

    private boolean completed(UUID lesson,UUID student) { return jdbc.queryForObject("select is_completed from tbl_lesson_completion where lesson_id=? and student_id=?", Boolean.class,lesson,student); }
    private int history(UUID lesson) { return jdbc.queryForObject("select count(*) from tbl_lesson_completion_history where lesson_id=?",Integer.class,lesson); }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity(); account.setId(UUID.randomUUID());
        account.setUsername("lesson_" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test"); account.setPasswordHash("unused");
        account.setRole(role); account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
}
