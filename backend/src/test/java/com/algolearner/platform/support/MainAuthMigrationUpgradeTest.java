package com.algolearner.platform.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that a database with main's original auth history can adopt internal auth. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class MainAuthMigrationUpgradeTest {

    @Test
    void preservesLegacyRowsWhileUpgradingToTheInternalAuthSchema() throws Exception {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[^?]*_test(?:\\?.*)?")) {
            throw new IllegalStateException("TEST_DB_URL must name a dedicated database ending in _test.");
        }
        String user = System.getenv("TEST_DB_USERNAME");
        String password = System.getenv("TEST_DB_PASSWORD");
        if (user == null || password == null) {
            throw new IllegalStateException("Set TEST_DB_USERNAME and TEST_DB_PASSWORD.");
        }

        String schema = "test_main_auth_" + UUID.randomUUID().toString().replace("-", "");
        UUID accountId = UUID.randomUUID();
        UUID otpId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            try {
                migrate(url, user, password, schema, "202611100002");
                connection.setSchema(schema);
                try (var insert = connection.prepareStatement("""
                        INSERT INTO tbl_account (id, username, email, role, status, password_hash, failed_login_count)
                        VALUES (?, ?, ?, 'STUDENT', 'ACTIVE', 'legacy-hash', 3)
                        """)) {
                    insert.setObject(1, accountId);
                    insert.setString(2, "legacy_" + accountId.toString().substring(0, 8));
                    insert.setString(3, accountId + "@example.test");
                    insert.executeUpdate();
                }
                try (var insert = connection.prepareStatement("""
                        INSERT INTO tbl_otp_challenge
                        (id, account_id, purpose, code_hash, sent_at, expires_at)
                        VALUES (?, ?, 'REGISTER', 'legacy-otp-hash', ?, ?)
                        """)) {
                    insert.setObject(1, otpId);
                    insert.setObject(2, accountId);
                    insert.setTimestamp(3, Timestamp.from(Instant.now()));
                    insert.setTimestamp(4, Timestamp.from(Instant.now().plusSeconds(600)));
                    insert.executeUpdate();
                }
                try (var insert = connection.prepareStatement("""
                        INSERT INTO tbl_auth_session
                        (id, account_id, refresh_token_hash, expires_at)
                        VALUES (?, ?, 'legacy-refresh-hash', ?)
                        """)) {
                    insert.setObject(1, sessionId);
                    insert.setObject(2, accountId);
                    insert.setTimestamp(3, Timestamp.from(Instant.now().plusSeconds(3600)));
                    insert.executeUpdate();
                }
                try (var insert = connection.prepareStatement("""
                        INSERT INTO tbl_account_restriction
                        (id, account_id, kind, starts_at, ends_at)
                        VALUES (?, ?, 'AUTO_LOCK', ?, ?)
                        """)) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, accountId);
                    insert.setTimestamp(3, Timestamp.from(Instant.now()));
                    insert.setTimestamp(4, Timestamp.from(Instant.now().plusSeconds(900)));
                    insert.executeUpdate();
                }
                try (var insert = connection.prepareStatement("""
                        INSERT INTO tbl_audit_log
                        (id, actor_account_id, target_account_id, actor_type, action, outcome)
                        VALUES (?, ?, ?, 'USER', 'ACCOUNT_LOCK', 'SUCCESS')
                        """)) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, accountId);
                    insert.setObject(3, accountId);
                    insert.executeUpdate();
                }

                migrate(url, user, password, schema, null);
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("""
                             SELECT a.failed_login_attempts, a.login_locked_until,
                                    o.purpose, s.refresh_token_hash, s.refresh_hash, s.revoked_at,
                                    (SELECT count(*) FROM tbl_account_audit_log WHERE target_id = a.id) AS audit_count
                             FROM tbl_account a
                             JOIN tbl_registration_otp o ON o.account_id = a.id
                             JOIN tbl_auth_session s ON s.account_id = a.id
                             WHERE a.id = '%s' AND o.id = '%s' AND s.id = '%s'
                             """.formatted(accountId, otpId, sessionId))) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt("failed_login_attempts")).isEqualTo(3);
                    assertThat(rows.getTimestamp("login_locked_until")).isNotNull();
                    assertThat(rows.getString("purpose")).isEqualTo("REGISTER");
                    assertThat(rows.getString("refresh_token_hash")).isEqualTo("legacy-refresh-hash");
                    assertThat(rows.getString("refresh_hash")).hasSize(64);
                    assertThat(rows.getTimestamp("revoked_at")).isNotNull();
                    assertThat(rows.getLong("audit_count")).isEqualTo(1);
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        }
    }

    private void migrate(String url, String user, String password, String schema, String target) {
        var config = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/main-auth").schemas(schema).defaultSchema(schema);
        if (target != null) config.target(target);
        var flyway = config.load();
        flyway.migrate();
        flyway.validate();
    }
}
