package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.judge.JudgeGateway;
import com.algolearner.platform.problem.PublicProblemCatalog;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"logging.level.root=WARN","debug=false",
        "app.security.jwt-secret=assignment-admin-tests-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
@Transactional
class AssignmentAdministrationApiTest extends PostgresIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accounts;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AssignmentQuestionRepository questions;
    @Autowired private AssignmentAttemptRepository attempts;
    @Autowired private AssignmentService service;

    @Test
    void activeAdminUsesSeparateGradingContextAndRetainsActualActor() throws Exception {
        var seed=seed();var admin=account(AccountRole.ADMIN);
        mvc.perform(get(adminPath(seed)+"/submissions/{attempt}/evaluations",seed.attempt().getId()).with(actor(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].finalScore").value(7));
        mvc.perform(post(adminPath(seed)+"/rejudge").with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Verify official grading\"}")).andExpect(status().isNoContent());
        mvc.perform(patch(adminPath(seed)+"/questions/{question}/code-case-weights",seed.code().getId()).with(actor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"weights\":[1,3],\"reason\":\"Review test importance\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.weights[1]").value(3));
        assertThat(jdbc.queryForObject("SELECT q->'caseWeights' FROM tbl_assignment_revision r CROSS JOIN LATERAL jsonb_array_elements(r.content->'questions') q WHERE r.id=? AND q->>'id'=?",
                String.class,seed.assignment().getPublishedRevisionId(),seed.code().getId().toString())).isEqualTo("[]");
        assertThat(jdbc.queryForObject("SELECT actor_role FROM tbl_audit_log WHERE actor_id=? AND action='EVALUATION_REJUDGE'",String.class,admin.getId()))
                .isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT actor_role FROM tbl_audit_log WHERE actor_id=? AND action='UPDATE_CODE_CASE_WEIGHTS'",String.class,admin.getId()))
                .isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT performed_by FROM tbl_assignment_rejudge_log WHERE attempt_id=?",UUID.class,seed.attempt().getId()))
                .isEqualTo(admin.getId());
        mvc.perform(post(teacherPath(seed)+"/rejudge").with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Wrong context\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/classrooms/{classId}/assignments",seed.classroom().getId()).with(actor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Not an author\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(teacherPath(seed)+"/submissions/{attempt}/evaluations",seed.attempt().getId()).with(actor(seed.teacher())))
                .andExpect(status().isOk());
        mvc.perform(patch(teacherPath(seed)+"/questions/{question}/code-case-weights",seed.code().getId()).with(actor(seed.teacher()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"weights\":[2,2],\"reason\":\"Owner adjustment\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void gradingRoutesRejectOtherRolesDisabledAdminsAndInvalidScope() throws Exception {
        var seed=seed();var admin=account(AccountRole.ADMIN);var otherTeacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        for(var denied:List.of(otherTeacher,student,seed.teacher())) {
            mvc.perform(post(adminPath(seed)+"/rejudge").with(actor(denied)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Denied\"}")).andExpect(status().isForbidden());
            mvc.perform(get(adminPath(seed)+"/submissions/{attempt}/evaluations",seed.attempt().getId()).with(actor(denied)))
                    .andExpect(status().isForbidden());
            mvc.perform(patch(adminPath(seed)+"/questions/{question}/code-case-weights",seed.code().getId()).with(actor(denied))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"weights\":[1,1],\"reason\":\"Denied\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(teacherPath(seed)+"/submissions/{attempt}/evaluations",seed.attempt().getId()).with(actor(otherTeacher)))
                .andExpect(status().isForbidden());
        mvc.perform(post(adminPath(seed)+"/rejudge").with(authentication(new UsernamePasswordAuthenticationToken(
                otherTeacher.getId().toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Forged role\"}"))
                .andExpect(status().isForbidden());
        admin.setStatus(AccountStatus.SUSPENDED);accounts.saveAndFlush(admin);
        mvc.perform(post(adminPath(seed)+"/rejudge").with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Disabled\"}")).andExpect(status().isForbidden());
        mvc.perform(get(adminPath(seed)+"/submissions/{attempt}/evaluations",seed.attempt().getId()).with(actor(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(patch(adminPath(seed)+"/questions/{question}/code-case-weights",seed.code().getId()).with(actor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"weights\":[1,1],\"reason\":\"Disabled\"}"))
                .andExpect(status().isForbidden());
        admin.setStatus(AccountStatus.ACTIVE);accounts.saveAndFlush(admin);
        mvc.perform(get(adminPath(seed)+"/submissions/{attempt}/evaluations",UUID.randomUUID()).with(actor(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(post(adminPath(seed)+"/rejudge").with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(patch(adminPath(seed)+"/questions/{question}/code-case-weights",seed.code().getId()).with(actor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"weights\":[1],\"reason\":\"Bad count\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archivedClassIsReadOnlyForAdminAndOwner() throws Exception {
        var seed=seed();var admin=account(AccountRole.ADMIN);
        seed.classroom().setStatus(ClassroomStatus.ARCHIVED);classrooms.saveAndFlush(seed.classroom());
        mvc.perform(get(adminPath(seed)+"/submissions/{attempt}/evaluations",seed.attempt().getId()).with(actor(admin)))
                .andExpect(status().isOk());
        mvc.perform(get(teacherPath(seed)+"/submissions/{attempt}/evaluations",seed.attempt().getId()).with(actor(seed.teacher())))
                .andExpect(status().isOk());
        mvc.perform(post(adminPath(seed)+"/rejudge").with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Archived\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(adminPath(seed)+"/questions/{question}/code-case-weights",seed.code().getId()).with(actor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"weights\":[1,1],\"reason\":\"Archived\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(teacherPath(seed)+"/rejudge").with(actor(seed.teacher())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Archived\"}")).andExpect(status().isForbidden());
        seed.classroom().setStatus(ClassroomStatus.ACTIVE);classrooms.saveAndFlush(seed.classroom());
        seed.teacher().setStatus(AccountStatus.SUSPENDED);accounts.saveAndFlush(seed.teacher());
        mvc.perform(post(adminPath(seed)+"/rejudge").with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Owner suspended\"}")).andExpect(status().isForbidden());
    }

    private record Seed(AccountEntity teacher,ClassroomEntity classroom,AssignmentEntity assignment,AssignmentQuestionEntity code,AssignmentAttemptEntity attempt) { }
    private Seed seed() throws Exception {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        var classroom=new ClassroomEntity();classroom.setId(UUID.randomUUID());classroom.setName("Admin grading");classroom.setOwner(teacher);
        classroom.setClassCode(UUID.randomUUID().toString().substring(0,20));classroom.setStatus(ClassroomStatus.ACTIVE);classroom.setJoinMode(JoinMode.AUTO_JOIN);
        classroom=classrooms.saveAndFlush(classroom);
        var assignment=assignments.saveAndFlush(AssignmentEntity.builder().id(UUID.randomUUID()).classroom(classroom).title("Assessment")
                .status(AssignmentStatus.PUBLISHED).build());
        var content=new PublicProblemCatalog.ContentSnapshot(UUID.randomUUID(),"Code","Solve",Set.of(JudgeGateway.Language.JAVA),
                List.of(new JudgeGateway.TestCaseSnapshot("sample","out",true,true),new JudgeGateway.TestCaseSnapshot("hidden","out",false,false)),
                new JudgeGateway.Limits(15,2000,65536),JudgeGateway.Feedback.PASS_FAIL);
        var code=questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.CODE)
                .title("Code").prompt("Solve").points(BigDecimal.TEN).codeLanguage("JAVA").codeContentSnapshot(json.writeValueAsString(content)).build());
        var essay=questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.ESSAY)
                .title("Essay").prompt("Explain").points(BigDecimal.TEN).essayFormat(EssayFormat.TEXT_ONLY).build());
        var frozen=new AssignmentSnapshotPolicy.Question(essay.getId(),QuestionType.ESSAY,"Essay","Explain",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,EssayFormat.TEXT_ONLY,null,null,null,0,List.of(),null);
        var snapshot=AssignmentSnapshotPolicy.encode(new AssignmentSnapshotPolicy.Content(null,TimeExpiredAction.AUTO_SUBMIT,false,false,List.of(frozen)));
        var attempt=attempts.saveAndFlush(AssignmentAttemptEntity.builder().id(UUID.randomUUID()).assignment(assignment).student(student).attemptNumber(1)
                .status(AttemptStatus.SUBMITTED).startedAt(Instant.now().minusSeconds(120)).submittedAt(Instant.now().minusSeconds(60))
                .revisionSnapshot(snapshot).snapshotProvenance("TRUSTED").build());
        service.gradeEssay(teacher.getId(),classroom.getId(),assignment.getId(),attempt.getId(),new AssignmentDtos.GradeEssayRequest(essay.getId(),new BigDecimal("7"),"Marked"));
        service.publish(teacher.getId(),classroom.getId(),assignment.getId());
        return new Seed(teacher,classroom,assignment,code,attempt);
    }
    private AccountEntity account(AccountRole role) {
        var account=new AccountEntity();account.setId(UUID.randomUUID());account.setUsername("admin-test-"+UUID.randomUUID());
        account.setEmail(account.getUsername()+"@example.test");account.setRole(role);account.setStatus(AccountStatus.ACTIVE);account.setPasswordHash("unused-fixture");
        return accounts.saveAndFlush(account);
    }
    private String adminPath(Seed seed) {return "/api/v1/admin/classrooms/"+seed.classroom().getId()+"/assignments/"+seed.assignment().getId();}
    private String teacherPath(Seed seed) {return "/api/v1/classrooms/"+seed.classroom().getId()+"/assignments/"+seed.assignment().getId();}
    private RequestPostProcessor actor(AccountEntity account) {
        return authentication(new UsernamePasswordAuthenticationToken(account.getId().toString(),null,List.of(new SimpleGrantedAuthority("ROLE_"+account.getRole()))));
    }
}
