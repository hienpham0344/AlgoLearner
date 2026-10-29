package com.algolearner.platform.common.security;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.*;
import java.time.Clock;
/** Development adapter. Replace with a real JwtDecoder when integrating module 4.1. */
@Configuration
@Profile("mock")
public class MockAuthenticationConfiguration {
    @Bean JwtDecoder mockJwtDecoder(AccountRepository repository, Clock clock) {
        return token -> {
            String username = token.startsWith("mock-") ? token.substring(5) : "";
            var account = repository.accounts().stream().filter(a -> a.username().equals(username))
                .findFirst().orElseThrow(() -> new BadJwtException("Unknown mock token"));
            return Jwt.withTokenValue(token).header("alg","mock").subject(account.id().toString())
                .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(3600)).build();
        };
    }
}
