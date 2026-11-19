package com.algolearner.platform.judge;

import static com.algolearner.platform.judge.JudgeGateway.*;
import static org.assertj.core.api.Assertions.*;
import com.algolearner.platform.auth.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=judge-scoring-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class JudgeScoringPrivacyTest extends PostgresIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired JudgeGateway gateway;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void ownerFeedbackNeverExposesHiddenIoEvenIfWorkerStoredDetailedResults() throws Exception {
        var owner = account();
        UUID job = seededWorkerResult(owner.getId(),Feedback.PASS_FAIL);
        assertThat(gateway.result(owner.getId(),job).cases()).isEmpty();
        assertThatThrownBy(() -> gateway.result(UUID.randomUUID(),job)).isInstanceOf(RuntimeException.class);
        UUID statusOnly = seededWorkerResult(owner.getId(),Feedback.TESTCASE_STATUS);
        assertThat(gateway.result(owner.getId(),statusOnly).cases()).hasSize(2).allSatisfy(result -> {
            assertThat(result.input()).isNull();assertThat(result.expectedOutput()).isNull();assertThat(result.actualOutput()).isNull();
        });
    }

    @Test
    void trustedScoringReceivesPerCaseVerdictsWithoutAnyTestcaseIo() throws Exception {
        var owner = account();
        UUID job = seededWorkerResult(owner.getId(),Feedback.PASS_FAIL);
        var result = gateway.scoringResult(owner.getId(),job);
        assertThat(result.cases()).extracting(CaseResult::verdict).containsExactly(Verdict.ACCEPTED,Verdict.WRONG_ANSWER);
        assertThat(result.cases()).allSatisfy(value -> {
            assertThat(value.input()).isNull();assertThat(value.expectedOutput()).isNull();assertThat(value.actualOutput()).isNull();
        });
        assertThatThrownBy(() -> gateway.scoringResult(UUID.randomUUID(),job)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejudgeCreatesNewJobWithoutCountingAnotherOfficialSubmission() {
        var owner = account();
        var request = new CodeEvaluationRequest(owner.getId(),null,null,"print(input())",Language.PYTHON,
                List.of(new TestCaseSnapshot("input","output",true,false)),UUID.randomUUID().toString(),0,
                Feedback.PASS_FAIL,SubmissionScope.PUBLIC_PROBLEM,UUID.randomUUID(),UUID.randomUUID(),new Limits(10,2000,65536));
        UUID first = gateway.submit(request);
        UUID second = gateway.submit(new CodeEvaluationRequest(request.ownerId(),request.questionId(),request.attemptId(),request.source(),
                request.language(),request.testCases(),request.requestKey(),1,request.feedback(),request.scope(),request.resourceId(),request.revisionId(),request.limits()));
        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE owner_id=?",Integer.class,owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT submission_id) FROM tbl_judge_job WHERE owner_id=?",Integer.class,owner.getId())).isEqualTo(1);
        assertThatThrownBy(() -> gateway.submit(new CodeEvaluationRequest(request.ownerId(),request.questionId(),request.attemptId(),"print('changed')",
                request.language(),request.testCases(),request.requestKey(),2,request.feedback(),request.scope(),request.resourceId(),request.revisionId(),request.limits())))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class)
                .extracting(error -> ((com.algolearner.platform.common.exception.AppException)error).getErrorCode())
                .isEqualTo("JUDGE_IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_judge_job WHERE owner_id=?",Integer.class,owner.getId())).isEqualTo(2);
    }

    @Test
    void trustedJobReferenceBindsRunToItsParentWithoutExposingCodeOrTestcases() throws Exception {
        var owner = account();
        UUID question = UUID.randomUUID(),attempt = UUID.randomUUID(),resource = UUID.randomUUID(),revision = UUID.randomUUID();
        var request = new CodeEvaluationRequest(owner.getId(),question,attempt,"print('private-source')",Language.PYTHON,
                List.of(new TestCaseSnapshot("private-input","private-expected",true,false)),UUID.randomUUID().toString(),0,
                Feedback.PASS_FAIL,SubmissionScope.CLASS_ASSIGNMENT,resource,revision,new Limits(10,2000,65536));
        UUID job = gateway.run(request);
        assertThat(gateway.jobReference(owner.getId(),job))
                .isEqualTo(new JobReference(job,owner.getId(),attempt,question,SubmissionScope.CLASS_ASSIGNMENT,resource,revision,true,0));
        assertThat(mapper.writeValueAsString(gateway.jobReference(owner.getId(),job)))
                .doesNotContain("private-source","private-input","private-expected");
        assertThatThrownBy(() -> gateway.jobReference(UUID.randomUUID(),job))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
    }

    @Test
    void explicitRejudgeCanSelectAnotherRevisionWhileKeepingOfficialIdentityAndOriginalSnapshot() {
        var owner = account();
        var original = new CodeEvaluationRequest(owner.getId(),UUID.randomUUID(),UUID.randomUUID(),"print(input())",Language.PYTHON,
                List.of(new TestCaseSnapshot("input","original-output",true,false)),UUID.randomUUID().toString(),0,
                Feedback.PASS_FAIL,SubmissionScope.CLASS_ASSIGNMENT,UUID.randomUUID(),UUID.randomUUID(),new Limits(10,2000,65536));
        UUID first = gateway.submit(original);
        var revised = new CodeEvaluationRequest(original.ownerId(),original.questionId(),original.attemptId(),original.source(),original.language(),
                List.of(new TestCaseSnapshot("input","revised-output",true,false)),original.requestKey(),2,
                Feedback.TESTCASE_STATUS,original.scope(),original.resourceId(),UUID.randomUUID(),original.limits());
        assertThatThrownBy(() -> gateway.submit(revised)).isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        UUID second = gateway.rejudge(revised);
        assertThat(second).isNotEqualTo(first);
        assertThat(gateway.rejudge(revised)).isEqualTo(second);
        assertThat(gateway.jobReference(owner.getId(),second).revisionId()).isEqualTo(revised.revisionId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE owner_id=?",Integer.class,owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revision_id FROM tbl_submission WHERE owner_id=?",UUID.class,owner.getId())).isEqualTo(original.revisionId());
        assertThat(jdbc.queryForObject("SELECT testcase_snapshot FROM tbl_submission WHERE owner_id=?",String.class,owner.getId()))
                .contains("original-output").doesNotContain("revised-output");
        assertThatThrownBy(() -> gateway.rejudge(new CodeEvaluationRequest(original.ownerId(),original.questionId(),original.attemptId(),"changed source",original.language(),
                revised.testCases(),original.requestKey(),3,revised.feedback(),original.scope(),original.resourceId(),revised.revisionId(),original.limits())))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        assertThatThrownBy(() -> gateway.rejudge(new CodeEvaluationRequest(original.ownerId(),original.questionId(),original.attemptId(),original.source(),original.language(),
                revised.testCases(),original.requestKey(),1,revised.feedback(),original.scope(),original.resourceId(),revised.revisionId(),original.limits())))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        var stranger = account();
        assertThatThrownBy(() -> gateway.rejudge(new CodeEvaluationRequest(stranger.getId(),original.questionId(),original.attemptId(),original.source(),original.language(),
                revised.testCases(),original.requestKey(),3,revised.feedback(),original.scope(),original.resourceId(),revised.revisionId(),original.limits())))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE owner_id=?",Integer.class,stranger.getId())).isZero();
    }

    private UUID seededWorkerResult(UUID owner,Feedback feedback) throws Exception {
        UUID job = gateway.submit(new CodeEvaluationRequest(owner,null,null,"print(input())",Language.PYTHON,
                List.of(new TestCaseSnapshot("sample","ok",true,false),new TestCaseSnapshot("hidden-input","hidden-output",false,false)),
                UUID.randomUUID().toString(),1,feedback,SubmissionScope.PUBLIC_PROBLEM,UUID.randomUUID(),UUID.randomUUID(),new Limits(10,2000,65536)));
        var result = new EvaluationResult(job,1,Verdict.WRONG_ANSWER,1,2,List.of(
                new CaseResult(0,Verdict.ACCEPTED,"sample","ok","ok"),
                new CaseResult(1,Verdict.WRONG_ANSWER,"hidden-input","hidden-output","echo-hidden-input")));
        // Explicit trusted-worker fixture: this verifies result projection, not actual Docker execution.
        jdbc.update("UPDATE tbl_judge_job SET status='WRONG_ANSWER',result=? WHERE id=?",mapper.writeValueAsString(result),job);
        return job;
    }

    private AccountEntity account() {
        var value = new AccountEntity();value.setId(UUID.randomUUID());value.setUsername("judge_"+UUID.randomUUID().toString().replace("-",""));
        value.setEmail(value.getUsername()+"@example.test");value.setPasswordHash("unused");value.setRole(AccountRole.STUDENT);value.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(value);
    }
}
