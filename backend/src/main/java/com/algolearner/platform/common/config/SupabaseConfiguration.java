package com.algolearner.platform.common.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("!test")
public class SupabaseConfiguration {
    @Bean
    static BeanFactoryPostProcessor requireSupabase(Environment environment) {
        return factory -> {
            if (Arrays.asList(environment.getActiveProfiles()).contains("mock")) {
                throw new IllegalStateException("Backend mock mode is only supported in isolated tests.");
            }
            validateUrl(environment.getProperty("spring.datasource.url", ""));
        };
    }
    static void validateUrl(String url) {
        try {
            if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI parsed = URI.create(url.substring(5));
            String host = parsed.getHost();
            if (host == null || !(host.endsWith(".supabase.co") || host.endsWith(".pooler.supabase.com"))
                    || parsed.getUserInfo() != null || parsed.getQuery() == null
                    || !parsed.getQuery().matches(".*(?:^|&)sslmode=(require|verify-ca|verify-full)(?:&.*|$)")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Configure a Supabase PostgreSQL JDBC URL with TLS in DB_URL. No local database fallback is allowed.");
        }
    }
}
