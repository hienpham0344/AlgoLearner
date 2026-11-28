package com.algolearner.platform.problem;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ProblemRuntimeRoleTest {
    @Test
    void runtimeCanUseBackendTablesButCannotDdlTruncateOrChangeAudit() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String owner = "owner_" + suffix,
                runtime = "runtime_" + suffix,
                schema = "roles_" + suffix,
                untrusted = "client_" + suffix;
        try (var connection =
                DriverManager.getConnection(
                        System.getenv("TEST_DB_URL"),
                        System.getenv("TEST_DB_USERNAME"),
                        System.getenv("TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE ROLE " + owner + " NOSUPERUSER NOBYPASSRLS");
                sql.execute("CREATE ROLE " + runtime + " NOSUPERUSER NOBYPASSRLS");
                sql.execute("CREATE ROLE " + untrusted + " NOSUPERUSER NOBYPASSRLS");
                sql.execute("CREATE SCHEMA " + schema + " AUTHORIZATION " + owner);
                sql.execute("SET LOCAL ROLE " + owner);
                sql.execute("SET LOCAL search_path TO " + schema);
                sql.execute("CREATE TABLE tbl_problem_report(id integer PRIMARY KEY, status text)");
                sql.execute("CREATE TABLE tbl_audit_log(id bigserial PRIMARY KEY, action text)");
                String script =
                        Files.readString(Path.of("ops/provision-runtime-role.sql"))
                                .replace("algolearner_runtime", runtime)
                                .replace("BEGIN;", "")
                                .replace("COMMIT;", "");
                sql.execute(script);
                // Even an accidental SQL grant to a client role must not bypass RLS.
                sql.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + untrusted);
                sql.execute("GRANT SELECT,INSERT ON tbl_problem_report TO " + untrusted);
                sql.execute("SET LOCAL ROLE " + runtime);
                sql.execute("INSERT INTO tbl_problem_report VALUES(1,'OPEN')");
                sql.execute("UPDATE tbl_problem_report SET status='RESOLVED' WHERE id=1");
                try (var rows = sql.executeQuery("SELECT status FROM tbl_problem_report")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("RESOLVED");
                }
                sql.execute(
                        "INSERT INTO tbl_audit_log(action)"
                            + " VALUES('PROBLEM_REPORT_STATUS_CHANGED')");
                for (String forbidden :
                        new String[] {
                            "CREATE TABLE unexpected(id integer)",
                            "ALTER TABLE tbl_problem_report ADD COLUMN extra text",
                            "TRUNCATE tbl_problem_report",
                            "DELETE FROM tbl_audit_log",
                            "UPDATE tbl_audit_log SET action='tampered'"
                        }) {
                    var savepoint = connection.setSavepoint();
                    assertThatThrownBy(() -> sql.execute(forbidden))
                            .isInstanceOf(java.sql.SQLException.class);
                    connection.rollback(savepoint);
                }
                try (var rows =
                        sql.executeQuery(
                                "SELECT relforcerowsecurity FROM pg_class WHERE"
                                    + " oid='tbl_problem_report'::regclass")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                }
                sql.execute("SET LOCAL ROLE " + untrusted);
                try (var rows = sql.executeQuery("SELECT count(*) FROM tbl_problem_report")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isZero();
                }
                var savepoint = connection.setSavepoint();
                assertThatThrownBy(
                                () ->
                                        sql.execute(
                                                "INSERT INTO tbl_problem_report VALUES(2,'OPEN')"))
                        .isInstanceOf(java.sql.SQLException.class);
                connection.rollback(savepoint);
            } finally {
                connection.rollback();
            }
        }
    }
}
