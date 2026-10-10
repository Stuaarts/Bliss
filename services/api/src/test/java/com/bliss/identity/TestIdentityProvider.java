package com.bliss.identity;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.jwt.build.Jwt;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;

/** Ephemeral signing keys exercise actual OIDC JWT validation, without committed keys. */
public class TestIdentityProvider implements QuarkusTestResourceLifecycleManager {
    public static final String ISSUER = "https://issuer.bliss.test";
    private static final String KEY_ID = "bliss-test-key";
    private static KeyPair keys;
    private HttpServer jwks;

    @Override
    public Map<String, String> start() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keys = generator.generateKeyPair();
            var publicKey = new RsaJsonWebKey((RSAPublicKey) keys.getPublic());
            publicKey.setKeyId(KEY_ID);
            byte[] response =
                    ("{\"keys\":["
                                    + publicKey.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)
                                    + "]}")
                            .getBytes(StandardCharsets.UTF_8);
            jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwks.createContext(
                    "/jwks",
                    exchange -> {
                        try (exchange) {
                            if (!"GET".equals(exchange.getRequestMethod())) {
                                exchange.sendResponseHeaders(405, -1);
                                return;
                            }
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, response.length);
                            exchange.getResponseBody().write(response);
                        }
                    });
            jwks.start();
            return Map.of(
                    "quarkus.oidc.auth-server-url",
                    "http://127.0.0.1:" + jwks.getAddress().getPort(),
                    "quarkus.oidc.jwks-path",
                    "/jwks",
                    "quarkus.oidc.token.issuer",
                    ISSUER,
                    "quarkus.oidc.discovery-enabled",
                    "false");
        } catch (Exception e) {
            stop();
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
                .jws()
                .keyId(KEY_ID)
                .sign(keys.getPrivate());
    }

    @Override
    public void stop() {
        if (jwks != null) jwks.stop(0);
        keys = null;
    }

    public static String tokenWithoutSubject() {
        return tokenWithoutSubject("provider");
    }

    public static String tokenWithoutSubject(String... roles) {
        return Jwt.issuer(ISSUER)
                .audience("bliss-api")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .expiresAt(Instant.now().plusSeconds(300))
                .jws()
                .keyId(KEY_ID)
                .sign(keys.getPrivate());
    }
}
