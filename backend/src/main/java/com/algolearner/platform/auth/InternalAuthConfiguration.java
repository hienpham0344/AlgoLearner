package com.algolearner.platform.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@Profile("internal-auth")
public class InternalAuthConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean JwtEncoder jwtEncoder(@Value("${app.security.jwt-secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(keyBytes(secret)));
    }

    @Bean JwtDecoder internalJwtDecoder(@Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.internal-issuer}") String issuer, Clock clock, AuthSessionRepository sessions) {
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(keyBytes(secret), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(jwt -> {
            var now = clock.instant();
            if (!issuer.equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                    || jwt.getAudience() == null || !jwt.getAudience().contains("algolearner")
                    || jwt.getSubject() == null || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().isAfter(now) || jwt.getIssuedAt().isAfter(now)
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            try {
                if (!java.util.UUID.fromString(jwt.getSubject()).toString().equalsIgnoreCase(jwt.getSubject()))
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                String sid = jwt.getClaimAsString("sid");
                if (sid == null || !sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
                        java.util.UUID.fromString(sid), java.util.UUID.fromString(jwt.getSubject()), now))
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            } catch (IllegalArgumentException ex) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            return OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }

    private static byte[] keyBytes(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 || secret.equals("change-this-development-secret-to-at-least-32-bytes"))
            throw new IllegalStateException("Set a unique JWT_SECRET of at least 32 bytes for internal-auth");
        return bytes;
    }
}
