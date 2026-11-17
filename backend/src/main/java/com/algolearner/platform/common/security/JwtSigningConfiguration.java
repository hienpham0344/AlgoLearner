package com.algolearner.platform.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/** The server owns its signing key; only the test profile may create an ephemeral key. */
@Configuration
@Profile("!mock")
public class JwtSigningConfiguration {
    @Bean
    KeyPair authSigningKeys(@Value("${app.security.jwt.signing-private-key-path:}") String privateKeyPath,
            Environment environment) throws Exception {
        if (privateKeyPath.isBlank() && environment.acceptsProfiles(Profiles.of("test"))) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        Assert.hasText(privateKeyPath, "JWT_SIGNING_PRIVATE_KEY_PATH is required outside mock/test");
        String pem = Files.readString(Path.of(privateKeyPath));
        byte[] der = Base64.getMimeDecoder().decode(pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", ""));
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
        Assert.isTrue(privateKey.getModulus().bitLength() >= 2048, "JWT signing key must be RSA 2048-bit or stronger");
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair authSigningKeys) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) authSigningKeys.getPublic())
                .privateKey(authSigningKeys.getPrivate()).keyID("algolearner-auth").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
