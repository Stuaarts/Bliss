package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verify the actual production JSON console output, not only the logging handler. */
class RequestDiagnosticsIT {
    @Test
    void packagedJsonDiagnosticsExcludeSubmittedPrivateMaterial() throws Exception {
        var api = new WorkflowRestartIT();
        var identity = new TestIdentityProvider();
        Path log = Path.of("target/restart-check-server.log");
        int start = Files.exists(log) ? Files.readString(log).length() : 0;
        String marker = "private-packaged-" + UUID.randomUUID();
        String resourceId = UUID.randomUUID().toString();
        String token = null;
        var expected = new HashMap<String, String>();
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            var oidc = identity.start();
            try (var socket = new ServerSocket(0)) {
                api.port = socket.getLocalPort();
            }
            api.start(db, oidc);
            token = TestIdentityProvider.token("private-packaged-subject", "provider");
            String[] paths = {
                "/api/v1/categories?private=" + marker,
                "/api/v1/offerings/" + resourceId,
                "/api/v1/providers/me"
            };
            String[] methods = {"GET", "GET", "POST"};
            String[] operations = {
                "CatalogResource.categories",
                "CatalogResource.offering",
                "ProviderManagementResource.create"
            };
            int[] statuses = {200, 404, 400};
            for (int index = 0; index < paths.length; index++) {
                var builder =
                        HttpRequest.newBuilder(api.uri(paths[index]))
                                .timeout(Duration.ofSeconds(5))
                                .header("X-Request-ID", marker)
                                .header("Cookie", "session=" + marker)
                                .header("Authorization", "Bearer " + token)
                                .header("Content-Type", "application/json");
                var body =
                        index == 2
                                ? HttpRequest.BodyPublishers.ofString(
                                        api.json.writeValueAsString(
                                                Map.of(
                                                        "displayName",
                                                        marker,
                                                        "type",
                                                        "INDIVIDUAL",
                                                        "description",
                                                        marker,
                                                        "city",
                                                        "")))
                                : HttpRequest.BodyPublishers.noBody();
                var response =
                        api.client.send(
                                builder.method(methods[index], body).build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(statuses[index], response.statusCode());
                String id = response.headers().firstValue("X-Request-ID").orElseThrow();
                assertDoesNotThrow(() -> UUID.fromString(id));
                assertNull(
                        expected.put(
                                id,
                                "event=rest_response requestId="
                                        + id
                                        + " method="
                                        + methods[index]
                                        + " operation="
                                        + operations[index]
                                        + " status="
                                        + statuses[index]));
            }
        } finally {
            try {
                api.stop();
            } finally {
                identity.stop();
                api.client.close();
            }
        }
        String output = Files.readString(log).substring(start);
        assertNotNull(token);
        for (String value : new String[] {marker, resourceId, token, "private-packaged-subject"}) {
            assertFalse(
                    output.contains(value),
                    "Private HTTP material entered production console output");
        }
        var seen = new HashMap<String, Integer>();
        for (String line : output.lines().toList()) {
            if (!line.startsWith("{")) continue;
            var record = api.json.readTree(line);
            if (!"com.bliss.http.requests".equals(record.path("loggerName").asText())) continue;
            assertEquals("INFO", record.path("level").asText());
            assertFalse(record.path("timestamp").asText().isBlank());
            assertFalse(record.hasNonNull("exception"));
            assertFalse(record.hasNonNull("stackTrace"));
            String message = record.path("message").asText();
            for (var entry : expected.entrySet()) {
                if (message.startsWith(entry.getValue() + " durationMs=")) {
                    assertTrue(
                            message.substring(entry.getValue().length())
                                    .matches(" durationMs=[0-9]+"));
                    seen.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        assertEquals(3, expected.size());
        assertEquals(expected.keySet(), seen.keySet());
        assertTrue(seen.values().stream().allMatch(count -> count == 1));
    }
}
