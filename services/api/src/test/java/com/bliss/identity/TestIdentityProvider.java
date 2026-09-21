package com.bliss.identity;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.jwt.build.Jwt;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Ephemeral signing keys exercise actual OIDC JWT validation, without committed keys. */
public class TestIdentityProvider implements QuarkusTestResourceLifecycleManager {
    public static final String ISSUER = "https://issuer.bliss.test";
    private static KeyPair keys;

    @Override
    public Map<String, String> start() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keys = generator.generateKeyPair();
            return Map.of(
                    "quarkus.oidc.public-key",
                    Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),
                    "quarkus.oidc.auth-server-url",
                    "",
                    "quarkus.oidc.token.issuer",
                    ISSUER,
                    "quarkus.oidc.discovery-enabled",
                    "false");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String token(String subject, String... roles) {
        return token(
                subject,
                ISSUER,
                "bliss-api",
                Instant.now().plusSeconds(300).getEpochSecond(),
                roles);
    }

    public static String token(
            String subject, String issuer, String audience, long expiry, String... roles) {
        return Jwt.issuer(issuer)
                .subject(subject)
                .audience(audience)
                .claim("preferred_username", subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .expiresAt(expiry)
                .sign(keys.getPrivate());
    }

    @Override
    public void stop() {
        keys = null;
    }

    public static String tokenWithoutSubject() {
        return Jwt.issuer(ISSUER)
                .audience("bliss-api")
                .claim("realm_access", Map.of("roles", List.of("provider")))
                .expiresAt(Instant.now().plusSeconds(300))
                .sign(keys.getPrivate());
    }
}
