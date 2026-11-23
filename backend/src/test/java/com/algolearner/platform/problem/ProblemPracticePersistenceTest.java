package com.algolearner.platform.problem;

import static org.assertj.core.api.Assertions.*;
import static com.algolearner.platform.judge.JudgeGateway.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.judge.JdbcJudgeGateway;
import com.algolearner.platform.practice.ClassPracticeService;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class ProblemPracticePersistenceTest {
    @Test void contributionsRolesSnapshotsAndCanonicalJobsWorkWithoutExecutingLearnerSource() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("TEST_DB_URL"),System.getenv("TEST_DB_USERNAME"),System.getenv("TEST_DB_PASSWORD"));
        var jdbc=new NamedParameterJdbcTemplate(ds);String schema="problem_test_"+UUID.randomUUID().toString().replace("-","");
        jdbc.getJdbcTemplate().execute("CREATE SCHEMA "+schema);
        try {
            var properties=new Properties();properties.setProperty("currentSchema",schema);ds.setConnectionProperties(properties);
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_account(id UUID PRIMARY KEY,role VARCHAR(20),status VARCHAR(20))");
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_classroom(id UUID PRIMARY KEY,owner_id UUID,status VARCHAR(20))");
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_classroom_membership(classroom_id UUID,student_id UUID,status VARCHAR(20))");
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_assignment_code_job(job_id UUID)");
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_lesson(id UUID PRIMARY KEY,classroom_id UUID,status TEXT)");
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_lesson_linked_item(lesson_id UUID,item_type TEXT,item_id UUID)");
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_audit_log(id BIGSERIAL PRIMARY KEY,actor_id UUID,actor_role TEXT,target_type TEXT,target_id TEXT,action TEXT,before_state TEXT,after_state TEXT,reason TEXT,correlation_id TEXT,created_at TIMESTAMPTZ,updated_at TIMESTAMPTZ)");
            migration(jdbc,"V202611180008__persistent_isolated_judge.sql");migration(jdbc,"V202611180010__public_problem_and_class_practice.sql");
            UUID teacher=account(jdbc,"TEACHER"),otherTeacher=account(jdbc,"TEACHER"),student=account(jdbc,"STUDENT"),otherStudent=account(jdbc,"STUDENT"),admin=account(jdbc,"ADMIN"),classroom=UUID.randomUUID();
            jdbc.update("INSERT INTO tbl_classroom VALUES(:id,:owner,'ACTIVE')",Map.of("id",classroom,"owner",teacher));
            jdbc.update("INSERT INTO tbl_classroom_membership VALUES(:class,:student,'ACTIVE')",Map.of("class",classroom,"student",student));
            var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));var json=new ObjectMapper();
            var judge=new JdbcJudgeGateway(jdbc,json,tx);var clock=Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"),ZoneOffset.UTC);
            var audit=new AuditService(jdbc.getJdbcTemplate(),clock);var problem=new ProblemService(jdbc,json,judge,audit,clock);
            var practice=new ClassPracticeService(jdbc,json,judge,problem,problem,audit);
            var content=new ProblemService.Content("Echo","Read and echo",Set.of(Language.PYTHON),
                    List.of(new TestCaseSnapshot("sample","sample",true,false),new TestCaseSnapshot("hidden secret","hidden answer",false,false)),new Limits(10,2000,65536),Feedback.TESTCASE_STATUS);
            assertThatThrownBy(()->tx.execute(s->problem.create(student,content))).isInstanceOf(RuntimeException.class);
            UUID id=tx.execute(s->problem.create(teacher,content));
            assertThatThrownBy(()->problem.detail(student,id)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->tx.executeWithoutResult(s->problem.edit(otherTeacher,id,content))).isInstanceOf(RuntimeException.class);
            tx.executeWithoutResult(s->problem.submitReview(teacher,id));
            assertThatThrownBy(()->tx.executeWithoutResult(s->problem.review(teacher,id,true,null))).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->tx.executeWithoutResult(s->problem.review(admin,id,false," "))).isInstanceOf(RuntimeException.class);
            tx.executeWithoutResult(s->problem.review(admin,id,false,"Add explanation"));
            tx.executeWithoutResult(s->problem.edit(teacher,id,content));tx.executeWithoutResult(s->problem.submitReview(teacher,id));
            assertThat(problem.contributions(admin,true,PageRequest.of(0,20)).getData().getItems()).hasSize(1);
            assertThat(problem.contribution(admin,id,true).content().testCases()).hasSize(2);
            assertThatThrownBy(()->problem.contribution(student,id,false)).isInstanceOf(RuntimeException.class);
            tx.executeWithoutResult(s->problem.review(admin,id,true,null));
            assertThat(problem.browse(student,PageRequest.of(0,20)).getData().getItems()).hasSize(1);
            assertThat(problem.detail(student,id).samples()).hasSize(1);
            assertThat(problem.contentSnapshot(teacher,id).testCases()).hasSize(2);
            assertThatThrownBy(()->problem.contentSnapshot(student,id)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->tx.executeWithoutResult(s->problem.edit(teacher,id,content))).isInstanceOf(RuntimeException.class);
            UUID copied=tx.execute(s->practice.copy(teacher,classroom,id));
            assertThatThrownBy(()->tx.execute(s->practice.copy(otherTeacher,classroom,id))).isInstanceOf(RuntimeException.class);
            assertThat(practice.availableToStudent(copied,classroom,student)).isFalse();
            tx.executeWithoutResult(s->practice.publish(teacher,classroom,copied));
            assertThat(practice.availableToStudent(copied,classroom,student)).isTrue();
            assertThat(practice.availableToStudent(copied,classroom,otherStudent)).isFalse();
            assertThatThrownBy(()->practice.detail(otherStudent,classroom,copied)).isInstanceOf(RuntimeException.class);
            var input=new ProblemService.CodeInput("print(input())",Language.PYTHON,"request");
            UUID classJob=tx.execute(s->practice.evaluate(student,classroom,copied,input,false));
            assertThat(practice.progress(copied,classroom,student).submitted()).isFalse();
            assertThat(judge.result(student,classJob).verdict()).isEqualTo(Verdict.QUEUED);
            // Seed a trusted worker's persisted completion; no Docker execution is claimed by this integration test.
            jdbc.update("UPDATE tbl_judge_job SET status='ACCEPTED' WHERE id=:job",Map.of("job",classJob));
            assertThat(practice.progress(copied,classroom,student).submitted()).isTrue();
            assertThat(practice.progress(copied,classroom,student).scorePercent()).isNull();
            assertThat(problem.statistics(student).solved()).isZero();
            UUID run=tx.execute(s->problem.evaluate(student,id,input,true));
            assertThat(jdbc.queryForObject("SELECT (payload::jsonb->'testCases')::text FROM tbl_judge_job WHERE id=:id",Map.of("id",run),String.class)).doesNotContain("hidden secret");
            UUID job=tx.execute(s->problem.evaluate(student,id,input,false));
            UUID duplicate=tx.execute(s->problem.evaluate(student,id,input,false));assertThat(duplicate).isEqualTo(job);
            assertThat(problem.history(student,id,PageRequest.of(0,20)).getData().getItems()).hasSize(1);
            assertThat(problem.history(otherStudent,id,PageRequest.of(0,20)).getData().getItems()).isEmpty();
            assertThatThrownBy(()->problem.result(otherStudent,id,job)).isInstanceOf(RuntimeException.class);
            jdbc.update("UPDATE tbl_judge_job SET status='ACCEPTED' WHERE id=:job",Map.of("job",job));
            assertThat(problem.statistics(student).solved()).isEqualTo(1);
            int generation=0;
            for(var status:List.of("QUEUED","RUNNING","INFRASTRUCTURE_ERROR","WRONG_ANSWER")) {
                generation++;
                rejudge(jdbc,classJob,generation,status);
                rejudge(jdbc,job,generation,status);
                boolean accepted=!status.equals("WRONG_ANSWER");
                org.junit.jupiter.api.Assertions.assertAll(status,
                        ()->assertThat(practice.progress(copied,classroom,student).submitted()).isEqualTo(accepted),
                        ()->assertThat(problem.statistics(student).solved()).isEqualTo(accepted?1:0));
            }
            assertThatThrownBy(()->jdbc.update("UPDATE tbl_problem_revision SET title='tampered'",Map.of())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->jdbc.update("UPDATE tbl_class_practice SET payload='tampered'",Map.of())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_audit_log",Map.of(),Long.class)).isGreaterThanOrEqualTo(8);
        } finally {ds.setConnectionProperties(new Properties());jdbc.getJdbcTemplate().execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    private UUID account(NamedParameterJdbcTemplate jdbc,String role){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO tbl_account VALUES(:id,:role,'ACTIVE')",Map.of("id",id,"role",role));return id;}
    private void rejudge(NamedParameterJdbcTemplate jdbc,UUID original,int generation,String status) {
        jdbc.update("INSERT INTO tbl_judge_job(id,owner_id,submission_id,request_key,generation,mode,payload,status) SELECT :id,owner_id,submission_id,request_key,:generation,mode,payload,:status FROM tbl_judge_job WHERE id=:original",
                Map.of("id",UUID.randomUUID(),"generation",generation,"status",status,"original",original));
    }
    private void migration(NamedParameterJdbcTemplate jdbc,String file)throws Exception{try(var in=getClass().getResourceAsStream("/db/migration/"+file)){jdbc.getJdbcTemplate().execute(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
}
