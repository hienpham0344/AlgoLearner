package com.algolearner.platform.assignment;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AssignmentRevisionMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"migration", "main-auth"})
    void legacyGradesRemainExactAndSnapshotsCannotBeRewritten(String track) throws Exception {
        String url = System.getenv("TEST_DB_URL"), user = System.getenv("TEST_DB_USERNAME"), password = System.getenv("TEST_DB_PASSWORD");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[^?]*_test(?:\\?.*)?"))
            throw new IllegalStateException("Dedicated test database required");
        String schema = "test_revision_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                migrate(url, user, password, schema, track, "202611180004");
                connection.setSchema(schema);
                UUID owner = UUID.randomUUID(), classroom = UUID.randomUUID(), assignment = UUID.randomUUID(), attempt = UUID.randomUUID();
                sql.execute("INSERT INTO tbl_account(id,username,email,role,status,password_hash) VALUES ('" + owner + "','legacy','legacy@test.dev','TEACHER','ACTIVE','fixture')");
                sql.execute("INSERT INTO tbl_classroom(id,owner_id,name,status,class_code,join_mode) VALUES ('" + classroom + "','" + owner + "','Legacy','ACTIVE','LEGACY','AUTO_JOIN')");
                sql.execute("INSERT INTO tbl_assignment(id,classroom_id,title,status) VALUES ('" + assignment + "','" + classroom + "','Legacy assignment','PUBLISHED')");
                sql.execute("INSERT INTO tbl_assignment_attempt(id,assignment_id,student_id,attempt_number,status,started_at,submitted_at,final_score) VALUES ('" + attempt + "','" + assignment + "','" + owner + "',1,'GRADED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,7.25)");
                migrate(url, user, password, schema, track, "202611180005");
                try (var rows = sql.executeQuery("SELECT final_score,snapshot_provenance,revision_id,revision_snapshot FROM tbl_assignment_attempt WHERE id='" + attempt + "'")) {
                    assertTrue(rows.next()); assertEquals("7.25", rows.getBigDecimal(1).toPlainString());
                    assertEquals("LEGACY_MISSING", rows.getString(2)); assertNull(rows.getObject(3)); assertNull(rows.getString(4));
                }
                UUID revision = UUID.randomUUID();
                sql.execute("INSERT INTO tbl_assignment_revision(id,assignment_id,content,provenance) VALUES ('" + revision + "','" + assignment + "','{}','TRUSTED')");
                assertThrows(SQLException.class, () -> sql.execute("UPDATE tbl_assignment_revision SET content='{\"changed\":true}' WHERE id='" + revision + "'"));
                assertThrows(SQLException.class, () -> sql.execute("DELETE FROM tbl_assignment_revision WHERE id='" + revision + "'"));
                assertThrows(SQLException.class, () -> sql.execute("UPDATE tbl_assignment_attempt SET revision_snapshot='{}' WHERE id='" + attempt + "'"));
                try (var rows = sql.executeQuery("SELECT content FROM tbl_assignment_revision WHERE id='" + revision + "'")) {
                    assertTrue(rows.next()); assertEquals("{}", rows.getString(1));
                }
            } finally {
                connection.setSchema("public"); sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void migrate(String url, String user, String password, String schema, String track, String target) {
        Flyway.configure().dataSource(url, user, password).defaultSchema(schema).schemas(schema)
                .locations("classpath:db/" + track).target(target).load().migrate();
    }
}
