package com.algolearner.platform.assignment;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.judge.JudgeGateway;
import com.algolearner.platform.problem.PublicProblemCatalog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class AssignmentCodeJudge {
    private final JudgeGateway judge;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private AssignmentRevisionRepository revisions;
    public AssignmentCodeJudge(JudgeGateway judge, JdbcTemplate jdbc, ObjectMapper json){this.judge=judge;this.jdbc=jdbc;this.json=json;}
    private PublicProblemCatalog.ContentSnapshot content(AssignmentSnapshotPolicy.Question question) {
        if(question.codeContent()==null)throw new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Original testcase snapshot unavailable; cannot judge historical content.");
        try{return json.readValue(question.codeContent(),PublicProblemCatalog.ContentSnapshot.class);}
        catch(JsonProcessingException failure){throw new IllegalStateException("Cannot read original testcase snapshot",failure);}
    }
    record Completed(UUID question, java.math.BigDecimal score, UUID job) { }
    java.util.List<Completed> completed(AssignmentAttemptEntity attempt) {
        return jdbc.query("""
                SELECT question_id,job_id,revision_id FROM (
                  SELECT DISTINCT ON(question_id) question_id,job_id,revision_id,applied_at FROM tbl_assignment_code_job
                  WHERE attempt_id=? ORDER BY question_id,generation DESC
                ) latest WHERE applied_at IS NULL
                """,(r,n)->new UUID[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class)},attempt.getId()).stream().map(ids->{
            var result=judge.scoringResult(attempt.getStudent().getId(),ids[1]);
            if(result.verdict()==JudgeGateway.Verdict.QUEUED || result.verdict()==JudgeGateway.Verdict.RUNNING
                    || result.verdict()==JudgeGateway.Verdict.INFRASTRUCTURE_ERROR)return null;
            var question=AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).questions().stream()
                    .filter(q->q.id().equals(ids[0])).findFirst().orElseThrow();
            var weights=question.caseWeights();
            if(ids[2]!=null && !ids[2].equals(attempt.getRevisionId())) {
                var revision=revisions.findById(ids[2]).filter(value->value.getAssignment().getId().equals(attempt.getAssignment().getId())
                        && "TRUSTED".equals(value.getProvenance())).orElseThrow(()->new IllegalStateException("Judge revision scope mismatch"));
                weights=AssignmentSnapshotPolicy.decode(revision.getContent()).questions().stream()
                        .filter(value->value.id().equals(question.id()) && value.type()==QuestionType.CODE).findFirst()
                        .orElseThrow(()->new IllegalStateException("Judge question missing from selected revision")).caseWeights();
            }
            return new Completed(ids[0],score(question,result,weights),ids[1]);
        }).filter(java.util.Objects::nonNull).toList();
    }
    @Transactional
    void applied(AssignmentAttemptEntity attempt, Completed result) {
        jdbc.update("UPDATE tbl_assignment_code_job SET applied_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE attempt_id=? AND job_id=? AND applied_at IS NULL",
                attempt.getId(),result.job());
    }
    static java.math.BigDecimal score(AssignmentSnapshotPolicy.Question question,JudgeGateway.EvaluationResult result) {
        return score(question,result,question.caseWeights());
    }
    private static java.math.BigDecimal score(AssignmentSnapshotPolicy.Question question,JudgeGateway.EvaluationResult result,
            java.util.List<java.math.BigDecimal> weights) {
        if(result.cases().isEmpty())return result.verdict()==JudgeGateway.Verdict.ACCEPTED?question.points():java.math.BigDecimal.ZERO;
        if(result.cases().size()!=result.total() || result.cases().stream().map(JudgeGateway.CaseResult::index).distinct().count()!=result.total())
            throw new IllegalStateException("Incomplete testcase verdicts cannot produce a weighted score");
        if(!weights.isEmpty() && weights.size()!=result.total())throw new IllegalStateException("Snapshot testcase weight count mismatch");
        java.math.BigDecimal maximum=java.math.BigDecimal.ZERO,earned=java.math.BigDecimal.ZERO;
        for(var testcase:result.cases()) {
            var weight=weights.isEmpty()?java.math.BigDecimal.ONE:weights.get(testcase.index());
            maximum=maximum.add(weight);
            if(testcase.verdict()==JudgeGateway.Verdict.ACCEPTED)earned=earned.add(weight);
        }
        if(maximum.signum()==0)return java.math.BigDecimal.ZERO;
        return question.points().multiply(earned).divide(maximum,java.math.MathContext.DECIMAL128);
    }
    @Transactional
    UUID evaluate(AssignmentAttemptEntity attempt, AssignmentSnapshotPolicy.Question question, String source, boolean run) {
        return enqueue(attempt,question,question,source,run,false,attempt.getRevisionId(),feedback(attempt));
    }

    @Transactional
    UUID rejudge(AssignmentAttemptEntity attempt, AssignmentSnapshotPolicy.Question original, AssignmentSnapshotPolicy.Question target,
            UUID revisionId, CodeFeedbackLevel feedback, String source) {
        return enqueue(attempt,original,target,source,false,true,revisionId,feedback.feedback());
    }

    private UUID enqueue(AssignmentAttemptEntity attempt, AssignmentSnapshotPolicy.Question question, AssignmentSnapshotPolicy.Question target,
            String source, boolean run, boolean rejudge, UUID revisionId, JudgeGateway.Feedback feedback) {
        var content=content(target);
        var cases=run?content.testCases().stream().filter(JudgeGateway.TestCaseSnapshot::sample).toList():content.testCases();
        var generation=run?0:jdbc.queryForObject("SELECT count(*)+1 FROM tbl_assignment_code_job WHERE attempt_id=? AND question_id=?",Long.class,attempt.getId(),question.id());
        var request=new JudgeGateway.CodeEvaluationRequest(attempt.getStudent().getId(),question.id(),attempt.getId(),source,
                JudgeGateway.Language.valueOf(question.codeLanguage().toUpperCase(java.util.Locale.ROOT)),cases,
                run?UUID.randomUUID().toString():attempt.getId()+":"+question.id(),generation,
                run?JudgeGateway.Feedback.FULL_DETAILS:feedback,JudgeGateway.SubmissionScope.CLASS_ASSIGNMENT,
                attempt.getAssignment().getId(),revisionId,content.limits());
        UUID job=run?judge.run(request):rejudge?judge.rejudge(request):judge.submit(request);
        if(!run)jdbc.update("INSERT INTO tbl_assignment_code_job(id,attempt_id,question_id,job_id,generation,revision_id) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(),attempt.getId(),question.id(),job,generation,revisionId);
        return job;
    }

    private JudgeGateway.Feedback feedback(AssignmentAttemptEntity attempt) {
        return attempt.getRevisionSnapshot() == null ? JudgeGateway.Feedback.PASS_FAIL
                : AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).codeFeedbackLevel().feedback();
    }

    JudgeGateway.JobReference reference(UUID student, UUID job) { return judge.jobReference(student, job); }

    JudgeGateway.EvaluationResult result(UUID student, UUID job, CodeFeedbackLevel currentPolicy) {
        var result = judge.result(student, job);
        var policy = currentPolicy == null ? JudgeGateway.Feedback.PASS_FAIL : currentPolicy.feedback();
        var cases = policy == JudgeGateway.Feedback.PASS_FAIL ? java.util.List.<JudgeGateway.CaseResult>of()
                : result.cases().stream().map(value -> policy == JudgeGateway.Feedback.TESTCASE_STATUS
                    ? new JudgeGateway.CaseResult(value.index(), value.verdict(), null, null, null) : value).toList();
        return new JudgeGateway.EvaluationResult(result.jobId(), result.generation(), result.verdict(), result.passed(), result.total(), cases);
    }
}
