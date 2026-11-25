package com.algolearner.platform.common.config;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class SupabaseConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "jdbc:postgresql://localhost:5432/db", "jdbc:postgresql://db.example.test/db?sslmode=require", "jdbc:postgresql://db.project.supabase.co:5432/postgres", "jdbc:postgresql://user:secret@db.project.supabase.co/postgres?sslmode=require"})
    void refusesMissingLocalOrInsecureConnections(String url) {
        assertThrows(IllegalStateException.class, () -> SupabaseConfiguration.validateUrl(url));
    }
    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://db.project.supabase.co:5432/postgres?sslmode=require", "jdbc:postgresql://aws-0-region.pooler.supabase.com:5432/postgres?sslmode=verify-full"})
    void acceptsSupabaseWithTls(String url) { assertDoesNotThrow(() -> SupabaseConfiguration.validateUrl(url)); }
}
