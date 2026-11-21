package com.algolearner.platform.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AccountIdentityMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"migration", "main-auth"})
    void preflightRejectsSameNamespaceCaseCollisionWithoutChangingData(String track) throws Exception {
        verifyMigration(track, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"migration", "main-auth"})
    void preservesLegacyCrossNamespaceIdentifiersAndAddsUniqueIndexes(String track) throws Exception {
        verifyMigration(track, false);
    }

    private void verifyMigration(String track, boolean collision) throws Exception {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[^?]*_test(?:\\?.*)?"))
            throw new IllegalStateException("Dedicated test database required.");
        String schema = "test_identity_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, System.getenv("TEST_DB_USERNAME"), System.getenv("TEST_DB_PASSWORD"));
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                sql.execute("CREATE TABLE tbl_account (email text NOT NULL, username text NOT NULL)");
                sql.execute("INSERT INTO tbl_account VALUES ('shared@test.dev', 'first'), ('other@test.dev', '"
                        + (collision ? "FIRST" : "shared@test.dev") + "')");
                String path = "/db/" + track + "/V202611180002__identity_namespace_safety.sql";
                String migration;
                try (var resource = getClass().getResourceAsStream(path)) {
                    assertNotNull(resource);
                    migration = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (collision) {
                    var failure = assertThrows(SQLException.class, () -> sql.execute(migration));
                    assertTrue(failure.getMessage().contains("explicit operator reconciliation"));
                    assertFalse(failure.getMessage().contains("shared@test.dev"));
                } else {
                    sql.execute(migration);
                    assertThrows(SQLException.class, () -> sql.execute("INSERT INTO tbl_account VALUES ('SHARED@test.dev', 'third')"));
                    assertThrows(SQLException.class, () -> sql.execute("INSERT INTO tbl_account VALUES ('third@test.dev', 'FIRST')"));
                }
                try (var rows = sql.executeQuery("SELECT count(*) FROM tbl_account")) {
                    assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
                }
            } finally {
                connection.setSchema("public");
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
