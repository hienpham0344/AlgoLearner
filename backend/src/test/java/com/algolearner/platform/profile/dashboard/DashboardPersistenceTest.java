package com.algolearner.platform.profile.dashboard;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.profile.InternalPracticeRepository;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"logging.level.root=WARN","app.security.jwt-secret=dashboard-persistence-test-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class DashboardPersistenceTest extends PostgresIntegrationTest {
    @Autowired JdbcDashboardRepository repository;
    @Autowired InternalPracticeRepository practice;
    @Autowired AccountRepository accounts;
    @Autowired ClassroomService classrooms;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.SpyBean org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedJdbc;
    private final Instant now=Instant.parse("2026-11-18T17:00:00Z");

    @Test void studentPreviewsUseMembershipPublicationWindowAndOwnEnrollment() {
        var teacher=account(AccountRole.TEACHER); var student=account(AccountRole.STUDENT);
        var outsider=account(AccountRole.STUDENT);
        var own=classroom(teacher); var foreign=classroom(teacher);
        jdbc.update("INSERT INTO tbl_classroom_membership(id,classroom_id,student_id,status,joined_at) VALUES(?,?,?,'ACTIVE',?)",UUID.randomUUID(),own,student,Timestamp.from(now));
        UUID visible=assignment(own,"PUBLISHED",now.plusSeconds(60));
        assignment(own,"DRAFT",now.plusSeconds(60)); assignment(foreign,"PUBLISHED",now.plusSeconds(60));
        assignment(own,"PUBLISHED",now.minusSeconds(60));
        assignment(own,"PUBLISHED",now.plusSeconds(900000));
        assignment(own,"SCHEDULED",now.plusSeconds(900000));
        UUID quiz=UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_quiz(id,classroom_id,title,status,graded,closes_at,max_attempts,questions_json) VALUES(?,?,?,'PUBLISHED',true,?,1,'[]')",quiz,own,"Quiz",Timestamp.from(now.plusSeconds(60)));
        UUID course=course(teacher,"PUBLISHED"); UUID enrollment=UUID.randomUUID(); UUID unit=UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_public_course_unit(id,course_id,title,content,display_order) VALUES(?,?,'Unit','Text',0)",unit,course);
        jdbc.update("INSERT INTO tbl_course_enrollment(id,course_id,account_id) VALUES(?,?,?)",enrollment,course,student);
        jdbc.update("INSERT INTO tbl_course_unit_completion(id,enrollment_id,unit_id) VALUES(?,?,?)",UUID.randomUUID(),enrollment,unit);
        var context=context(student);
        assertThat(repository.studentAssignments(context).items()).extracting(DashboardResponse.Assignment::assignmentId).containsExactly(visible);
        assertThat(repository.studentAssignments(context).totalElements()).isEqualTo(1);
        assertThat(repository.studentQuizzes(context).items()).extracting(DashboardResponse.Quiz::quizId).containsExactly(quiz);
        assertThat(repository.courses(context).items()).singleElement().satisfies(c -> assertThat(c.progressPercent()).isEqualTo(100));
        assertThat(repository.studentAssignments(context(outsider)).items()).isEmpty();
        assertThat(repository.courses(context(outsider)).items()).isEmpty();
    }

    @Test void scheduledUpcomingAssignmentsAreVisibleOnlyToActiveMembers() {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);var outsider=account(AccountRole.STUDENT);
        UUID classroom=classroom(teacher);
        jdbc.update("INSERT INTO tbl_classroom_membership(id,classroom_id,student_id,status,joined_at) VALUES(?,?,?,'ACTIVE',?)",
                UUID.randomUUID(),classroom,student,Timestamp.from(now));
        UUID scheduled=assignment(classroom,"SCHEDULED",now.plusSeconds(7200));
        jdbc.update("UPDATE tbl_assignment SET start_time=? WHERE id=?",Timestamp.from(now.plusSeconds(3600)),scheduled);

        assertThat(repository.studentAssignments(context(student)).items())
                .extracting(DashboardResponse.Assignment::assignmentId).containsExactly(scheduled);
        assertThat(repository.studentAssignments(context(student)).totalElements()).isEqualTo(1);
        assertThat(repository.studentAssignments(context(student)).items()).singleElement().satisfies(item -> {
            assertThat(item.opensAt()).isEqualTo(now.plusSeconds(3600));
            assertThat(item.effectiveDueAt()).isNull();
        });
        assertThat(repository.studentAssignments(context(outsider)).items()).isEmpty();
        jdbc.update("UPDATE tbl_classroom_membership SET status='LEFT' WHERE classroom_id=? AND student_id=?",classroom,student);
        assertThat(repository.studentAssignments(context(student)).items()).isEmpty();
    }

    @Test void upcomingScheduledAssignmentDoesNotRequireADeadline() {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        UUID classroom=classroom(teacher);
        jdbc.update("INSERT INTO tbl_classroom_membership(id,classroom_id,student_id,status,joined_at) VALUES(?,?,?,'ACTIVE',?)",
                UUID.randomUUID(),classroom,student,Timestamp.from(now));
        UUID scheduled=assignment(classroom,"SCHEDULED",now.plusSeconds(7200));
        jdbc.update("UPDATE tbl_assignment SET start_time=?,deadline=NULL WHERE id=?",Timestamp.from(now.plusSeconds(3600)),scheduled);
        assertThat(repository.studentAssignments(context(student)).items())
                .extracting(DashboardResponse.Assignment::assignmentId).containsExactly(scheduled);
    }

    @Test void teacherOpenAssignmentsIncludePublishedLateWorkAfterItsDeadline() {
        var teacher=account(AccountRole.TEACHER);var other=account(AccountRole.TEACHER);
        UUID classroom=classroom(teacher);
        UUID lateOpen=assignment(classroom,"PUBLISHED",now.minusSeconds(60));
        jdbc.update("UPDATE tbl_assignment SET allow_late_submission=true WHERE id=?",lateOpen);
        assignment(classroom,"PUBLISHED",now.minusSeconds(60));
        assertThat(repository.teacherAssignments(context(teacher)).items())
                .extracting(DashboardResponse.Assignment::assignmentId).containsExactly(lateOpen);
        assertThat(repository.teacherAssignments(context(other)).items()).isEmpty();
    }

    @Test void teacherWorkCountsAttemptsOnceAndExcludesOtherOwners() {
        var teacher=account(AccountRole.TEACHER);var other=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        UUID own=classroom(teacher), foreign=classroom(other);
        UUID assignment=assignment(own,"PUBLISHED",now.plusSeconds(60));
        UUID hidden=assignment(foreign,"PUBLISHED",now.plusSeconds(60));
        UUID official=attempt(assignment,student,"SUBMITTED",true);
        attempt(assignment,student,"IN_PROGRESS",false);attempt(hidden,student,"SUBMITTED",true);
        var context=context(teacher);
        assertThat(repository.ungraded(context).items()).extracting(DashboardResponse.Submission::submissionId).containsExactly(official);
        assertThat(repository.recentSubmissions(context).totalElements()).isEqualTo(1);
        assertThat(repository.classStatistics(context).items()).singleElement().satisfies(c -> {
            assertThat(c.publishedAssignmentCount()).isEqualTo(1);assertThat(c.ungradedWorkCount()).isEqualTo(1);
        });
    }

    @Test void publicPracticeCountsCanonicalLatestGenerationAndAdminUsesRealSources() {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);var outsider=account(AccountRole.STUDENT);
        UUID course=course(teacher,"DRAFT");
        jdbc.update("INSERT INTO tbl_public_course_revision(id,course_id,author_id,revision_no,status,snapshot,submitted_at) VALUES(?,?,?,1,'PENDING_REVIEW','{}'::jsonb,CURRENT_TIMESTAMP)",UUID.randomUUID(),course,teacher);
        UUID problem=UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_public_problem(id,author_id,status,review_status) VALUES(?,?,'DRAFT','PENDING_REVIEW')",problem,teacher);
        UUID submission=UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_submission(id,owner_id,scope,resource_id,revision_id,language,source,testcase_snapshot,created_at) VALUES(?,?,'PUBLIC_PROBLEM',?,?,'PYTHON','pass','[]',?)",submission,student,problem,UUID.randomUUID(),Timestamp.from(now));
        // Trusted worker-result fixtures exercise persisted projections; no Docker execution is claimed.
        job(student,submission,0,"ACCEPTED");job(student,submission,1,"ACCEPTED");
        assertThat(practice.acceptedPublicSubmissions(student)).hasSize(1);
        assertThat(practice.acceptedPublicSubmissions(outsider)).isEmpty();
        assertThat(repository.contributions(context(teacher),false)).isEqualTo(new DashboardResponse.Contributions(1,1));
        assertThat(repository.contributions(context(outsider),false)).isEqualTo(new DashboardResponse.Contributions(0,0));
        assertThat(repository.totals().officialSubmissionCount()).isGreaterThanOrEqualTo(1);
        assertThat(repository.chartEvents(now.minusSeconds(1),now.plusSeconds(1))).anyMatch(e -> e.metric().equals("SUBMISSION"));
        var auditId = jdbc.queryForObject("""
                INSERT INTO tbl_audit_log(actor_id,actor_role,target_type,target_id,action,created_at)
                VALUES (?,'TEACHER','COURSE',?,'COURSE_SUBMIT',clock_timestamp()) RETURNING 'audit-'||id
                """, String.class, teacher, course.toString());
        assertThat(repository.audits(context(teacher)).items())
                .anyMatch(e -> e.auditId().equals(auditId) && e.action().equals("COURSE_SUBMIT"));
        assertThat(repository.activities(context(teacher)).items())
                .anyMatch(e -> e.eventId().equals(auditId) && e.action().equals("COURSE_SUBMIT"));
        job(student,submission,2,"WRONG_ANSWER");
        assertThat(practice.acceptedPublicSubmissions(student)).isEmpty();
    }
    @Test void previewQueryCountStaysConstantWhenRowsExceedLimit() {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        var classroom=classroom(teacher);
        jdbc.update("INSERT INTO tbl_classroom_membership(id,classroom_id,student_id,status,joined_at) VALUES(?,?,?,'ACTIVE',?)",UUID.randomUUID(),classroom,student,Timestamp.from(now));
        for(int i=0;i<12;i++) assignment(classroom,"PUBLISHED",now.plusSeconds(3600));
        var context=new DashboardRepository.Context(student,now,now.plusSeconds(604800),3);
        org.mockito.Mockito.clearInvocations(namedJdbc);
        var result=repository.studentAssignments(context);
        assertThat(result.totalElements()).isEqualTo(12);assertThat(result.items()).hasSize(3);assertThat(result.hasMore()).isTrue();
        org.mockito.Mockito.verify(namedJdbc,org.mockito.Mockito.times(1)).queryForObject(
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.eq(Long.class));
        org.mockito.Mockito.verify(namedJdbc,org.mockito.Mockito.times(1)).query(
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"QUEUED","RUNNING","INFRASTRUCTURE_ERROR"})
    void unfinishedOrFailedRejudgeKeepsPreviousOfficialPublicPracticeResult(String status) {
        var student=account(AccountRole.STUDENT);
        UUID problem=UUID.randomUUID(), submission=UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_submission(id,owner_id,scope,resource_id,revision_id,language,source,testcase_snapshot,created_at) VALUES(?,?,'PUBLIC_PROBLEM',?,?,'PYTHON','pass','[]',?)",submission,student,problem,UUID.randomUUID(),Timestamp.from(now));
        job(student,submission,0,"ACCEPTED");
        job(student,submission,1,status);

        assertThat(practice.acceptedPublicSubmissions(student)).singleElement().satisfies(accepted -> {
            assertThat(accepted.problemId()).isEqualTo(problem);
            assertThat(accepted.submittedAt()).isEqualTo(now);
        });

        // A valid completed rejudge becomes official, even when it removes completion.
        job(student,submission,2,"WRONG_ANSWER");
        assertThat(practice.acceptedPublicSubmissions(student)).isEmpty();
    }

    private DashboardRepository.Context context(UUID account){return new DashboardRepository.Context(account,now,now.plusSeconds(604800),100);}
    private UUID account(AccountRole role){var a=new AccountEntity();a.setId(UUID.randomUUID());a.setUsername("dash-"+a.getId());a.setEmail(a.getUsername()+"@example.test");a.setFullName("Dashboard");a.setPasswordHash("unused-test-fixture");a.setRole(role);a.setStatus(AccountStatus.ACTIVE);return accounts.saveAndFlush(a).getId();}
    private UUID classroom(UUID owner){return classrooms.create(owner,new ClassroomDtos.Create("Dashboard",null,JoinMode.AUTO_JOIN,null,null)).id();}
    private UUID assignment(UUID classroom,String status,Instant deadline){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title,status,deadline) VALUES(?,?,'Assignment',?,?)",id,classroom,status,Timestamp.from(deadline));return id;}
    private UUID course(UUID teacher,String status){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO tbl_public_course(id,author_id,title,status) VALUES(?,?,'Course',?)",id,teacher,status);return id;}
    private UUID attempt(UUID assignment,UUID student,String status,boolean submitted){UUID id=UUID.randomUUID();int n=jdbc.queryForObject("SELECT COUNT(*)+1 FROM tbl_assignment_attempt WHERE assignment_id=? AND student_id=?",Integer.class,assignment,student);jdbc.update("INSERT INTO tbl_assignment_attempt(id,assignment_id,student_id,attempt_number,status,started_at,submitted_at) VALUES(?,?,?,?,?,?,?)",id,assignment,student,n,status,Timestamp.from(now),submitted?Timestamp.from(now):null);return id;}
    private void job(UUID student,UUID submission,int generation,String status){jdbc.update("INSERT INTO tbl_judge_job(id,owner_id,submission_id,request_key,generation,mode,payload,status) VALUES(?,?,?,'dashboard',?,'SUBMIT','{}',?)",UUID.randomUUID(),student,submission,generation,status);}
}
