package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Stop only this drill's labelled database, then recover without restarting the API. */
class DatabaseOutageIT {
    private static final String OWNER_LABEL = "bliss.outage-drill";
    private final WorkflowRestartIT api = new WorkflowRestartIT();

    @Test
    void readinessDetectsDatabaseOutageAndRecoversInTheSameApiProcess() throws Exception {
        var identity = new TestIdentityProvider();
        String owner = UUID.randomUUID().toString();
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.withLabel(OWNER_LABEL, owner);
            // Preserve the JDBC address when Docker starts the same container again.
            db.setPortBindings(List.of("127.0.0.1:" + freePort() + ":5432"));
            db.start();
            var oidc = identity.start();
            api.port = freePort();
            api.start(db, oidc);
            long process = api.server.pid();
            String container = db.getContainerId();
            String jdbc = db.getJdbcUrl();
            health("/q/health/live", 200, "UP");
            health("/q/health/ready", 200, "UP");

            String provider = TestIdentityProvider.token("outage-provider", "provider");
            api.call("POST", "/api/v1/me/bootstrap", provider, null, null, 200);
            JsonNode saved =
                    api.call(
                            "POST",
                            "/api/v1/providers/me",
                            provider,
                            Map.of(
                                    "displayName", "Outage drill provider",
                                    "type", "INDIVIDUAL",
                                    "description", "Isolated synthetic recovery record",
                                    "city", "Toronto"),
                            null,
                            201);
            String providerPath = "/api/v1/providers/me/" + saved.path("id").asText();
            assertEquals(saved, api.call("GET", providerPath, provider, null, null, 200));

            requireOwner(db, container, owner);
            long stoppedAt = System.nanoTime();
            db.getDockerClient().stopContainerCmd(container).withTimeout(2).exec();
            assertFalse(
                    db.getDockerClient()
                            .inspectContainerCmd(container)
                            .exec()
                            .getState()
                            .getRunning());
            JsonNode down = awaitReadiness(503, "DOWN");
            long detectedAt = System.nanoTime();
            JsonNode databaseCheck = null;
            for (JsonNode check : down.path("checks")) {
                if ("Database connections health check".equals(check.path("name").asText())) {
                    databaseCheck = check;
                }
            }
            assertNotNull(databaseCheck, "Readiness must include the database health check");
            assertEquals("DOWN", databaseCheck.path("status").asText());
            health("/q/health/live", 200, "UP");
            assertSameApi(process);
            assertEquals(401, request("/q/health/ready", null).statusCode());
            assertEquals(
                    403,
                    request(
                                    "/q/health/ready",
                                    TestIdentityProvider.token("outage-customer", "customer"))
                            .statusCode());
            assertEquals(200, request("/q/metrics", operator()).statusCode());

            requireOwner(db, container, owner);
            long restoredAt = System.nanoTime();
            db.getDockerClient().startContainerCmd(container).exec();
            awaitReadiness(200, "UP");
            long recoveredAt = System.nanoTime();
            health("/q/health/live", 200, "UP");
            assertSameApi(process);
            assertEquals(container, db.getContainerId());
            assertEquals(jdbc, db.getJdbcUrl());
            assertEquals(saved, api.call("GET", providerPath, provider, null, null, 200));
            var updated =
                    api.call(
                            "PUT",
                            providerPath,
                            provider,
                            Map.of(
                                    "displayName", "Recovered drill provider",
                                    "description", "Successful write after database recovery",
                                    "city", "Toronto",
                                    "version", saved.path("version").asLong()),
                            null,
                            200);
            assertEquals(saved.path("id"), updated.path("id"));
            assertEquals(saved.path("version").asLong() + 1, updated.path("version").asLong());
            assertEquals(updated, api.call("GET", providerPath, provider, null, null, 200));
            var catalog = request("/api/v1/categories", null);
            assertEquals(200, catalog.statusCode());
            assertEquals(9, api.json.readTree(catalog.body()).size());

            System.out.printf(
                    "Database outage drill: detection_ms=%d recovery_ms=%d same_api=true retained_provider=true recovered_write=true%n",
                    TimeUnit.NANOSECONDS.toMillis(detectedAt - stoppedAt),
                    TimeUnit.NANOSECONDS.toMillis(recoveredAt - restoredAt));
        } finally {
            try {
                api.stop();
            } finally {
                identity.stop();
                api.client.close();
            }
        }
    }

    private void requireOwner(PostgreSQLContainer db, String container, String owner) {
        assertEquals(container, db.getContainerId());
        var inspected = db.getDockerClient().inspectContainerCmd(container).exec();
        assertEquals(owner, inspected.getConfig().getLabels().get(OWNER_LABEL));
        assertEquals("true", inspected.getConfig().getLabels().get("org.testcontainers"));
    }

    private void assertSameApi(long process) {
        assertTrue(api.server.isAlive(), "Database outage must not stop the API process");
        assertEquals(process, api.server.pid(), "Recovery must not restart the API");
    }

    private JsonNode awaitReadiness(int status, String state) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        Integer lastStatus = null;
        do {
            try {
                var response = request("/q/health/ready", operator());
                lastStatus = response.statusCode();
                if (lastStatus == status) {
                    var body = api.json.readTree(response.body());
                    assertEquals(state, body.path("status").asText());
                    return body;
                }
            } catch (java.io.IOException ignored) {
                // A timeout is not health evidence. Require an actual HTTP response.
            }
            assertTrue(api.server.isAlive(), "Own API exited during database outage drill");
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        fail("Readiness did not return " + status + "/" + state + "; last status=" + lastStatus);
        return null;
    }

    private void health(String path, int status, String state) throws Exception {
        var response = request(path, operator());
        assertEquals(status, response.statusCode(), path);
        assertEquals(state, api.json.readTree(response.body()).path("status").asText());
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(api.uri(path)).timeout(Duration.ofSeconds(12));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return api.client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String operator() {
        return TestIdentityProvider.token("outage-probe", "operator");
    }

    private int freePort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
