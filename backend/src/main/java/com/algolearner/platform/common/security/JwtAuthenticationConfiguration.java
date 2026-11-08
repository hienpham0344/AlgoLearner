package com.algolearner.platform.common.security;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/** Signed tokens are mandatory outside explicitly enabled local mock mode. */
@Configuration
@Profile("!mock | (!dev & !test) | prod | production")
public class JwtAuthenticationConfiguration {
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.security.jwt.issuer:}") String issuer,
            @Value("${app.security.jwt.audience:}") String audience,
            @Value("${app.security.jwt.jwk-set-uri:}") String jwkSetUri,
            Clock clock) {
        return createDecoder(issuer, audience, jwkSetUri, clock, new RestTemplate());
    }

    static NimbusJwtDecoder createDecoder(String issuer, String audience, String jwkSetUri,
            Clock clock, RestOperations restOperations) {
        requireHttps(issuer, "JWT issuer");
        requireHttps(jwkSetUri, "JWT JWK set URI");
        Assert.hasText(audience, "JWT audience is required");
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
            .restOperations(restOperations)
            .jwsAlgorithms(algorithms -> {
                algorithms.clear();
                algorithms.add(SignatureAlgorithm.RS256);
                algorithms.add(SignatureAlgorithm.ES256);
            }).build();
        decoder.setJwtValidator(jwt -> {
            Instant now = clock.instant();
            String subject = jwt.getSubject();
            boolean validSubject = false;
            try {
                validSubject = subject != null && UUID.fromString(subject).toString().equalsIgnoreCase(subject);
            } catch (IllegalArgumentException ignored) {
                // UUID.fromString also accepts abbreviated UUIDs; require canonical form above.
            }
            if (!issuer.equals(jwt.getClaimAsString("iss")) || jwt.getAudience() == null || !jwt.getAudience().contains(audience)
                    || !validSubject || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                    || !jwt.getExpiresAt().isAfter(now) || jwt.getIssuedAt().isAfter(now)
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "JWT claims are invalid", null));
            }
            return OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }

    private static void requireHttps(String value, String name) {
        Assert.hasText(value, name + " is required");
        URI uri = URI.create(value);
        Assert.isTrue("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getFragment() == null,
                name + " must be an absolute HTTPS URL without credentials or fragment");
    }
}
