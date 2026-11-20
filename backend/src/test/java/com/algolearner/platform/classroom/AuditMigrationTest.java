package com.algolearner.platform.classroom;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AuditMigrationTest {
    @ParameterizedTest @ValueSource(strings = {"migration", "main-auth"})
    void adoptsLegacyAuditWithoutLosingOriginalColumnsOrRows(String track) throws Exception {
        var schema = "test_audit_" + UUID.randomUUID().toString().replace("-", "");
        var id = UUID.randomUUID(); var actor = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(System.getenv("TEST_DB_URL"), System.getenv("TEST_DB_USERNAME"), System.getenv("TEST_DB_PASSWORD"));
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                sql.execute("CREATE TABLE tbl_account(id uuid primary key)");
                sql.execute("CREATE TABLE tbl_classroom(id uuid primary key)");
                sql.execute("INSERT INTO tbl_account VALUES ('" + actor + "')");
                sql.execute("CREATE TABLE tbl_audit_log(id uuid primary key,actor_account_id uuid references tbl_account(id),target_account_id uuid references tbl_account(id),actor_type varchar(20) not null,action varchar(100) not null,outcome varchar(20) not null,occurred_at timestamptz not null)");
                sql.execute("CREATE INDEX idx_audit_actor_time ON tbl_audit_log(actor_account_id,occurred_at)");
                sql.execute("ALTER TABLE tbl_audit_log ENABLE ROW LEVEL SECURITY");
                sql.execute("INSERT INTO tbl_audit_log VALUES ('" + id + "','" + actor + "',null,'USER','LEGACY_EVENT','REJECTED','2026-11-01T00:00:00Z')");
                try (var input = getClass().getResourceAsStream("/db/" + track + "/V202611180001__classroom_lock_version_and_audit.sql")) {
                    assertNotNull(input); sql.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                try (var rows = sql.executeQuery("select legacy_id,outcome,actor_id,action from tbl_audit_log")) {
                    assertTrue(rows.next()); assertEquals(id, rows.getObject(1)); assertEquals("REJECTED", rows.getString(2));
                    assertEquals(actor, rows.getObject(3)); assertEquals("LEGACY_EVENT", rows.getString(4)); assertFalse(rows.next());
                }
                try (var rows = sql.executeQuery("select id,actor_type,outcome from tbl_legacy_auth_audit_log")) {
                    assertTrue(rows.next()); assertEquals(id, rows.getObject(1)); assertEquals("USER", rows.getString(2)); assertEquals("REJECTED", rows.getString(3));
                }
                try (var rows = sql.executeQuery("select relrowsecurity from pg_class where oid='tbl_legacy_auth_audit_log'::regclass")) { assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); }
                sql.execute("insert into tbl_audit_log(actor_role,target_type,target_id,action) values('ADMIN','CLASSROOM','test','NEW_EVENT')");
            } finally { connection.setSchema("public"); sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
