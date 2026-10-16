package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Observe real queues and failed collection through operator-gated Prometheus HTTP. */
class WorkflowBacklogMetricsIT {
    private static final String OWNER_LABEL = "bliss.backlog-drill";
    private static final List<String> QUEUES =
            List.of("booking_start", "booking_action", "dispute_start", "dispute_decision");
    private final WorkflowRestartIT api = new WorkflowRestartIT();

    @Test
    @Timeout(240)
    void backlogSurvivesFailedCollectionAndDrainsAfterDispatchResumes() throws Exception {
        var identity = new TestIdentityProvider();
        String owner = UUID.randomUUID().toString();
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            try {
                db.withLabel(OWNER_LABEL, owner);
                db.setPortBindings(List.of("127.0.0.1:" + freePort() + ":5432"));
                db.start();
                var oidc = new HashMap<>(identity.start());
                oidc.put("bliss.operations.backlog.every", "1s");
                oidc.put("bliss.workflow.dispatch-enabled", "false");
                api.port = freePort();
                api.start(db, oidc);
                var empty = awaitMetrics(values -> value(values, "snapshot_available") == 1);
                assertQueues(empty, 0, 0);
                assertEquals(401, request("/q/metrics", null).statusCode());
                for (String role : List.of("customer", "provider", "admin"))
                    assertEquals(
                            403,
                            request(
                                            "/q/metrics",
                                            TestIdentityProvider.token("backlog-" + role, role))
                                    .statusCode());
                String customer =
                        TestIdentityProvider.token("backlog-private-customer", "customer");
                String provider =
                        TestIdentityProvider.token("backlog-private-provider", "provider");
                api.call("POST", "/api/v1/me/bootstrap", customer, null, null, 200);
                api.call("POST", "/api/v1/me/bootstrap", provider, null, null, 200);
                String providerId =
                        api.call(
                                        "POST",
                                        "/api/v1/providers/me",
                                        provider,
                                        Map.of(
                                                "displayName",
                                                "Private backlog provider",
                                                "type",
                                                "INDIVIDUAL",
                                                "description",
                                                "Private aggregate fixture",
                                                "city",
                                                "Toronto"),
                                        null,
                                        201)
                                .path("id")
                                .asText();
                String offering =
                        api.call(
                                        "POST",
                                        "/api/v1/providers/me/" + providerId + "/offerings",
                                        provider,
                                        Map.of(
                                                "title",
                                                "Private backlog offering",
                                                "description",
                                                "Private scope",
                                                "price",
                                                "80.50",
                                                "currency",
                                                "CAD",
                                                "categoryId",
                                                "10000000-0000-0000-0000-000000000004"),
                                        null,
                                        201)
                                .path("id")
                                .asText();
                String booking =
                        api.call(
                                        "POST",
                                        "/api/v1/offerings/" + offering + "/bookings",
                                        customer,
                                        Map.of("offeringVersion", 0, "city", "Toronto"),
                                        UUID.randomUUID().toString(),
                                        201)
                                .path("id")
                                .asText();
                String action = UUID.randomUUID().toString();
                api.call(
                        "POST",
                        "/api/v1/bookings/" + booking + "/cancel",
                        customer,
                        null,
                        action,
                        202);
                var pending =
                        awaitMetrics(
                                values ->
                                        pending(values, "booking_start") == 1
                                                && pending(values, "booking_action") == 1
                                                && oldest(values, "booking_start") > 0
                                                && oldest(values, "booking_action") > 0);
                assertQueues(pending, 1, 1);
                for (String privateValue :
                        List.of(
                                customer,
                                provider,
                                providerId,
                                offering,
                                booking,
                                action,
                                "backlog-private-customer",
                                "backlog-private-provider",
                                "Private backlog"))
                    assertFalse(String.join("\n", pending.keySet()).contains(privateValue));

                String container = db.getContainerId();
                long process = api.server.pid();
                requireOwner(db, owner);
                db.getDockerClient().stopContainerCmd(container).withTimeout(2).exec();
                var failed =
                        awaitMetrics(
                                values ->
                                        value(values, "collection_success") == 0
                                                && value(values, "collection_failures_total") >= 1
                                                && value(values, "snapshot_age_seconds") >= 2);
                assertQueues(
                        failed, 1, 1); // Database failure must never turn pending work into zero.
                assertEquals(1, value(failed, "snapshot_available"));
                var later =
                        awaitMetrics(
                                values ->
                                        value(values, "snapshot_age_seconds")
                                                > value(failed, "snapshot_age_seconds") + 1);
                assertEquals(oldest(failed, "booking_action"), oldest(later, "booking_action"));
                assertEquals(200, request("/q/health/live", operator()).statusCode());
                assertTrue(api.server.isAlive());
                assertEquals(process, api.server.pid());
                requireOwner(db, owner);
                db.getDockerClient().startContainerCmd(container).exec();
                var recovered =
                        awaitMetrics(
                                values ->
                                        value(values, "collection_success") == 1
                                                && value(values, "snapshot_age_seconds") < 2);
                assertQueues(recovered, 1, 1);
                assertTrue(
                        value(recovered, "collection_failures_total")
                                >= value(failed, "collection_failures_total"));
                assertEquals(process, api.server.pid());

                // Deliberately paused dispatch created the backlog; resume only the owned worker.
                api.stop();
                oidc.put("bliss.workflow.dispatch-enabled", "true");
                api.start(db, oidc);
                api.awaitStatus(booking, customer, "CANCELLED");
                var drained =
                        awaitMetrics(
                                values ->
                                        value(values, "snapshot_available") == 1
                                                && pending(values, "booking_start") == 0
                                                && pending(values, "booking_action") == 0);
                assertQueues(drained, 0, 0);
                for (String queue : QUEUES) assertEquals(0, oldest(drained, queue));
                var replay =
                        api.call(
                                "POST",
                                "/api/v1/bookings/" + booking + "/cancel",
                                customer,
                                null,
                                action,
                                202);
                assertEquals(action, replay.path("eventId").asText());
                assertTrue(replay.path("processed").asBoolean());
                verifySavedBookingFailureSurvivesRestart(db, oidc, customer, offering, booking);
                System.out.println(
                        "Workflow backlog metrics proof: series=28 fixed_queues=4 pending_start=1 pending_action=1 failure_retains_snapshot=true freshness_advances=true same_process_collection_recovery=true drained=true operator_only=true empty_dispute_failures=true empty_booking_failures=true empty_booking_waits=true empty_dispute_waits=true; no capacity/alert claim.");
            } finally {
                api.stop();
            }
        } finally {
            identity.stop();
            api.client.close();
        }
    }

    private void verifySavedBookingFailureSurvivesRestart(
            PostgreSQLContainer db,
            Map<String, String> oidc,
            String customer,
            String offering,
            String terminal)
            throws Exception {
        api.stop();
        oidc.put("bliss.workflow.dispatch-enabled", "false");
        api.start(db, oidc);
        String booking =
                api.call(
                                "POST",
                                "/api/v1/offerings/" + offering + "/bookings",
                                customer,
                                Map.of("offeringVersion", 0, "city", "Toronto"),
                                UUID.randomUUID().toString(),
                                201)
                        .path("id")
                        .asText();
        String instance = "saved-failure-" + UUID.randomUUID();
        // Synthetic marker persistence fixture; actual engine retry exhaustion is tested
        // separately.
        try (var c =
                DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
            try (var s =
                    c.prepareStatement(
                            "UPDATE booking SET workflow_instance_id=?,workflow_failed_at=now()-interval '5 seconds',version=version+1 WHERE id=?")) {
                s.setString(1, instance);
                s.setObject(2, UUID.fromString(booking));
                assertEquals(1, s.executeUpdate());
            }
            try (var s =
                    c.prepareStatement(
                            "UPDATE booking_workflow_start SET claimed_at=now()-interval '60 seconds' WHERE booking_id=?")) {
                s.setObject(1, UUID.fromString(booking));
                assertEquals(1, s.executeUpdate());
            }
            try (var s =
                    c.prepareStatement(
                            "UPDATE booking SET workflow_failed_at=now()-interval '1 day' WHERE id=?")) {
                s.setObject(1, UUID.fromString(terminal));
                assertEquals(1, s.executeUpdate());
            }
        }
        assertEquals(
                "FINISHED",
                api.call(
                                "GET",
                                "/api/v1/bookings/" + terminal + "/execution",
                                customer,
                                null,
                                null,
                                200)
                        .path("status")
                        .asText());
        var before =
                awaitMetrics(
                        v ->
                                v.get("bliss_workflow_booking_failed_bookings") == 1
                                        && v.get("bliss_workflow_booking_failed_oldest_seconds")
                                                >= 5);
        assertTrue(
                before.get("bliss_workflow_booking_failed_oldest_seconds") < 120,
                "Terminal fixture's older marker must not contribute");
        api.stop(); // A distinct packaged process must retain the domain marker and skip dispatch.
        oidc.put("bliss.workflow.dispatch-enabled", "true");
        api.start(db, oidc);
        var after =
                awaitMetrics(
                        v ->
                                v.get("bliss_workflow_booking_failed_bookings") == 1
                                        && v.get("bliss_workflow_booking_failed_oldest_seconds")
                                                >= before.get(
                                                        "bliss_workflow_booking_failed_oldest_seconds"));
        assertEquals(
                1,
                pending(
                        after,
                        "booking_start")); // Failure does not pretend admission was acknowledged.
        Thread.sleep(4500); // Two scheduled dispatch intervals beyond readiness.
        assertEquals(
                instance,
                api.call("GET", "/api/v1/bookings/" + booking, customer, null, null, 200)
                        .path("workflowInstanceId")
                        .asText());
        var execution =
                api.call(
                        "GET",
                        "/api/v1/bookings/" + booking + "/execution",
                        customer,
                        null,
                        null,
                        200);
        assertEquals("ERROR", execution.path("status").asText());
        assertFalse(execution.path("checkpointPresent").asBoolean());
        api.call(
                "POST",
                "/api/v1/bookings/" + booking + "/cancel",
                customer,
                null,
                UUID.randomUUID().toString(),
                409);
        assertEquals(
                "NOT_AUTHORIZED",
                api.call(
                                "GET",
                                "/api/v1/bookings/" + booking + "/payment",
                                customer,
                                null,
                                null,
                                200)
                        .path("status")
                        .asText());
        System.out.println(
                "Saved booking failure proof: synthetic_marker=true fresh_api_process=true expired_claim_not_restarted=true same_instance=true positive_age_retained=true terminal_marker_excluded=true no_authorization=true; actual retry exhaustion covered separately.");
    }

    private Map<String, Double> awaitMetrics(Predicate<Map<String, Double>> expected)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        do {
            var response = request("/q/metrics", operator());
            assertEquals(200, response.statusCode());
            var values = new TreeMap<String, Double>();
            for (String line : response.body().split("\n")) {
                if (!line.startsWith("bliss_workflow_backlog_")
                        && !line.startsWith("bliss_workflow_dispute_failed_")
                        && !line.startsWith("bliss_workflow_booking_failed_")
                        && !line.startsWith("bliss_workflow_booking_waiting_")
                        && !line.startsWith("bliss_workflow_dispute_waiting_")) continue;
                String[] parts = line.trim().split("\\s+");
                assertEquals(2, parts.length);
                values.put(parts[0], Double.parseDouble(parts[1]));
            }
            assertEquals(28, values.size(), "Fixed-cardinality aggregate series only");
            if (expected.test(values)) return values;
            assertTrue(api.server.isAlive());
            Thread.sleep(150);
        } while (System.nanoTime() < deadline);
        fail("Metrics did not reach expected aggregate state; inspect packaged API log");
        return Map.of();
    }

    private void assertQueues(Map<String, Double> values, int starts, int actions) {
        assertEquals(starts, pending(values, "booking_start"));
        assertEquals(actions, pending(values, "booking_action"));
        assertEquals(0, pending(values, "dispute_start"));
        assertEquals(0, pending(values, "dispute_decision"));
        assertEquals(0, values.get("bliss_workflow_dispute_failed_cases"));
        assertEquals(0, values.get("bliss_workflow_dispute_failed_oldest_seconds"));
        assertEquals(0, values.get("bliss_workflow_booking_failed_bookings"));
        assertEquals(0, values.get("bliss_workflow_booking_failed_oldest_seconds"));
        for (String stage : List.of("party_evidence", "admin_review"))
            for (String name : List.of("cases", "oldest_seconds", "history_missing"))
                assertEquals(
                        0,
                        values.get(
                                "bliss_workflow_dispute_waiting_"
                                        + name
                                        + "{stage=\""
                                        + stage
                                        + "\"}"));
        for (String stage : List.of("provider_decision", "customer_decision"))
            for (String name : List.of("bookings", "oldest_seconds", "history_missing"))
                assertEquals(
                        0,
                        values.get(
                                "bliss_workflow_booking_waiting_"
                                        + name
                                        + "{stage=\""
                                        + stage
                                        + "\"}"));
    }

    private double pending(Map<String, Double> values, String queue) {
        return value(values, "pending{queue=\"" + queue + "\"}");
    }

    private double oldest(Map<String, Double> values, String queue) {
        return value(values, "oldest_seconds{queue=\"" + queue + "\"}");
    }

    private double value(Map<String, Double> values, String suffix) {
        return Objects.requireNonNull(values.get("bliss_workflow_backlog_" + suffix), suffix);
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(api.uri(path)).timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return api.client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String operator() {
        return TestIdentityProvider.token("backlog-probe", "operator");
    }

    private void requireOwner(PostgreSQLContainer db, String owner) {
        var inspected = db.getDockerClient().inspectContainerCmd(db.getContainerId()).exec();
        assertEquals(owner, inspected.getConfig().getLabels().get(OWNER_LABEL));
        assertEquals("true", inspected.getConfig().getLabels().get("org.testcontainers"));
    }

    private int freePort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
