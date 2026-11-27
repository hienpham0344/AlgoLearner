package com.algolearner.platform.support;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class BackendMigrationVerificationTest {
    @ParameterizedTest
    @CsvSource({"migration,false", "main-auth,false", "migration,true", "main-auth,true"})
    void newAndUpgradedDatabasesValidateAndRepeatWithoutChangingLegacyData(String track,boolean upgrade) throws Exception {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[^?]*_test(?:\\?.*)?")) {
            throw new IllegalStateException("Dedicated test database required");
        }
        String user = System.getenv("TEST_DB_USERNAME"),password = System.getenv("TEST_DB_PASSWORD");
        if (user == null || password == null) throw new IllegalStateException("Explicit test database credentials required");
        String schema = "test_rollout_"+UUID.randomUUID().toString().replace("-","");
        UUID account = UUID.randomUUID(),classroom = UUID.randomUUID(),assignment = UUID.randomUUID(),attempt = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url,user,password); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA "+schema);
            try {
                if (upgrade) {
                    String legacyVersion = track.equals("main-auth") ? "202611150005" : "202611140001";
                    configuration(url,user,password,schema,track).target(legacyVersion).load().migrate();
                    connection.setSchema(schema);
                    seedLegacy(connection,account,classroom,assignment,attempt);
                }
                var flyway = configuration(url,user,password,schema,track).load();
                assertThat(flyway.migrate().migrationsExecuted).isPositive();
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                connection.setSchema(schema);
                try (var rows = statement.executeQuery("""
                        SELECT (SELECT count(*) FROM tbl_gradebook_publication) AS publications,
                               (SELECT count(*) FROM tbl_gradebook_student_result) AS results,
                               (SELECT count(*) FROM tbl_gradebook_history) AS history,
                               (SELECT count(*) FROM tbl_gradebook_refresh) AS refresh
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("publications")).isZero();
                    assertThat(rows.getLong("results")).isZero();
                    assertThat(rows.getLong("history")).isZero();
                    assertThat(rows.getLong("refresh")).isZero();
                }
                try (var rows = statement.executeQuery("""
                        SELECT classroom_id,metadata FROM tbl_audit_log WHERE false
                        UNION ALL
                        SELECT gradebook_classroom_id,to_jsonb(gradebook_result_id) FROM tbl_notification WHERE false
                        """)) {
                    assertThat(rows.next()).isFalse();
                }
                try (var rows = statement.executeQuery("""
                        SELECT g.classroom_id, g.active_scheme_revision_id, g.show_overall, g.version,
                               g.created_at, g.updated_at
                        FROM tbl_gradebook g
                        """)) {
                    assertThat(rows.next()).isEqualTo(upgrade);
                    if (upgrade) {
                        assertThat(rows.getObject("classroom_id", UUID.class)).isEqualTo(classroom);
                        assertThat(rows.getObject("active_scheme_revision_id")).isNull();
                        assertThat(rows.getBoolean("show_overall")).isFalse();
                        assertThat(rows.getLong("version")).isZero();
                        assertThat(rows.getTimestamp("created_at")).isNotNull();
                        assertThat(rows.getTimestamp("updated_at")).isNotNull();
                        assertThat(rows.next()).isFalse();
                    }
                }
                try (var rows = statement.executeQuery("""
                        SELECT (SELECT count(*) FROM tbl_grade_scheme_revision),
                               (SELECT count(*) FROM tbl_grade_scheme_item)
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isZero();
                    assertThat(rows.getLong(2)).isZero();
                }
                try (var rows = statement.executeQuery("SELECT relname FROM pg_class WHERE relnamespace=current_schema()::regnamespace AND relkind='r' AND relname LIKE 'tbl_%' AND NOT relrowsecurity")) {
                    assertThat(rows.next()).as("Application tables must keep RLS enabled").isFalse();
                }
                if (upgrade) {
                    try (var query = connection.prepareStatement("SELECT a.avatar_key,t.final_score,t.snapshot_provenance,t.revision_snapshot FROM tbl_account a JOIN tbl_assignment_attempt t ON t.student_id=a.id WHERE t.id=?")) {
                        query.setObject(1,attempt);
                        try (var rows = query.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getString(1)).isEqualTo(account+".png");
                            assertThat(rows.getBigDecimal(2)).isEqualByComparingTo(new BigDecimal("7.25"));
                            assertThat(rows.getString(3)).isEqualTo("LEGACY_MISSING");
                            assertThat(rows.getString(4)).isNull();
                            assertThat(rows.next()).isFalse();
                        }
                    }
                    try (var rows = statement.executeQuery("SELECT file_url,material_state,stored_file_id FROM tbl_lesson_material")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("https://legacy.example.test/file.pdf");
                        assertThat(rows.getString(2)).isEqualTo("NEEDS_REUPLOAD");
                        assertThat(rows.getObject(3)).isNull();
                        assertThat(rows.next()).isFalse();
                    }
                }
            } finally {
                connection.setSchema("public");
                statement.execute("DROP SCHEMA "+schema+" CASCADE");
            }
        }
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration configuration(String url,String user,String password,String schema,String track) {
        return Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/"+track);
    }

    private void seedLegacy(Connection connection,UUID account,UUID classroom,UUID assignment,UUID attempt) throws Exception {
        insert(connection,"INSERT INTO tbl_account(id,username,email,role,status,password_hash,avatar_key) VALUES (?,'legacy','legacy@example.test','TEACHER','ACTIVE','fixture',?)",account,account+".png");
        insert(connection,"INSERT INTO tbl_classroom(id,owner_id,name,status,class_code,join_mode) VALUES (?,?,'Legacy','ACTIVE','LEGACY','AUTO_JOIN')",classroom,account);
        insert(connection,"INSERT INTO tbl_assignment(id,classroom_id,title,status) VALUES (?,?,'Legacy assignment','PUBLISHED')",assignment,classroom);
        insert(connection,"INSERT INTO tbl_assignment_attempt(id,assignment_id,student_id,attempt_number,status,started_at,submitted_at,final_score) VALUES (?,?,?,1,'GRADED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,7.25)",attempt,assignment,account);
        UUID lesson = UUID.randomUUID();
        insert(connection,"INSERT INTO tbl_lesson(id,classroom_id,title) VALUES (?,?,'Legacy lesson')",lesson,classroom);
        insert(connection,"INSERT INTO tbl_lesson_material(id,lesson_id,file_name,file_url,file_type,file_size) VALUES (?,?,'legacy.pdf','https://legacy.example.test/file.pdf','PDF',100)",UUID.randomUUID(),lesson);
    }

    private void insert(Connection connection,String sql,Object... parameters) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index+1,parameters[index]);
            statement.executeUpdate();
        }
    }
}
