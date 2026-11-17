package com.algolearner.platform.quiz;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Explicit test database only; never reads the application's DB_URL or credentials. */
@EnabledIfEnvironmentVariable(named="QUIZ_TEST_POSTGRES_URL",matches="jdbc:postgresql:.*")
class QuizPostgresMigrationTest {
    @Test void migratesExistingModulesAndEnforcesQuizConstraints() throws Exception {
        String url=System.getenv("QUIZ_TEST_POSTGRES_URL");
        String user=System.getenv("QUIZ_TEST_POSTGRES_USERNAME");
        String password=System.getenv("QUIZ_TEST_POSTGRES_PASSWORD");
        String schema="quiz_test_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false).load();
        try {
            assertThat(flyway.migrate().migrationsExecuted).isGreaterThanOrEqualTo(5);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try(var c=DriverManager.getConnection(url,user,password)) {
                c.setSchema(schema);
                UUID teacher=UUID.randomUUID(),student=UUID.randomUUID(),classroom=UUID.randomUUID(),quiz=UUID.randomUUID();
                execute(c,"insert into tbl_account(id,username,email,role,status) values(?,?,?,'TEACHER','ACTIVE')",teacher,"teacher","teacher@test.example");
                execute(c,"insert into tbl_account(id,username,email,role,status) values(?,?,?,'STUDENT','ACTIVE')",student,"student","student@test.example");
                execute(c,"insert into tbl_classroom(id,owner_id,name,status,class_code,join_mode) values(?,?,'Quiz','ACTIVE','QUIZTEST','AUTO_JOIN')",classroom,teacher);
                execute(c,"insert into tbl_quiz(id,classroom_id,title,configuration,status) values(?,?,'Quiz','{}','PUBLISHED')",quiz,classroom);
                String insert="insert into tbl_quiz_attempt(id,quiz_id,student_id,attempt_number,snapshot,answers,status,started_at) values(?,?,?,?, '{}','{}','ACTIVE',CURRENT_TIMESTAMP)";
                execute(c,insert,UUID.randomUUID(),quiz,student,1);
                assertThatThrownBy(()->execute(c,insert,UUID.randomUUID(),quiz,student,2))
                        .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("23505");
                execute(c,"update tbl_quiz_attempt set status='EXPIRED' where quiz_id=?",quiz);
                execute(c,insert,UUID.randomUUID(),quiz,student,2);
                assertThatThrownBy(()->execute(c,insert,UUID.randomUUID(),quiz,student,1))
                        .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("23505");
                assertThatThrownBy(()->execute(c,"update tbl_quiz_attempt set status='SUBMITTED' where quiz_id=?",quiz))
                        .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("23514");
                assertThatThrownBy(()->execute(c,"delete from tbl_classroom where id=?",classroom))
                        .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("23503");
                String grade="insert into tbl_quiz_grade_override(id,quiz_id,student_id,actor_id,original_score,final_score,revision,reason) values(?,?,?,?,0,75,1,'Review')";
                execute(c,grade,UUID.randomUUID(),quiz,student,teacher);
                assertThatThrownBy(()->execute(c,grade,UUID.randomUUID(),quiz,student,teacher))
                        .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("23505");
                assertThatThrownBy(()->execute(c,"update tbl_quiz_grade_override set final_score=101 where quiz_id=?",quiz))
                        .isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("23514");
            }
        } finally {
            // Flyway is scoped to the random schema created above; application schemas are excluded.
            flyway.clean();
        }
    }
    private static void execute(Connection c,String sql,Object... args) throws SQLException {
        try(var statement=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            statement.executeUpdate();
        }
    }
}
