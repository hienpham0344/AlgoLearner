package com.algolearner.platform.judge;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Explicit standalone main, never a Spring bean. API processes have no Docker executor. */
public final class JudgeWorkerMain {
    private JudgeWorkerMain() { }
    public static void main(String[] args) throws Exception {
        Map<String,String> env=System.getenv();
        var sandbox=new DockerSandbox(required(env,"JUDGE_JAVA_IMAGE"),required(env,"JUDGE_PYTHON_IMAGE"),Path.of(required(env,"JUDGE_WORK_DIR")));
        var ds=new DriverManagerDataSource(required(env,"JUDGE_DB_URL"),required(env,"JUDGE_DB_USERNAME"),required(env,"JUDGE_DB_PASSWORD"));
        var gateway=new JdbcJudgeGateway(new NamedParameterJdbcTemplate(ds),new ObjectMapper(),new TransactionTemplate(new DataSourceTransactionManager(ds)));
        while(!Thread.currentThread().isInterrupted()) {
            var lease=gateway.claim();
            if(lease.isEmpty()){Thread.sleep(500);continue;}
            try { gateway.complete(lease.get(),sandbox.evaluate(lease.get().id(),lease.get().request())); }
            catch(InterruptedException e){gateway.infrastructureFailure(lease.get());Thread.currentThread().interrupt();}
            catch(Exception e){
                // Stable failure code only: never log source, testcase input/output, DB URL or credentials.
                System.err.println("JUDGE_INFRASTRUCTURE_FAILURE job="+lease.get().id());
                gateway.infrastructureFailure(lease.get());
            }
        }
    }
    private static String required(Map<String,String> env,String key) {
        String value=env.get(key);if(value==null || value.isBlank())throw new IllegalArgumentException("Missing worker configuration: "+key);return value;
    }
}
