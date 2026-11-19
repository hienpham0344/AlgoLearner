package com.algolearner.platform.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Explicit test database only; subclasses must opt in with EnabledIfEnvironmentVariable. */
public abstract class PostgresIntegrationTest {
    private static final String SCHEMA = "test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean prepared;

    @DynamicPropertySource
    static synchronized void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        String username = System.getenv("TEST_DB_USERNAME");
        String password = System.getenv("TEST_DB_PASSWORD");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[^?]*_test(?:\\?.*)?")) {
            throw new IllegalStateException("TEST_DB_URL must name a dedicated database ending in _test.");
        }
        if (username == null || password == null) throw new IllegalStateException("Set TEST_DB_USERNAME and TEST_DB_PASSWORD.");
        var properties = new Properties();
        properties.setProperty("user", username); properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "10");
        if (!prepared) {
            execute(url, properties, "CREATE SCHEMA " + SCHEMA);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> execute(url, properties, "DROP SCHEMA " + SCHEMA + " CASCADE")));
            prepared = true;
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    private static void execute(String url, Properties properties, String sql) {
        try (var connection = DriverManager.getConnection(url, properties); var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException error) {
            throw new IllegalStateException("Cannot prepare or clean isolated PostgreSQL test schema (details hidden).");
        }
    }
}
