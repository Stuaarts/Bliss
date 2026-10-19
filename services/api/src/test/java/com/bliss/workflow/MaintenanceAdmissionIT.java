package com.bliss.workflow;

import static com.bliss.workflow.RuntimeDatabaseFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Pause new REST writes and separately fence the sole worker, then resume its saved dispute. */
class MaintenanceAdmissionIT {
    private final WorkflowRestartIT api = new WorkflowRestartIT();
    private final RuntimeDatabaseFixture roles = new RuntimeDatabaseFixture();

    @Test
    @Timeout(240)
    void maintenanceRefusesWritesPreservesReadsAndResumesTheSameWorkflow() throws Exception {
        var identity = new TestIdentityProvider();
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            try {
                db.start();
                roles.provision(db);
                roles.migrate(db, "latest");
                roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
                roles.admin(db, "ALTER ROLE bliss_migrator NOLOGIN");
                var config = new HashMap<>(identity.start());
                config.put("quarkus.flyway.migrate-at-start", "false");
                config.put("quarkus.flyway.validate-at-start", "true");
                config.put("bliss.operations.backlog.every", "1s");
                try (var socket = new ServerSocket(0)) {
                    api.port = socket.getLocalPort();
                }
                api.start(db, config, RUNTIME, roles.runtimePassword);
                var f = DisputeRecoveryFixture.create(api, false);
                String outsider = TestIdentityProvider.token("maintenance-outsider", "customer");
                api.call("POST", "/api/v1/me/bootstrap", outsider, null, null, 200);
                String path = "/api/v1/disputes/" + f.dispute();
                String bookingPath = "/api/v1/bookings/" + f.booking();
                var booking = api.call("GET", bookingPath, f.customer(), null, null, 200);
                String offeringPath =
                        "/api/v1/providers/me/"
                                + booking.path("providerId").asText()
                                + "/offerings/"
                                + booking.path("offeringId").asText();
                String instance =
                        DisputeRestartIT.await(
                                        api, f.dispute(), f.customer(), "COLLECTING_EVIDENCE", true)
                                .path("workflowInstanceId")
                                .asText();
                api.awaitLoopCheckpoints(db, instance);
                var waits = new DecisionWaitMetrics(api, DecisionWaitMetrics.Kind.DISPUTE);
                double age = waits.await("party_evidence", false);
                var evidence = api.call("GET", path + "/evidence", f.customer(), null, null, 200);
                String evidenceKey = UUID.randomUUID().toString();
                var evidenceBody = Map.of("body", "Private synthetic maintenance evidence");
                String decisionKey = UUID.randomUUID().toString();
                var decision =
                        Map.of(
                                "outcome",
                                "PROVIDER_FAVORED",
                                "rationale",
                                "Independent synthetic maintenance review");
                api.stop();
                var paused = new HashMap<>(config);
                paused.put("bliss.operations.writes-paused", "true");
                paused.put("bliss.workflow.dispatch-enabled", "false");
                paused.put("quarkus.flow.persistence.auto-restore", "false");
                api.startExpectingReadiness(db, paused, RUNTIME, roles.runtimePassword, 503);
                health("/q/health/ready", 503, "DOWN", "DOWN");
                health("/q/health/live", 200, "UP", null);
                roles.assertRuntimeSessions(db);
                assertEquals(401, request("GET", "/q/health/ready", null, null, null).statusCode());
                for (String token : List.of(f.customer(), f.provider(), f.admin())) {
                    assertEquals(
                            403, request("GET", "/q/health/ready", token, null, null).statusCode());
                }
                assertEquals(
                        200, request("GET", "/q/metrics", operator(), null, null).statusCode());

                var before = WorkflowBackup.snapshot(db);
                assertEquals(
                        200, request("GET", "/api/v1/categories", null, null, null).statusCode());
                assertEquals(401, request("GET", path, null, null, null).statusCode());
                for (String suffix : List.of("", "/evidence", "/history", "/execution")) {
                    api.call("GET", path + suffix, f.customer(), null, null, 200);
                    api.call("GET", path + suffix, outsider, null, null, 404);
                }
                assertEquals(
                        evidence,
                        api.call("GET", path + "/evidence", f.customer(), null, null, 200));
                reject("POST", path + "/evidence", f.provider(), evidenceBody, evidenceKey);
                reject("POST", path + "/decision", f.admin(), decision, decisionKey);
                reject(
                        "POST",
                        bookingPath + "/cancel",
                        f.customer(),
                        null,
                        UUID.randomUUID().toString());
                reject("POST", "/api/v1/me/bootstrap", outsider, null, null);
                reject(
                        "PUT",
                        "/api/v1/me",
                        f.customer(),
                        Map.of("displayName", "Private blocked rename"),
                        null);
                reject("DELETE", offeringPath + "?version=0", f.provider(), null, null);
                Thread.sleep(
                        4500); // Observe two dispatch intervals while both worker controls are off.
                assertEquals(
                        before,
                        WorkflowBackup.snapshot(db),
                        "Paused REST traffic changed public records");
                assertEquals(
                        instance,
                        DisputeRestartIT.await(
                                        api, f.dispute(), f.customer(), "COLLECTING_EVIDENCE", true)
                                .path("workflowInstanceId")
                                .asText());
                assertTrue(waits.await("party_evidence", false) >= age);
                api.stop();

                api.start(db, config, RUNTIME, roles.runtimePassword);
                health("/q/health/ready", 200, "UP", "UP");
                assertEquals(
                        instance,
                        DisputeRestartIT.await(
                                        api, f.dispute(), f.customer(), "COLLECTING_EVIDENCE", true)
                                .path("workflowInstanceId")
                                .asText());
                assertTrue(waits.await("party_evidence", false) >= age);
                // The same keys rejected during maintenance must be admitted normally after
                // restart.
                api.call("POST", path + "/evidence", f.provider(), evidenceBody, evidenceKey, 200);
                DisputeRestartIT.await(api, f.dispute(), f.admin(), "UNDER_REVIEW", true);
                api.call("POST", path + "/decision", f.admin(), decision, decisionKey, 202);
                var resolved =
                        DisputeRestartIT.await(
                                api, f.dispute(), f.admin(), "RESOLVED_PROVIDER", false);
                assertEquals(instance, resolved.path("workflowInstanceId").asText());
                assertTrue(
                        api.call("POST", path + "/decision", f.admin(), decision, decisionKey, 202)
                                .path("processed")
                                .asBoolean());
                api.awaitStatus(f.booking(), f.customer(), "COMPLETED");
                var payment =
                        api.call("GET", bookingPath + "/payment", f.customer(), null, null, 200);
                assertTrue(payment.path("simulated").asBoolean());
                assertEquals(
                        List.of("AUTHORIZE", "CAPTURE"),
                        api.json.convertValue(payment.path("operations"), List.class));
                assertEquals(
                        "2", roles.value(db, "SELECT count(*)::text FROM payment_transaction"));
                roles.assertRuntimeBoundary(db);
                System.out.println(
                        "Maintenance admission proof: restricted_runtime=true readiness_down_database_up=true liveness_up=true operator_only_health=true public_and_owned_reads=true outsider_denied=true post_put_delete_refused=true correlated_private_503=true records_unchanged=true no_receipts_admitted=true both_worker_controls_paused=true readiness_recovers=true same_instance=true age_retained=true evidence_retained=true rejected_keys_reusable=true decision_replay=true authorize=1 capture=1; synthetic single-worker restart only.");
            } finally {
                api.stop();
            }
        } finally {
            identity.stop();
            api.client.close();
        }
    }

    private void reject(String method, String path, String token, Object body, String key)
            throws Exception {
        var response = request(method, path, token, body, key);
        assertEquals(503, response.statusCode());
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .startsWith("application/problem+json"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        var problem = api.json.readTree(response.body());
        String id = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertDoesNotThrow(() -> UUID.fromString(id));
        assertEquals(id, problem.path("requestId").asText());
        assertEquals("API writes are temporarily paused.", problem.path("detail").asText());
        for (String value : List.of("Private", "Exception", "untrusted-maintenance-id", token)) {
            assertFalse(response.body().contains(value));
        }
    }

    private void health(String path, int status, String state, String admission) throws Exception {
        var response = request("GET", path, operator(), null, null);
        assertEquals(status, response.statusCode());
        var body = api.json.readTree(response.body());
        assertEquals(state, body.path("status").asText());
        if (admission != null) {
            var values = new HashMap<String, String>();
            for (var check : body.path("checks"))
                values.put(check.path("name").asText(), check.path("status").asText());
            assertEquals(admission, values.get("API write admission"));
            assertEquals("UP", values.get("Database connections health check"));
        }
    }

    private HttpResponse<String> request(
            String method, String path, String token, Object body, String key) throws Exception {
        var request =
                HttpRequest.newBuilder(api.uri(path))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-Request-ID", "untrusted-maintenance-id");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(api.json.writeValueAsString(body)));
        return api.client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String operator() {
        return TestIdentityProvider.token("maintenance-probe", "operator");
    }
}
