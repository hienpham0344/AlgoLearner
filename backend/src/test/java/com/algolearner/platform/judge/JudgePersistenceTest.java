package com.algolearner.platform.judge;

import static org.assertj.core.api.Assertions.*;
import static com.algolearner.platform.judge.JudgeGateway.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches="jdbc:postgresql:.*")
class JudgePersistenceTest {
    @Test void submitIsIdempotentRunIsSeparateAndSnapshotsAreImmutable() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("TEST_DB_URL"), System.getenv("TEST_DB_USERNAME"), System.getenv("TEST_DB_PASSWORD"));
        String schema = "judge_test_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new NamedParameterJdbcTemplate(ds);
        jdbc.getJdbcTemplate().execute("CREATE SCHEMA " + schema);
        try {
            ds.setConnectionProperties(properties(schema));
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_account(id UUID PRIMARY KEY)");
            // V180008 installs the FK for the assignment-owned table introduced by V180006.
            jdbc.getJdbcTemplate().execute("CREATE TABLE tbl_assignment_code_job(job_id UUID)");
            String migration = new String(getClass().getResourceAsStream("/db/migration/V202611180008__persistent_isolated_judge.sql").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            jdbc.getJdbcTemplate().execute(migration);
            UUID owner=UUID.randomUUID(); jdbc.update("INSERT INTO tbl_account VALUES(:id)",Map.of("id",owner));
            var gateway = new JdbcJudgeGateway(jdbc, new com.fasterxml.jackson.databind.ObjectMapper(), new TransactionTemplate(new DataSourceTransactionManager(ds)));
            var request = new CodeEvaluationRequest(owner,null,null,"print(input())",Language.PYTHON,
                    List.of(new TestCaseSnapshot("a","a",true,false),new TestCaseSnapshot("secret","secret",false,false)),
                    "request",0,Feedback.PASS_FAIL,SubmissionScope.PUBLIC_PROBLEM,UUID.randomUUID(),UUID.randomUUID(),new Limits(10,2000,65536));
            UUID job=gateway.submit(request); assertThat(gateway.submit(request)).isEqualTo(job);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_submission",Map.of(),Long.class)).isEqualTo(1);
            gateway.run(request);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_submission",Map.of(),Long.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("UPDATE tbl_submission SET source='changed'",Map.of())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> gateway.result(UUID.randomUUID(),job)).isInstanceOf(RuntimeException.class);
            var lease=gateway.claim().orElseThrow();
            assertThat(gateway.complete(new JdbcJudgeGateway.Lease(lease.id(),UUID.randomUUID(),lease.attempts(),lease.request()),
                    new EvaluationResult(lease.id(),lease.request().generation(),Verdict.ACCEPTED,1,1,List.of()))).isFalse();
            assertThat(gateway.complete(lease,new EvaluationResult(lease.id(),lease.request().generation()+1,Verdict.ACCEPTED,1,1,List.of()))).isFalse();
            assertThat(gateway.complete(lease,new EvaluationResult(lease.id(),lease.request().generation(),Verdict.ACCEPTED,1,1,List.of()))).isTrue();
            assertThat(gateway.complete(lease,new EvaluationResult(lease.id(),lease.request().generation(),Verdict.WRONG_ANSWER,0,1,List.of()))).isFalse();
            var retry=gateway.claim().orElseThrow();
            gateway.infrastructureFailure(retry);
            assertThat(jdbc.queryForObject("SELECT last_error FROM tbl_judge_job WHERE id=:id",Map.of("id",retry.id()),String.class)).isEqualTo("WORKER_INFRASTRUCTURE_FAILURE");

            jdbc.update("UPDATE tbl_judge_job SET attempts=2,status='QUEUED',available_at=clock_timestamp()-INTERVAL '1 second' WHERE id=:id",Map.of("id",retry.id()));
            var exhausted=gateway.claim().orElseThrow();gateway.infrastructureFailure(exhausted);
            assertThat(gateway.result(owner,exhausted.id()).verdict()).isEqualTo(Verdict.INFRASTRUCTURE_ERROR);
            jdbc.getJdbcTemplate().execute("CREATE FUNCTION reject_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END $$");
            jdbc.getJdbcTemplate().execute("CREATE TRIGGER reject_job BEFORE INSERT ON tbl_judge_job FOR EACH ROW EXECUTE FUNCTION reject_job()");
            var rollbackRequest=new CodeEvaluationRequest(owner,null,null,"pass",Language.PYTHON,request.testCases(),"rollback",0,Feedback.PASS_FAIL,request.scope(),request.resourceId(),request.revisionId(),request.limits());
            assertThatThrownBy(()->gateway.submit(rollbackRequest)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_submission",Map.of(),Long.class)).isEqualTo(1);
            jdbc.getJdbcTemplate().execute("DROP TRIGGER reject_job ON tbl_judge_job");
            for(int i=0;i<4;i++) gateway.run(new CodeEvaluationRequest(owner,null,null,"pass",Language.PYTHON,request.testCases(),"run"+i,0,Feedback.PASS_FAIL,request.scope(),request.resourceId(),request.revisionId(),request.limits()));
            assertThatThrownBy(() -> gateway.run(new CodeEvaluationRequest(owner,null,null,"pass",Language.PYTHON,request.testCases(),"overflow",0,Feedback.PASS_FAIL,request.scope(),request.resourceId(),request.revisionId(),request.limits()))).isInstanceOf(RuntimeException.class);
            jdbc.update("UPDATE tbl_judge_job SET status='ACCEPTED'", Map.of());
            UUID last = null;
            CodeEvaluationRequest lastRequest = null;
            for (int i=0;i<5;i++) {
                lastRequest = new CodeEvaluationRequest(owner,null,null,"pass",Language.PYTHON,request.testCases(),"submit"+i,0,Feedback.PASS_FAIL,request.scope(),request.resourceId(),request.revisionId(),request.limits());
                last = gateway.submit(lastRequest);
            }
            assertThat(gateway.submit(lastRequest)).isEqualTo(last);
            var overflow = new CodeEvaluationRequest(owner,null,null,"pass",Language.PYTHON,request.testCases(),"submit-overflow",0,Feedback.PASS_FAIL,request.scope(),request.resourceId(),request.revisionId(),request.limits());
            assertThatThrownBy(() -> gateway.submit(overflow)).isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
            jdbc.update("UPDATE tbl_judge_job SET status='ACCEPTED' WHERE id=:id",Map.of("id",last));
            assertThat(gateway.submit(overflow)).isNotNull();
            UUID concurrentOwner=UUID.randomUUID();
            jdbc.update("INSERT INTO tbl_account VALUES(:id)",Map.of("id",concurrentOwner));
            var start=new java.util.concurrent.CountDownLatch(1);
            try (var pool=java.util.concurrent.Executors.newFixedThreadPool(10)) {
                var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
                for (int i=0;i<10;i++) {
                    String key="concurrent"+i;
                    futures.add(pool.submit(() -> {
                        start.await();
                        try {
                            gateway.submit(new CodeEvaluationRequest(concurrentOwner,null,null,"pass",Language.PYTHON,request.testCases(),key,0,Feedback.PASS_FAIL,request.scope(),request.resourceId(),request.revisionId(),request.limits()));
                            return true;
                        } catch (com.algolearner.platform.common.exception.AppException rejected) { return false; }
                    }));
                }
                start.countDown();
                int admitted=0;
                for (var future : futures) if (future.get(20,java.util.concurrent.TimeUnit.SECONDS)) admitted++;
                assertThat(admitted).isEqualTo(5);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_submission WHERE owner_id=:id",Map.of("id",concurrentOwner),Long.class)).isEqualTo(5);
            }
        } finally { ds.setConnectionProperties(new java.util.Properties()); jdbc.getJdbcTemplate().execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
    private java.util.Properties properties(String schema) { var p=new java.util.Properties();p.setProperty("currentSchema",schema); return p; }
}
