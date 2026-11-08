package com.algolearner.platform.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JwtAuthenticationConfigurationTest {
    private static final String ISSUER = "https://issuer.example/auth/v1";
    private static final String JWKS = ISSUER + "/.well-known/jwks.json";
    private static final String SUBJECT = "9b452236-2d40-46c0-a74d-463dff559937";
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RSAKey key = new RSAKeyGenerator(2048).keyID("key1").generate();

    JwtAuthenticationConfigurationTest() throws Exception { }

    @Test void acceptsSignedTokenAndRejectsInvalidClaimsAndSignatures() throws Exception {
        var rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(JWKS)).andRespond(withSuccess(
                new JWKSet(key.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
        var decoder = JwtAuthenticationConfiguration.createDecoder(ISSUER, "authenticated", JWKS, CLOCK, rest);
        assertThat(decoder.decode(token(key, b -> {})).getSubject()).isEqualTo(SUBJECT);
        for (Consumer<JWTClaimsSet.Builder> invalid : java.util.List.<Consumer<JWTClaimsSet.Builder>>of(
                b -> b.issuer("https://other.example"), b -> b.audience("other"),
                b -> b.claim("aud", null), b -> b.subject("admin"), b -> b.subject("1-1-1-1-1"),
                b -> b.subject(null), b -> b.expirationTime(Date.from(NOW)),
                b -> b.expirationTime(null), b -> b.issueTime(null),
                b -> b.issueTime(Date.from(NOW.plusSeconds(10))),
                b -> b.notBeforeTime(Date.from(NOW.plusSeconds(10))))) {
            String token = token(key, invalid);
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
        var wrongKey = new RSAKeyGenerator(2048).keyID("key1").generate();
        String forged = token(wrongKey, b -> {});
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("mock-admin")).isInstanceOf(JwtException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "mock", "dev", "prod", "production", "mock,dev,prod", "mock,test,production"})
    void realDecoderIsRequiredAndMissingSettingsFailStartup(String profiles) {
        context(profiles).run(c -> assertThat(c).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mock,dev", "mock,test", "prod", "mock,dev,production", ""})
    void exactlyOneDecoderIsSelected(String profiles) {
        context(profiles).withPropertyValues("app.security.jwt.issuer=" + ISSUER,
            "app.security.jwt.audience=authenticated", "app.security.jwt.jwk-set-uri=" + JWKS)
            .run(c -> assertThat(c).hasSingleBean(JwtDecoder.class));
    }

    @Test void requiresEverySettingAndHttps() {
        var rest = new RestTemplate();
        assertThatIllegalArgumentException().isThrownBy(() -> JwtAuthenticationConfiguration.createDecoder("", "aud", JWKS, CLOCK, rest));
        assertThatIllegalArgumentException().isThrownBy(() -> JwtAuthenticationConfiguration.createDecoder(ISSUER, "", JWKS, CLOCK, rest));
        assertThatIllegalArgumentException().isThrownBy(() -> JwtAuthenticationConfiguration.createDecoder(ISSUER, "aud", "", CLOCK, rest));
        assertThatIllegalArgumentException().isThrownBy(() -> JwtAuthenticationConfiguration.createDecoder(ISSUER, "aud", "http://issuer.example/jwks", CLOCK, rest));
    }

    private ApplicationContextRunner context(String profiles) {
        return new ApplicationContextRunner()
            .withInitializer(c -> c.getEnvironment().setActiveProfiles(profiles.isEmpty() ? new String[0] : profiles.split(",")))
            .withBean(Clock.class, () -> CLOCK)
            .withBean(AccountRepository.class, () -> org.mockito.Mockito.mock(AccountRepository.class))
            .withUserConfiguration(JwtAuthenticationConfiguration.class, MockAuthenticationConfiguration.class);
    }

    private String token(RSAKey signer, Consumer<JWTClaimsSet.Builder> customize) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("authenticated").subject(SUBJECT)
            .issueTime(Date.from(NOW.minusSeconds(30))).expirationTime(Date.from(NOW.plusSeconds(300)));
        customize.accept(claims);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key1").build(), claims.build());
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }
}
