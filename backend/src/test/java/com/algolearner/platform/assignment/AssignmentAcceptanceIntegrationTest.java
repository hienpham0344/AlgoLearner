package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.judge.JudgeGateway;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"logging.level.root=WARN","debug=false",
        "app.security.jwt-secret=assignment-acceptance-tests-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
@Transactional
class AssignmentAcceptanceIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW=Instant.parse("2026-11-18T12:00:00Z");
    static class MutableClock extends Clock {
        Instant now=NOW;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock acceptanceClock(){return new MutableClock();}
    }
    @Autowired private MutableClock clock;
    @Autowired private AssignmentService service;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AssignmentAttemptRepository attempts;
    @Autowired private AssignmentQuestionOptionRepository options;
    @Autowired private AssignmentLessonProgressProvider lessonProgress;
    @Autowired private AccountRepository accounts;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomMembershipRepository memberships;
    @Autowired private JudgeGateway judge;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private MockMvc mvc;
    @BeforeEach void resetClock(){clock.now=NOW;}

    @Test void mixedQuestionOrderAndUngradedCompletionPersistWithOwnerOnlyAuthoring() throws Exception {
        var s=seed(null,null,false,null,CodeFeedbackLevel.SUMMARY);
        assertThat(service.get(s.teacher().getId(),s.classroom().getId(),s.id()).type()).isEqualTo(AssignmentType.GRADED);
        var objective=choice(s);var code=code(s);
        var essay=service.addQuestion(s.teacher().getId(),s.classroom().getId(),s.id(),new AssignmentDtos.AddQuestionRequest(
                QuestionType.ESSAY,"Essay","Explain",BigDecimal.ZERO,null,EssayFormat.TEXT_ONLY,null,null,null,List.of()));
        service.reorderQuestions(s.teacher().getId(),s.classroom().getId(),s.id(),List.of(essay.id(),code.id(),objective.id()));
        assertThat(service.get(s.teacher().getId(),s.classroom().getId(),s.id()).questions())
                .extracting(AssignmentDtos.QuestionView::id).containsExactly(essay.id(),code.id(),objective.id());
        assertThat(service.get(s.teacher().getId(),s.classroom().getId(),s.id()).questions())
                .extracting(AssignmentDtos.QuestionView::questionType).containsExactly(QuestionType.ESSAY,QuestionType.CODE,QuestionType.SINGLE_CHOICE);
        var other=account(AccountRole.TEACHER);
        mvc.perform(put(path(s)+"/"+s.id()+"/question-order").with(actor(other)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(objective.id(),code.id(),essay.id())))).andExpect(status().isForbidden());
        mvc.perform(put(path(s)+"/"+s.id()).with(actor(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Forbidden\",\"type\":\"UNGRADED\"}")).andExpect(status().isForbidden());
        var ungraded=service.create(s.teacher().getId(),s.classroom().getId(),new AssignmentDtos.CreateAssignmentRequest(
                "Ungraded",null,AssignmentType.UNGRADED,null,null,false,null,null,null,1,null,false,false,null,null,null));
        assertThat(ungraded.type()).isEqualTo(AssignmentType.UNGRADED);
        service.publish(s.teacher().getId(),s.classroom().getId(),ungraded.id());
        var attempt=service.startAttempt(s.student().getId(),s.classroom().getId(),ungraded.id());
        service.submitAttempt(s.student().getId(),s.classroom().getId(),ungraded.id(),attempt.id());
        assertThat(lessonProgress.progress(ungraded.id(),s.classroom().getId(),s.student().getId()).submitted()).isTrue();
    }

    @Test void cloneKeepsSettingsAndIndependentQuestionSnapshotsWithoutLearnerHistory() {
        var s=seed(null,NOW.plusSeconds(600),true,5,CodeFeedbackLevel.DETAILED);var q=code(s);publish(s);
        var source=assignments.findById(s.id()).orElseThrow();source.setGradePublishMode(GradePublishMode.AFTER_DEADLINE);
        source.setShuffleQuestions(true);source.setShuffleAnswers(true);source.setViewAnswersMode(ViewAnswersMode.AFTER_DEADLINE);
        var official=start(s);service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),official.id());
        var target=new ClassroomEntity();target.setId(UUID.randomUUID());target.setName("Target");target.setOwner(s.teacher());
        target.setClassCode(UUID.randomUUID().toString().substring(0,20));target.setStatus(ClassroomStatus.ACTIVE);target.setJoinMode(JoinMode.AUTO_JOIN);
        target=classrooms.saveAndFlush(target);
        var copy=service.cloneAssignment(s.teacher().getId(),s.classroom().getId(),s.id(),target.getId());
        assertThat(copy.status()).isEqualTo(AssignmentStatus.DRAFT);assertThat(copy.deadline()).isEqualTo(source.getDeadline());
        assertThat(copy.gradePublishMode()).isEqualTo(GradePublishMode.AFTER_DEADLINE);
        assertThat(copy.durationMinutes()).isEqualTo(5);assertThat(copy.allowLateSubmission()).isTrue();
        assertThat(copy.shuffleQuestions()).isTrue();assertThat(copy.shuffleAnswers()).isTrue();
        assertThat(copy.codeFeedbackLevel()).isEqualTo(CodeFeedbackLevel.DETAILED);
        assertThat(copy.questions()).hasSize(1);assertThat(copy.questions().getFirst().id()).isNotEqualTo(q.id());
        assertThat(copy.questions().getFirst().codeContent()).isEqualTo(q.codeContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_attempt WHERE assignment_id=?",Long.class,copy.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_rejudge_log WHERE assignment_id=?",Long.class,copy.id())).isZero();
        service.deleteQuestion(s.teacher().getId(),s.classroom().getId(),s.id(),q.id());
        assertThat(service.get(s.teacher().getId(),target.getId(),copy.id()).questions()).hasSize(1);
        service.publish(s.teacher().getId(),target.getId(),copy.id());
        var wrong=account(AccountRole.TEACHER);target.setOwner(wrong);classrooms.saveAndFlush(target);
        UUID targetId=target.getId();
        assertThatThrownBy(()->service.cloneAssignment(s.teacher().getId(),s.classroom().getId(),s.id(),targetId))
                .isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
    }

    @Test void extraAndResetQuotaKeepAllOfficialSubmissionsAndAudit() {
        var s=seed(null,null,false,null,CodeFeedbackLevel.SUMMARY);var q=choice(s);
        assignments.findById(s.id()).orElseThrow().setMaxAttempts(1);publish(s);
        var first=start(s);saveCorrect(s,first,q);service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),first.id());
        assertThatThrownBy(()->start(s)).isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
        service.grantAttempts(s.teacher().getId(),s.classroom().getId(),s.id(),s.student().getId(),new AssignmentDtos.AttemptGrantRequest(1,false,"Extra"));
        var second=start(s);saveCorrect(s,second,q);service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),second.id());
        service.grantAttempts(s.teacher().getId(),s.classroom().getId(),s.id(),s.student().getId(),new AssignmentDtos.AttemptGrantRequest(0,true,"Reset"));
        var third=start(s);assertThat(third.attemptNumber()).isEqualTo(3);
        assertThat(attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(s.id(),s.student().getId()))
                .filteredOn(a->a.getSubmittedAt()!=null).extracting(AssignmentAttemptEntity::getId).containsExactly(second.id(),first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_evaluation WHERE attempt_id IN (?,?)",Long.class,first.id(),second.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_audit_log WHERE target_id=? AND action IN ('GIVE_EXTRA_ATTEMPT','RESET_ATTEMPT_COUNT')",Long.class,s.id()+":"+s.student().getId())).isEqualTo(2);
    }

    @Test void grantedAttemptStartingAtTheExactGrantInstantStillLocksAfterItsOwnTimeout() {
        var s=seed(null,null,false,1,CodeFeedbackLevel.SUMMARY);choice(s);
        assignments.findById(s.id()).orElseThrow().setTimeExpiredAction(TimeExpiredAction.LOCK_WITHOUT_SUBMIT);publish(s);
        var first=start(s);clock.now=NOW.plusSeconds(60);service.expireAttempt(first.id());
        service.grantAttempts(s.teacher().getId(),s.classroom().getId(),s.id(),s.student().getId(),
                new AssignmentDtos.AttemptGrantRequest(1,false,"Unlock after timeout"));
        var second=start(s);assertThat(second.startedAt()).isEqualTo(clock.instant());
        clock.now=NOW.plusSeconds(120);service.expireAttempt(second.id());
        assertThat(attempts.findById(second.id()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.TIMED_OUT);
        assertThatThrownBy(()->start(s)).isInstanceOf(com.algolearner.platform.common.exception.ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode","ATTEMPT_LIMIT_EXCEEDED");
        assertThat(attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(s.id(),s.student().getId())).hasSize(2);
    }

    @Test void shuffledSnapshotRemainsStableAndDoesNotChangeCorrectAnswersOrScore() {
        var s=seed(null,null,false,null,CodeFeedbackLevel.SUMMARY);var first=choice(s);var second=choice(s);
        var assignment=assignments.findById(s.id()).orElseThrow();assignment.setShuffleQuestions(true);assignment.setShuffleAnswers(true);publish(s);
        var attempt=start(s);var reloaded=service.getActiveAttempt(s.student().getId(),s.classroom().getId(),s.id());
        assertThat(reloaded.questions()).isEqualTo(attempt.questions());
        assertThat(attempt.questions()).extracting(AssignmentDtos.QuestionView::id).containsExactlyInAnyOrder(first.id(),second.id());
        for(var question:List.of(first,second))saveCorrect(s,attempt,question);
        service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id());
        assertThat(attempts.findById(attempt.id()).orElseThrow().getFinalScore()).isEqualByComparingTo("20");
    }

    @Test void directCodeRunAndOfficialSubmitReachTheRealPersistentJudge() {
        var s=seed(null,null,false,null,CodeFeedbackLevel.SUMMARY);
        var question=code(s);publish(s);
        var attempt=start(s);
        assertThat(judge).isInstanceOf(com.algolearner.platform.judge.JdbcJudgeGateway.class);
        UUID run=service.runCode(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),question.id(),"class Main {}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE attempt_id=?",Long.class,attempt.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT json_array_length((payload::json)->'testCases') FROM tbl_judge_job WHERE id=?",Integer.class,run)).isEqualTo(1);
        service.saveDraftAnswer(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),
                new AssignmentDtos.SaveDraftAnswerRequest(question.id(),null,null,null,"class Main {}"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE attempt_id=?",Long.class,attempt.id())).isZero();
        service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE attempt_id=?",Long.class,attempt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_code_job WHERE attempt_id=?",Long.class,attempt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT json_array_length((payload::json)->'testCases') FROM tbl_judge_job WHERE mode='SUBMIT' AND payload::json->>'attemptId'=?",Integer.class,attempt.id().toString())).isEqualTo(3);
    }

    @Test void allowLateUntimedAttemptCanStartAndSubmitAfterDeadlineWithoutPenalty() {
        var s=seed(null,NOW.minusSeconds(1),true,null,CodeFeedbackLevel.SUMMARY);
        var q=choice(s);publish(s);var attempt=start(s);saveCorrect(s,attempt,q);
        var submitted=service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id());
        assertThat(submitted.isLate()).isTrue();assertThat(submitted.submittedAt()).isEqualTo(NOW);
        assertThat(attempts.findById(attempt.id()).orElseThrow().getFinalScore()).isEqualByComparingTo("10");
    }

    @Test void lateAllowedTimerUsesDurationAndSchedulerDoesNotExpireAtDeadline() {
        var s=seed(null,NOW.plusSeconds(60),true,5,CodeFeedbackLevel.SUMMARY);
        var q=choice(s);publish(s);var attempt=start(s);saveCorrect(s,attempt,q);
        clock.now=NOW.plusSeconds(61);
        assertThat(attempts.findExpiredTimedAttemptIds(clock.instant(),100)).doesNotContain(attempt.id());
        assertThat(service.getActiveAttempt(s.student().getId(),s.classroom().getId(),s.id()).remainingSeconds()).isEqualTo(239);
        clock.now=NOW.plusSeconds(300);service.expireAttempt(attempt.id());
        var saved=attempts.findById(attempt.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(saved.getSubmittedAt()).isEqualTo(clock.now);assertThat(saved.isLate()).isTrue();
        assertThat(saved.getFinalScore()).isEqualByComparingTo("10");
    }

    @Test void elapsedDeadlineCannotBeReintroducedBeforeALateAttemptsStartTime() {
        var deadline=NOW.minusSeconds(60);var s=seed(null,deadline,true,null,CodeFeedbackLevel.SUMMARY);
        choice(s);publish(s);var attempt=start(s);
        assertThatThrownBy(()->service.update(s.teacher().getId(),s.classroom().getId(),s.id(),
                new AssignmentDtos.UpdateAssignmentRequest("Assessment",null,null,null,deadline,false,null,
                        null,null,null,null,null,null,null,null,null)))
                .isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
        assertThat(assignments.findById(s.id()).orElseThrow().isAllowLateSubmission()).isTrue();
        service.expireAttempt(attempt.id());
        assertThat(attempts.findById(attempt.id()).orElseThrow().getSubmittedAt()).isNull();
    }

    @Test void futurePublicationIsPersistedScheduledAndActivatesAtExactServerStart() throws Exception {
        var s=seed(NOW.plusSeconds(60),null,false,null,CodeFeedbackLevel.SUMMARY);var q=choice(s);
        var published=publish(s);
        assertThat(published.status()).isEqualTo(AssignmentStatus.SCHEDULED);
        var revision=assignments.findById(s.id()).orElseThrow().getPublishedRevisionId();
        mvc.perform(get(path(s)).with(actor(s.student()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].title").value("Assessment"))
                .andExpect(jsonPath("$.data[0].startTime").value("2026-11-18T12:01:00Z"))
                .andExpect(jsonPath("$.data[0].type").doesNotExist())
                .andExpect(jsonPath("$.data[0].deadline").doesNotExist())
                .andExpect(jsonPath("$.data[0].durationMinutes").doesNotExist())
                .andExpect(jsonPath("$.data[0].maxAttempts").doesNotExist())
                .andExpect(jsonPath("$.data[0].isGradePublished").doesNotExist())
                .andExpect(jsonPath("$.data[0].myRemainingAttempts").doesNotExist())
                .andExpect(jsonPath("$.data[0].myBestScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
        mvc.perform(get(path(s)+"/"+s.id()).with(actor(s.student()))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_NOT_STARTED"));
        mvc.perform(post(path(s)+"/"+s.id()+"/attempts").with(actor(s.student()))).andExpect(status().isBadRequest());
        var scheduler=new AssignmentTimeoutScheduler(attempts,service,clock);
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler,"assignments",assignments);
        scheduler.activateScheduledAssignments();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_notification WHERE assignment_id=?",Long.class,s.id())).isZero();
        clock.now=NOW.plusSeconds(60);scheduler.activateScheduledAssignments();scheduler.activateScheduledAssignments();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_notification WHERE assignment_id=?",Long.class,s.id())).isEqualTo(1);
        var started=start(s);
        assertThat(assignments.findById(s.id()).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.PUBLISHED);
        assertThat(started.revisionId()).isEqualTo(revision);
        assertThat(started.questions()).extracting(AssignmentDtos.QuestionView::id).containsExactly(q.id());
    }

    @Test void blockedSchedulesCannotStarveAnActiveAssignmentOutsideTheFirstHundredRows() {
        var blocked=seed(NOW.plusSeconds(1),null,false,null,CodeFeedbackLevel.SUMMARY);publish(blocked);
        blocked.classroom().setStatus(ClassroomStatus.ARCHIVED);classrooms.saveAndFlush(blocked.classroom());
        jdbc.update("""
                INSERT INTO tbl_assignment(id,classroom_id,title,status,start_time)
                SELECT gen_random_uuid(),?,'Blocked schedule','SCHEDULED',? FROM generate_series(1,99)
                """,blocked.classroom().getId(),java.sql.Timestamp.from(NOW.plusSeconds(1)));
        var active=seed(NOW.plusSeconds(60),null,false,null,CodeFeedbackLevel.SUMMARY);publish(active);
        clock.now=NOW.plusSeconds(60);
        var due=assignments.findDueScheduledIds(clock.instant());
        assertThat(due).contains(active.id()).doesNotContain(blocked.id());
        var scheduler=new AssignmentTimeoutScheduler(attempts,service,clock);
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler,"assignments",assignments);
        scheduler.activateScheduledAssignments();
        assertThat(assignments.findById(active.id()).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.PUBLISHED);
    }

    @Test void studentReadsOnlyOwnOfficialHistoryAndFrozenClosedSubmissionUnderReleasePolicy() throws Exception {
        var s=seed(null,null,false,null,CodeFeedbackLevel.SUMMARY);var q=choice(s);publish(s);
        var submitted=start(s);saveCorrect(s,submitted,q);
        service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),submitted.id());
        var draft=start(s);
        service.updateQuestion(s.teacher().getId(),s.classroom().getId(),s.id(),q.id(),
                new AssignmentDtos.AddQuestionRequest(QuestionType.SINGLE_CHOICE,"New question","New prompt",BigDecimal.TEN,
                        null,null,"New explanation",null,null,List.of(new AssignmentDtos.OptionRequest("No",false),new AssignmentDtos.OptionRequest("Yes",true))));
        service.close(s.teacher().getId(),s.classroom().getId(),s.id());
        String history=path(s)+"/"+s.id()+"/attempts";
        mvc.perform(get(history).with(actor(s.student()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(submitted.id().toString()))
                .andExpect(jsonPath("$.data[0].finalScore").doesNotExist());
        mvc.perform(get(history+"/"+submitted.id()).with(actor(s.student()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].title").value("Original question"))
                .andExpect(jsonPath("$.data.questions[0].options[0].isCorrect").doesNotExist());
        mvc.perform(get(history+"/"+draft.id()).with(actor(s.student()))).andExpect(status().isForbidden());
        var other=account(AccountRole.STUDENT);member(s.classroom(),other);
        mvc.perform(get(history+"/"+submitted.id()).with(actor(other))).andExpect(status().isForbidden());
        service.publishGrades(s.teacher().getId(),s.classroom().getId(),s.id());
        mvc.perform(get(history+"/"+submitted.id()).with(actor(s.student()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(10));
    }

    @Test void threeTeacherFeedbackLevelsAreAcceptedPersistedAndSnapshotted() throws Exception {
        for(String level:List.of("PASS_FAIL","TESTCASE_STATUS","FULL_DETAILS")) {
            var s=seed(null,null,false,null,CodeFeedbackLevel.SUMMARY);
            mvc.perform(put(path(s)+"/"+s.id()).with(actor(s.teacher())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Policy\",\"codeFeedbackLevel\":\""+level+"\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.codeFeedbackLevel").value(level));
            var q=code(s);publish(s);var attempt=start(s);
            service.saveDraftAnswer(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),
                    new AssignmentDtos.SaveDraftAnswerRequest(q.id(),null,null,null,"class Main {}"));
            service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id());
            assertThat(jdbc.queryForObject("SELECT payload::json->>'feedback' FROM tbl_judge_job WHERE mode='SUBMIT' AND payload::json->>'attemptId'=?",String.class,attempt.id().toString())).isEqualTo(level);
            UUID job=jdbc.queryForObject("SELECT job_id FROM tbl_assignment_code_job WHERE attempt_id=?",UUID.class,attempt.id());
            complete(job,1);service.refreshCodeResults(attempt.id());service.publishGrades(s.teacher().getId(),s.classroom().getId(),s.id());
            var response=mvc.perform(get(path(s)+"/"+s.id()+"/attempts/"+attempt.id()+"/jobs/"+job).with(actor(s.student())))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var cases=json.readTree(response).path("data").path("cases");
            assertThat(cases.size()).isEqualTo(level.equals("PASS_FAIL")?0:3);
            if(level.equals("TESTCASE_STATUS"))for(var testcase:cases) {
                assertThat(testcase.path("input").isNull() || testcase.path("input").isMissingNode()).isTrue();
                assertThat(testcase.path("expectedOutput").isNull() || testcase.path("expectedOutput").isMissingNode()).isTrue();
            }
            // A later permissive policy cannot upgrade the immutable job's original disclosure level.
            assignments.findById(s.id()).orElseThrow().setCodeFeedbackLevel(CodeFeedbackLevel.DETAILED);
            var upgraded=service.codeResult(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),job);
            assertThat(upgraded.cases().size()).isEqualTo(cases.size());
        }
    }

    @Test void ownCodePollingRequiresPublishedGenerationAndRedactsHiddenIo() throws Exception {
        var s=seed(null,null,false,null,CodeFeedbackLevel.DETAILED);var q=code(s);publish(s);var attempt=start(s);
        UUID run=service.runCode(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),q.id(),"class Main {}");
        String jobs=path(s)+"/"+s.id()+"/attempts/"+attempt.id()+"/jobs/";
        mvc.perform(get(jobs+run).with(actor(s.student()))).andExpect(status().isOk());
        service.saveDraftAnswer(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),
                new AssignmentDtos.SaveDraftAnswerRequest(q.id(),null,null,null,"class Main {}"));
        service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id());
        UUID job=jdbc.queryForObject("SELECT job_id FROM tbl_assignment_code_job WHERE attempt_id=?",UUID.class,attempt.id());
        complete(job,1);service.refreshCodeResults(attempt.id());
        mvc.perform(get(jobs+job).with(actor(s.student()))).andExpect(status().isForbidden());
        service.publishGrades(s.teacher().getId(),s.classroom().getId(),s.id());
        mvc.perform(get(path(s)+"/"+s.id()+"/attempts/"+attempt.id()).with(actor(s.student()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codeJobs[0].jobId").value(job.toString()));
        mvc.perform(get(jobs+job).with(actor(s.student()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].input").value("sample"))
                .andExpect(jsonPath("$.data.cases[1].input").doesNotExist())
                .andExpect(jsonPath("$.data.cases[1].actualOutput").doesNotExist())
                .andExpect(jsonPath("$.data.cases[2].input").value("permitted hidden"));
        assignments.findById(s.id()).orElseThrow().setCodeFeedbackLevel(CodeFeedbackLevel.SUMMARY);
        mvc.perform(get(jobs+job).with(actor(s.student()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.cases.length()").value(0));
        assignments.findById(s.id()).orElseThrow().setCodeFeedbackLevel(CodeFeedbackLevel.DETAILED);
        var other=account(AccountRole.STUDENT);member(s.classroom(),other);
        mvc.perform(get(jobs+job).with(actor(other))).andExpect(status().isForbidden());
        service.rejudge(s.teacher().getId(),s.classroom().getId(),s.id(),new AssignmentDtos.RejudgeRequest("Recheck"));
        UUID newer=jdbc.queryForObject("SELECT job_id FROM tbl_assignment_code_job WHERE attempt_id=? ORDER BY generation DESC LIMIT 1",UUID.class,attempt.id());
        mvc.perform(get(jobs+newer).with(actor(s.student()))).andExpect(status().isForbidden());
        mvc.perform(get(jobs+job).with(actor(s.student()))).andExpect(status().isOk());
        var newerAttempt=start(s);
        mvc.perform(get(path(s)+"/"+s.id()+"/attempts/"+newerAttempt.id()+"/jobs/"+run).with(actor(s.student())))
                .andExpect(status().isForbidden());
    }

    @Test void rejudgeSelectsLatestPublishedCasesAndWeightsButKeepsOriginalIdentitySourceAndHistory() throws Exception {
        var s=seed(null,null,false,null,CodeFeedbackLevel.DETAILED);var q=code(s);publish(s);var attempt=start(s);
        service.saveDraftAnswer(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),
                new AssignmentDtos.SaveDraftAnswerRequest(q.id(),null,null,null,"class Main {}"));
        service.submitAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id());
        UUID originalJob=latestJob(attempt.id());complete(originalJob,1);service.refreshCodeResults(attempt.id());
        service.publishGrades(s.teacher().getId(),s.classroom().getId(),s.id());
        UUID originalEvaluation=attempts.findById(attempt.id()).orElseThrow().getPublishedEvaluationId();
        service.updateQuestion(s.teacher().getId(),s.classroom().getId(),s.id(),q.id(),new AssignmentDtos.AddQuestionRequest(
                QuestionType.CODE,"Code v2","Changed statement",new BigDecimal("100"),null,null,null,null,"JAVA",List.of(),null,
                List.of(new JudgeGateway.TestCaseSnapshot("sample v2","out",true,true),
                        new JudgeGateway.TestCaseSnapshot("new hidden","different",false,false),
                        new JudgeGateway.TestCaseSnapshot("permitted v2","out",false,true)),new BigDecimal("5"),
                List.of(BigDecimal.ONE,new BigDecimal("3"),BigDecimal.ZERO)));
        publish(s);UUID newerRevision=assignments.findById(s.id()).orElseThrow().getPublishedRevisionId();
        // Working edits after publication must not become the selected testcase context.
        service.updateQuestion(s.teacher().getId(),s.classroom().getId(),s.id(),q.id(),new AssignmentDtos.AddQuestionRequest(
                QuestionType.CODE,"Unpublished v3","Working only",new BigDecimal("200"),null,null,null,null,"JAVA",List.of(),null,
                List.of(new JudgeGateway.TestCaseSnapshot("unpublished","wrong context",true,true)),null,null));
        String base=path(s)+"/"+s.id();String jobs=base+"/attempts/"+attempt.id()+"/jobs/";
        mvc.perform(post(base+"/rejudge").with(actor(s.teacher())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Use approved v2\",\"target\":\"LATEST_PUBLISHED\"}")).andExpect(status().isNoContent());
        UUID latest=latestJob(attempt.id());
        assertThat(judge.jobReference(s.student().getId(),latest).revisionId()).isEqualTo(newerRevision);
        assertThat(attempts.findById(attempt.id()).orElseThrow().getPublishedEvaluationId()).isEqualTo(originalEvaluation);
        mvc.perform(get(jobs+originalJob).with(actor(s.student()))).andExpect(status().isOk());
        mvc.perform(get(jobs+latest).with(actor(s.student()))).andExpect(status().isForbidden());
        var mixed=new JudgeGateway.EvaluationResult(latest,2,JudgeGateway.Verdict.WRONG_ANSWER,2,3,List.of(
                new JudgeGateway.CaseResult(0,JudgeGateway.Verdict.ACCEPTED,null,null,"out"),
                new JudgeGateway.CaseResult(1,JudgeGateway.Verdict.WRONG_ANSWER,null,null,"out"),
                new JudgeGateway.CaseResult(2,JudgeGateway.Verdict.ACCEPTED,null,null,"out")));
        jdbc.update("UPDATE tbl_judge_job SET status='WRONG_ANSWER',result=? WHERE id=?",json.writeValueAsString(mixed),latest);
        service.refreshCodeResults(attempt.id());
        assertThat(attempts.findById(attempt.id()).orElseThrow().getFinalScore()).isEqualByComparingTo("2.50");
        assertThat(service.ownOfficialAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id()).finalScore()).isEqualByComparingTo("10");
        service.publishGrades(s.teacher().getId(),s.classroom().getId(),s.id());
        assertThat(service.ownOfficialAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id()).finalScore()).isEqualByComparingTo("2.50");
        mvc.perform(get(jobs+latest).with(actor(s.student()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].input").value("sample v2"));
        assertThat(jdbc.queryForObject("SELECT parts->0->>'judgeRevisionId' FROM tbl_assignment_evaluation WHERE id=?",String.class,originalEvaluation))
                .isEqualTo(attempt.revisionId().toString());
        assertThat(jdbc.queryForObject("SELECT parts->0->>'judgeRevisionId' FROM tbl_assignment_evaluation WHERE attempt_id=? ORDER BY generation DESC LIMIT 1",String.class,attempt.id()))
                .isEqualTo(newerRevision.toString());
        mvc.perform(post(base+"/rejudge").with(actor(s.teacher())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Default returns to original\"}")).andExpect(status().isNoContent());
        UUID rerunOriginal=latestJob(attempt.id());
        assertThat(judge.jobReference(s.student().getId(),rerunOriginal).revisionId()).isEqualTo(attempt.revisionId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE attempt_id=?",Long.class,attempt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_attempt WHERE assignment_id=? AND submitted_at IS NOT NULL",Long.class,s.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source FROM tbl_submission WHERE attempt_id=?",String.class,attempt.id())).isEqualTo("class Main {}");
        complete(rerunOriginal,3);service.refreshCodeResults(attempt.id());service.publishGrades(s.teacher().getId(),s.classroom().getId(),s.id());
        assertThat(service.ownOfficialAttempt(s.student().getId(),s.classroom().getId(),s.id(),attempt.id()).finalScore()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT final_score FROM tbl_assignment_evaluation WHERE id=?",BigDecimal.class,originalEvaluation)).isEqualByComparingTo("10");
        assertThatThrownBy(()->jdbc.update("UPDATE tbl_assignment_code_job SET revision_id=? WHERE job_id=?",newerRevision,originalJob))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private UUID latestJob(UUID attempt) {
        return jdbc.queryForObject("SELECT job_id FROM tbl_assignment_code_job WHERE attempt_id=? ORDER BY generation DESC LIMIT 1",UUID.class,attempt);
    }

    private void complete(UUID job,long generation) throws Exception {
        var result=new JudgeGateway.EvaluationResult(job,generation,JudgeGateway.Verdict.ACCEPTED,3,3,List.of(
                new JudgeGateway.CaseResult(0,JudgeGateway.Verdict.ACCEPTED,"sample","out","out"),
                new JudgeGateway.CaseResult(1,JudgeGateway.Verdict.ACCEPTED,"secret hidden","secret out","secret result"),
                new JudgeGateway.CaseResult(2,JudgeGateway.Verdict.ACCEPTED,"permitted hidden","out","out")));
        jdbc.update("UPDATE tbl_judge_job SET status='ACCEPTED',result=? WHERE id=?",json.writeValueAsString(result),job);
    }
    private record Seed(AccountEntity teacher,AccountEntity student,ClassroomEntity classroom,UUID id) { }
    private Seed seed(Instant start,Instant deadline,boolean allowLate,Integer duration,CodeFeedbackLevel feedback) {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        var classroom=new ClassroomEntity();classroom.setId(UUID.randomUUID());classroom.setName("Acceptance");classroom.setOwner(teacher);
        classroom.setClassCode(UUID.randomUUID().toString().substring(0,20));classroom.setStatus(ClassroomStatus.ACTIVE);classroom.setJoinMode(JoinMode.AUTO_JOIN);
        classroom=classrooms.saveAndFlush(classroom);member(classroom,student);
        var input=new AssignmentDtos.CreateAssignmentRequest("Assessment",null,AssignmentType.GRADED,start,deadline,allowLate,null,
                duration,TimeExpiredAction.AUTO_SUBMIT,0,ScoringPolicy.HIGHEST,false,false,GradePublishMode.MANUAL,ViewAnswersMode.NEVER,feedback);
        return new Seed(teacher,student,classroom,service.create(teacher.getId(),classroom.getId(),input).id());
    }
    private AssignmentDtos.QuestionView choice(Seed s) {
        return service.addQuestion(s.teacher().getId(),s.classroom().getId(),s.id(),new AssignmentDtos.AddQuestionRequest(
                QuestionType.SINGLE_CHOICE,"Original question","Original prompt",BigDecimal.TEN,null,null,"Private explanation",null,null,
                List.of(new AssignmentDtos.OptionRequest("Correct",true),new AssignmentDtos.OptionRequest("Wrong",false))));
    }
    private AssignmentDtos.QuestionView code(Seed s) {
        return service.addQuestion(s.teacher().getId(),s.classroom().getId(),s.id(),new AssignmentDtos.AddQuestionRequest(
                QuestionType.CODE,"Code","Solve",BigDecimal.TEN,null,null,null,null,"JAVA",List.of(),null,
                List.of(new JudgeGateway.TestCaseSnapshot("sample","out",true,true),new JudgeGateway.TestCaseSnapshot("secret hidden","secret out",false,false),
                        new JudgeGateway.TestCaseSnapshot("permitted hidden","out",false,true)),null,null));
    }
    private AssignmentDtos.AssignmentDetailView publish(Seed s){return service.publish(s.teacher().getId(),s.classroom().getId(),s.id());}
    private AssignmentDtos.AttemptView start(Seed s){return service.startAttempt(s.student().getId(),s.classroom().getId(),s.id());}
    private void saveCorrect(Seed s,AssignmentDtos.AttemptView attempt,AssignmentDtos.QuestionView q) {
        service.saveDraftAnswer(s.student().getId(),s.classroom().getId(),s.id(),attempt.id(),new AssignmentDtos.SaveDraftAnswerRequest(q.id(),
                "[\""+q.options().getFirst().id()+"\"]",null,null,null));
    }
    private AccountEntity account(AccountRole role) {
        var a=new AccountEntity();a.setId(UUID.randomUUID());a.setUsername("acceptance-"+UUID.randomUUID());a.setEmail(a.getUsername()+"@example.test");
        a.setRole(role);a.setStatus(AccountStatus.ACTIVE);a.setPasswordHash("unused-fixture");return accounts.saveAndFlush(a);
    }
    private void member(ClassroomEntity classroom,AccountEntity student) {
        var m=new ClassroomMembershipEntity();m.setId(UUID.randomUUID());m.setClassroom(classroom);m.setStudent(student);
        m.setStatus(MembershipStatus.ACTIVE);m.setJoinedAt(NOW);memberships.saveAndFlush(m);
    }
    private String path(Seed s){return "/api/v1/classrooms/"+s.classroom().getId()+"/assignments";}
    private RequestPostProcessor actor(AccountEntity a){return authentication(new UsernamePasswordAuthenticationToken(a.getId().toString(),null,
            List.of(new SimpleGrantedAuthority("ROLE_"+a.getRole()))));}
}
