package com.algolearner.platform.common.security;

import com.algolearner.platform.common.dto.ApiErrorResponse;
import com.algolearner.platform.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, AccountAccess accounts, ObjectMapper mapper) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/*/profile", "/api/v1/users/*/profile/avatar").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                try {
                    String role = accounts.activeRole(UUID.fromString(token.getSubject()));
                    return new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)), token.getSubject());
                } catch (ApiException | IllegalArgumentException ex) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Account access denied");
                }
            })).authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.writeValue(response.getOutputStream(), ApiErrorResponse.builder().errorCode("UNAUTHORIZED_ACCESS")
                        .message("Authentication is required").path(request.getRequestURI()).build());
            }))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), ApiErrorResponse.builder().errorCode("UNAUTHORIZED_ACCESS")
                            .message("Authentication is required").path(request.getRequestURI()).build());
                })
                .accessDeniedHandler((request, response, ex) -> {
                    response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), ApiErrorResponse.builder().errorCode("FORBIDDEN_OPERATION")
                            .message("Access denied").path(request.getRequestURI()).build());
                }));
        return http.build();
    }
}
