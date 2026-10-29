package com.algolearner.platform.common;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.fail;

/** Explicit JDBC probe only: no Spring context, schema, repositories or writes. */
@EnabledIfSystemProperty(named = "supabase.check", matches = "true")
class SupabaseConnectionTest {
    @Test
    void selectOne() {
        String url = requiredEnvironment("DB_URL", "HOST");
        String username = requiredEnvironment("DB_USERNAME", "PROJECT_REF");
        String password = requiredEnvironment("DB_PASSWORD", "YOUR_DATABASE_PASSWORD");
        if (!url.startsWith("jdbc:postgresql://") || !url.matches(".*[?&]sslmode=(require|verify-ca|verify-full)(&.*)?$")) {
            fail("DB_URL must be a PostgreSQL JDBC URL with sslmode=require or certificate verification.");
        }
        var properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "10");
        // Driver messages can contain connection details; report only classified SQLState.
        Logger logger = Logger.getLogger("org.postgresql");
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
        try (var connection = DriverManager.getConnection(url, properties);
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var result = statement.executeQuery("SELECT 1;")) {
                if (!result.next() || result.getInt(1) != 1) {
                    fail("Supabase connection failed: SELECT 1 returned an unexpected result.");
                }
            }
            System.out.println("Supabase connection successful");
        } catch (SQLException exception) {
            String state = exception.getSQLState();
            String reason = state != null && state.startsWith("28") ? "authentication rejected"
                    : state != null && state.startsWith("08") ? "connection/network/TLS failure"
                    : "JDBC failure";
            String safeState = state != null && state.matches("[A-Z0-9]{5}") ? state : "unknown";
            fail("Supabase connection failed: " + reason + " (SQLState " + safeState
                    + "). Check credentials, project availability, DNS, network and TLS. Details hidden.");
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private static String requiredEnvironment(String name, String placeholder) {
        String value = System.getenv(name);
        if (value == null || value.isBlank() || value.contains(placeholder)) {
            fail("Missing or placeholder " + name + "; configure environment variables or backend/.env.");
        }
        return value;
    }
}
