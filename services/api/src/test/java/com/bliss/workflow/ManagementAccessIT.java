package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Production-profile HTTP policy, using a packaged API and its own PostgreSQL. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManagementAccessIT {
    private final WorkflowRestartIT api = new WorkflowRestartIT();
    private final TestIdentityProvider identity = new TestIdentityProvider();
    private PostgreSQLContainer db;
    private static final List<String> PATHS =
            List.of(
                    "/q/health/live",
                    "/q/health/ready",
                    "/q/health/started",
                    "/q/metrics",
                    "/q/openapi",
                    "/q/swagger-ui/");

    @BeforeAll
    void start() throws Exception {
        var oidc = identity.start();
        db = new PostgreSQLContainer("postgres:17.6-alpine");
        db.start();
        try (var socket = new ServerSocket(0)) {
            api.port = socket.getLocalPort();
        }
        api.start(db, oidc);
    }

    @AfterAll
    void stop() throws Exception {
        try {
            api.stop();
        } finally {
            try {
                if (db != null) db.close();
            } finally {
                identity.stop();
                api.client.close();
            }
        }
    }

    @Test
    void managementRequiresOperatorRatherThanMarketplaceRoles() throws Exception {
        String operator = TestIdentityProvider.token("operations-reader", "operator");
        for (String path : PATHS) {
            assertEquals(401, request("GET", path, null).statusCode(), path);
            for (String role : List.of("customer", "provider", "admin")) {
                assertEquals(
                        403,
                        request(
                                        "GET",
                                        path,
                                        TestIdentityProvider.token("marketplace-" + role, role))
                                .statusCode(),
                        role + " must not read " + path);
            }
            assertEquals(200, request("GET", path, operator).statusCode(), path);
        }
        assertEquals(
                "UP",
                api.json
                        .readTree(request("GET", "/q/health/ready", operator).body())
                        .path("status")
                        .asText());
        assertTrue(request("GET", "/q/metrics", operator).body().contains("# HELP"));
    }

    @Test
    void invalidOperatorTokensCannotReadManagement() throws Exception {
        long future = Instant.now().plusSeconds(300).getEpochSecond();
        String[] signature = TestIdentityProvider.token("signature-check", "operator").split("\\.");
        signature[2] = (signature[2].charAt(0) == 'A' ? "B" : "A") + signature[2].substring(1);
        var invalid =
                List.of(
                        "not-a-jwt",
                        TestIdentityProvider.token(
                                "wrong-issuer",
                                "https://other.bliss.test",
                                "bliss-api",
                                future,
                                "operator"),
                        TestIdentityProvider.token(
                                "wrong-audience",
                                TestIdentityProvider.ISSUER,
                                "other-api",
                                future,
                                "operator"),
                        TestIdentityProvider.token(
                                "expired",
                                TestIdentityProvider.ISSUER,
                                "bliss-api",
                                Instant.now().minusSeconds(120).getEpochSecond(),
                                "operator"),
                        TestIdentityProvider.tokenWithoutSubject("operator"),
                        String.join(".", signature));
        var labels =
                List.of(
                        "malformed",
                        "issuer",
                        "audience",
                        "expiry",
                        "missing subject",
                        "signature");
        for (int index = 0; index < invalid.size(); index++) {
            assertEquals(
                    401,
                    request("GET", "/q/metrics", invalid.get(index)).statusCode(),
                    labels.get(index));
        }
        assertEquals(
                403,
                request("GET", "/q/metrics", TestIdentityProvider.token("no-roles")).statusCode());
    }

    @Test
    void methodAndNormalizedPathVariantsDoNotBypassThePolicy() throws Exception {
        for (String method : List.of("GET", "HEAD", "OPTIONS", "POST")) {
            assertEquals(401, request(method, "/q/metrics", null).statusCode(), method);
            assertEquals(
                    403,
                    request(
                                    method,
                                    "/q/metrics",
                                    TestIdentityProvider.token("customer", "customer"))
                            .statusCode(),
                    method);
        }
        for (String path :
                List.of("/q", "/q/", "/%71/metrics", "/q/./metrics", "/q/metrics?probe=1")) {
            assertEquals(401, request("GET", path, null).statusCode(), path);
        }
    }

    @Test
    void publicCatalogAndBusinessAuthorizationRemainSeparate() throws Exception {
        for (String token :
                new String[] {
                    null,
                    TestIdentityProvider.token("reader", "customer"),
                    TestIdentityProvider.token("operator", "operator")
                }) {
            var response = request("GET", "/api/v1/categories", token);
            assertEquals(200, response.statusCode());
            assertEquals(9, api.json.readTree(response.body()).size());
        }
        assertEquals(401, request("GET", "/api/v1/me", null).statusCode());
        assertEquals(
                403,
                request(
                                "GET",
                                "/api/v1/providers/me",
                                TestIdentityProvider.token("operator", "operator"))
                        .statusCode());
    }

    private HttpResponse<String> request(String method, String path, String token)
            throws Exception {
        var builder = HttpRequest.newBuilder(api.uri(path)).timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return api.client.send(
                builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
