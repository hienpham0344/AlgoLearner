package com.algolearner.platform.common.security;

import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.common.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, AccountRepository accounts, ObjectMapper mapper) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll().anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, mapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED_ACCESS", "Authentication is required", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, mapper, HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", "Access denied", request.getRequestURI())))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    try {
                        var account = accounts.findById(UUID.fromString(token.getSubject())).orElseThrow();
                        if (account.getStatus() != AccountStatus.ACTIVE) throw new IllegalStateException();
                        return new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())), token.getSubject());
                    } catch (RuntimeException ex) {
                        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Account is not active");
                    }
                })));
        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper mapper, HttpStatus status,
                                   String code, String message, String path) throws java.io.IOException {
        response.setStatus(status.value()); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiErrorResponse.builder().errorCode(code).message(message).path(path).build());
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String secret) {
        if (secret.length() < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")).build();
    }
}
