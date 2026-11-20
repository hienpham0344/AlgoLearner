package com.algolearner.platform.judge;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import com.algolearner.platform.common.exception.AppException;

/** No process execution exists in this API-side adapter. A separate worker leases persisted jobs. */
@Service
@Profile("!mock")
public class JdbcJudgeGateway implements JudgeGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    @Autowired
    public JdbcJudgeGateway(NamedParameterJdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager) {
        this(jdbc,json,new TransactionTemplate(manager));
    }
    public JdbcJudgeGateway(NamedParameterJdbcTemplate jdbc,ObjectMapper json,TransactionTemplate tx) {
        this.jdbc=jdbc;this.json=json;this.tx=tx;
    }
    @Override public UUID submit(CodeEvaluationRequest r) { return enqueue(r,false,false); }
    @Override public UUID run(CodeEvaluationRequest r) { return enqueue(r,true,false); }
    @Override public UUID rejudge(CodeEvaluationRequest r) { return enqueue(r,false,true); }
    private UUID enqueue(CodeEvaluationRequest original,boolean run,boolean rejudge) {
        JudgePolicy.validate(original);
        var r=run ? new CodeEvaluationRequest(original.ownerId(),original.questionId(),original.attemptId(),original.source(),original.language(),
                original.testCases().stream().filter(TestCaseSnapshot::sample).toList(),original.requestKey(),original.generation(),original.feedback(),
                original.scope(),original.resourceId(),original.revisionId(),original.limits()) : original;
        if(r.testCases().isEmpty()) throw new IllegalArgumentException("RUN requires sample testcases");
        return tx.execute(status -> {
            // Serialize each owner's admission and rolling RUN quota atomically across API instances.
            var locks=jdbc.queryForList("SELECT id FROM tbl_account WHERE id=:owner FOR UPDATE",Map.of("owner",r.ownerId()));
            if(locks.isEmpty()) throw failure(HttpStatus.NOT_FOUND,"JUDGE_OWNER_NOT_FOUND");
            var params=new HashMap<String,Object>();params.put("owner",r.ownerId());params.put("key",r.requestKey());
            params.put("generation",r.generation());params.put("mode",run?"RUN":"SUBMIT");
            var existing=jdbc.queryForList("SELECT id,payload FROM tbl_judge_job WHERE owner_id=:owner AND request_key=:key AND generation=:generation AND mode=:mode",params);
            if(!existing.isEmpty()) {
                var previous=decode((String)existing.getFirst().get("payload"),CodeEvaluationRequest.class);
                if(!previous.equals(r)) throw failure(HttpStatus.CONFLICT,"JUDGE_IDEMPOTENCY_CONFLICT");
                return (UUID)existing.getFirst().get("id");
            }
            if(run && jdbc.queryForObject("SELECT COUNT(*) FROM tbl_judge_job WHERE owner_id=:owner AND mode='RUN' AND created_at>clock_timestamp()-INTERVAL '60 seconds'",params,Long.class)>=5)
                throw failure(HttpStatus.TOO_MANY_REQUESTS,"JUDGE_RUN_RATE_LIMIT");
            UUID submission = null;
            boolean newSubmission = false;
            if (!run) {
                var previousJobs = jdbc.queryForList("SELECT submission_id,payload FROM tbl_judge_job WHERE owner_id=:owner AND request_key=:key AND mode='SUBMIT' ORDER BY generation LIMIT 1",params);
                if (previousJobs.isEmpty()) {
                    if(rejudge)throw failure(HttpStatus.NOT_FOUND,"JUDGE_SUBMISSION_NOT_FOUND");
                    submission = UUID.randomUUID();
                    newSubmission = true;
                } else {
                    var previous = decode((String)previousJobs.getFirst().get("payload"),CodeEvaluationRequest.class);
                    var sameGeneration = new CodeEvaluationRequest(r.ownerId(),r.questionId(),r.attemptId(),r.source(),r.language(),
                            r.testCases(),r.requestKey(),previous.generation(),r.feedback(),r.scope(),r.resourceId(),r.revisionId(),r.limits());
                    if(rejudge) {
                        if(!sameSubmissionIdentity(previous,r))throw failure(HttpStatus.CONFLICT,"JUDGE_IDEMPOTENCY_CONFLICT");
                        long latest=jdbc.queryForObject("SELECT MAX(generation) FROM tbl_judge_job WHERE owner_id=:owner AND request_key=:key AND mode='SUBMIT'",params,Long.class);
                        if(r.generation()<=latest)throw failure(HttpStatus.CONFLICT,"JUDGE_STALE_GENERATION");
                    } else if (!previous.equals(sameGeneration)) throw failure(HttpStatus.CONFLICT,"JUDGE_IDEMPOTENCY_CONFLICT");
                    submission = (UUID)previousJobs.getFirst().get("submission_id");
                }
            }
            UUID job=UUID.randomUUID();
            params.put("job",job);params.put("submission",submission);params.put("payload",encode(r));
            if(newSubmission) {
                params.put("scope",r.scope().name());params.put("resource",r.resourceId());params.put("question",r.questionId());
                params.put("attempt",r.attemptId());params.put("revision",r.revisionId());params.put("language",r.language().name());
                params.put("source",r.source());params.put("cases",encode(r.testCases()));
                jdbc.update("INSERT INTO tbl_submission(id,owner_id,scope,resource_id,question_id,attempt_id,revision_id,language,source,testcase_snapshot) VALUES(:submission,:owner,:scope,:resource,:question,:attempt,:revision,:language,:source,:cases)",params);
            }
            jdbc.update("INSERT INTO tbl_judge_job(id,owner_id,submission_id,request_key,generation,mode,payload) VALUES(:job,:owner,:submission,:key,:generation,:mode,:payload)",params);
            return job;
        });
    }
    private static boolean sameSubmissionIdentity(CodeEvaluationRequest original,CodeEvaluationRequest revised) {
        return original.ownerId().equals(revised.ownerId()) && Objects.equals(original.questionId(),revised.questionId())
                && Objects.equals(original.attemptId(),revised.attemptId()) && original.scope()==revised.scope()
                && original.resourceId().equals(revised.resourceId()) && original.requestKey().equals(revised.requestKey())
                && original.language()==revised.language() && original.source().equals(revised.source());
    }
    @Override public EvaluationResult result(UUID requester,UUID id) {
        var row = ownedJob(requester,id);
        var result = evaluation(row,id);
        var request = decode((String)row.get("payload"),CodeEvaluationRequest.class);
        if (request.feedback() == Feedback.PASS_FAIL) {
            return new EvaluationResult(id,result.generation(),result.verdict(),result.passed(),result.total(),List.of());
        }
        var cases = result.cases().stream().filter(value -> value.index() >= 0 && value.index() < request.testCases().size())
                .map(value -> JudgePolicy.visibleCase(value.index(),request.testCases().get(value.index()),value.verdict(),value.actualOutput(),request.feedback()))
                .filter(Objects::nonNull).toList();
        return new EvaluationResult(id,result.generation(),result.verdict(),result.passed(),result.total(),cases);
    }

    @Override public EvaluationResult scoringResult(UUID owner,UUID id) {
        var result = evaluation(ownedJob(owner,id),id);
        var cases = result.cases().stream().map(value -> new CaseResult(value.index(),value.verdict(),null,null,null)).toList();
        return new EvaluationResult(id,result.generation(),result.verdict(),result.passed(),result.total(),cases);
    }

    @Override public JobReference jobReference(UUID owner,UUID id) {
        var row=ownedJob(owner,id);
        var request=decode((String)row.get("payload"),CodeEvaluationRequest.class);
        return new JobReference(id,request.ownerId(),request.attemptId(),request.questionId(),request.scope(),
                request.resourceId(),request.revisionId(),"RUN".equals(row.get("mode")),((Number)row.get("generation")).longValue());
    }

    private Map<String,Object> ownedJob(UUID owner,UUID id) {
        var rows=jdbc.queryForList("SELECT result,generation,status,payload,mode FROM tbl_judge_job WHERE id=:id AND owner_id=:owner",Map.of("id",id,"owner",owner));
        if(rows.isEmpty()) throw failure(HttpStatus.NOT_FOUND,"JUDGE_JOB_NOT_FOUND");
        return rows.getFirst();
    }

    private EvaluationResult evaluation(Map<String,Object> row,UUID id) {
        return row.get("result")==null ? new EvaluationResult(id,((Number)row.get("generation")).longValue(),Verdict.valueOf((String)row.get("status")),0,0,List.of())
                : decode((String)row.get("result"),EvaluationResult.class);
    }
    record Lease(UUID id,UUID token,int attempts,CodeEvaluationRequest request) { }
    Optional<Lease> claim() {
        return tx.execute(status -> {
            jdbc.update("DELETE FROM tbl_judge_job WHERE mode='RUN' AND created_at<clock_timestamp()-INTERVAL '24 hours' AND status<>'RUNNING'",Map.of());
            // Expired leases are infrastructure failures. Never allow unlimited poisoned-job cycling.
            jdbc.update("UPDATE tbl_judge_job SET status='INFRASTRUCTURE_ERROR',last_error='LEASE_EXHAUSTED',lease_token=NULL,lease_until=NULL,updated_at=clock_timestamp() WHERE status='RUNNING' AND lease_until<clock_timestamp() AND attempts>=3",Map.of());
            var rows=jdbc.queryForList("SELECT id,payload,attempts FROM tbl_judge_job WHERE ((status='QUEUED' AND available_at<=clock_timestamp()) OR (status='RUNNING' AND lease_until<clock_timestamp())) AND attempts<3 ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT 1",Map.of());
            if(rows.isEmpty()) return Optional.empty();
            var row=rows.getFirst();UUID id=(UUID)row.get("id"),token=UUID.randomUUID();
            var request=decode((String)row.get("payload"),CodeEvaluationRequest.class);
            // Lease exceeds the sum of bounded compile and all testcase execution budgets.
            int leaseSeconds=30+request.limits().compileSeconds()+request.testCases().size()*(2+(request.limits().runtimeMillis()+999)/1000);
            jdbc.update("UPDATE tbl_judge_job SET status='RUNNING',attempts=attempts+1,lease_token=:token,lease_until=clock_timestamp()+make_interval(secs=>:seconds),updated_at=clock_timestamp() WHERE id=:id",Map.of("id",id,"token",token,"seconds",leaseSeconds));
            return Optional.of(new Lease(id,token,((Number)row.get("attempts")).intValue()+1,request));
        });
    }
    boolean complete(Lease lease,EvaluationResult result) {
        if(!result.jobId().equals(lease.id()) || result.generation()!=lease.request().generation()) return false;
        return jdbc.update("UPDATE tbl_judge_job SET status=:status,result=:result,lease_token=NULL,lease_until=NULL,updated_at=clock_timestamp() WHERE id=:id AND lease_token=:token AND status='RUNNING' AND lease_until>clock_timestamp() AND generation=:generation",
                Map.of("status",result.verdict().name(),"result",encode(result),"id",lease.id(),"token",lease.token(),"generation",result.generation()))==1;
    }
    void infrastructureFailure(Lease lease) {
        jdbc.update("UPDATE tbl_judge_job SET status=:status,last_error='WORKER_INFRASTRUCTURE_FAILURE',available_at=clock_timestamp()+make_interval(secs=>:backoff),lease_token=NULL,lease_until=NULL,updated_at=clock_timestamp() WHERE id=:id AND lease_token=:token AND status='RUNNING'",
                Map.of("status",lease.attempts()>=3?"INFRASTRUCTURE_ERROR":"QUEUED","backoff",1<<lease.attempts(),"id",lease.id(),"token",lease.token()));
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Judge snapshot serialization failed",e);}}
    private <T>T decode(String text,Class<T> type) {try{return json.readValue(text,type);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid persisted judge snapshot",e);}}
    private static AppException failure(HttpStatus status,String code) {return new AppException("Judge request rejected",status,code) { };}
}
