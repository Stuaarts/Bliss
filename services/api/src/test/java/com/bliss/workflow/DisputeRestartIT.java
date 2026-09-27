package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real booking lifecycle, then two process crashes in distinct dispute waits. */
class DisputeRestartIT {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disputeRestoresEvidenceAndAdminWaitWithOneSettlement(boolean captured) throws Exception {
        var api = new WorkflowRestartIT();
        var identity = new TestIdentityProvider();
        var oidc = identity.start();
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            try (var socket = new ServerSocket(0)) {
                api.port = socket.getLocalPort();
            }
            api.start(db, oidc);
            String customer = TestIdentityProvider.token("dispute-restart-customer", "customer");
            String provider = TestIdentityProvider.token("dispute-restart-provider", "provider");
            String admin = TestIdentityProvider.token("dispute-restart-admin", "admin");
            for (String token : List.of(customer, provider, admin))
                api.call("POST", "/api/v1/me/bootstrap", token, null, null, 200);
            String providerId =
                    api.call(
                                    "POST",
                                    "/api/v1/providers/me",
                                    provider,
                                    Map.of(
                                            "displayName",
                                            "Dispute restart provider",
                                            "type",
                                            "INDIVIDUAL",
                                            "description",
                                            "Demo",
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
                                            "Dispute restart service",
                                            "description",
                                            "Fixed scope",
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
            api.awaitStatus(booking, customer, "WAIT_PROVIDER");
            api.call(
                    "POST",
                    "/api/v1/bookings/" + booking + "/provider-accept",
                    provider,
                    Map.of(
                            "scheduledAt",
                            Instant.now()
                                    .plusSeconds(10)
                                    .truncatedTo(ChronoUnit.MICROS)
                                    .toString()),
                    UUID.randomUUID().toString(),
                    202);
            var scheduled = api.awaitStatus(booking, customer, "SCHEDULED");
            long wait =
                    Duration.between(
                                    Instant.now(),
                                    Instant.parse(scheduled.path("scheduledAt").asText()))
                            .toMillis();
            if (wait > 0) Thread.sleep(wait + 50);
            api.call(
                    "POST",
                    "/api/v1/bookings/" + booking + "/start",
                    provider,
                    null,
                    UUID.randomUUID().toString(),
                    202);
            api.awaitStatus(booking, customer, "IN_PROGRESS");
            api.call(
                    "POST",
                    "/api/v1/bookings/" + booking + "/mark-complete",
                    provider,
                    null,
                    UUID.randomUUID().toString(),
                    202);
            api.awaitStatus(booking, customer, "WAIT_CUSTOMER");
            if (captured) {
                api.call(
                        "POST",
                        "/api/v1/bookings/" + booking + "/confirm-completion",
                        customer,
                        null,
                        UUID.randomUUID().toString(),
                        202);
                api.awaitStatus(booking, customer, "COMPLETED");
            }
            String id =
                    api.call(
                                    "POST",
                                    "/api/v1/bookings/" + booking + "/disputes",
                                    customer,
                                    Map.of("reason", "Work needs review; restart demo."),
                                    null,
                                    201)
                            .path("id")
                            .asText();
            var collecting = await(api, id, customer, "COLLECTING_EVIDENCE", true);
            String instance = collecting.path("workflowInstanceId").asText();
            Thread.sleep(4500);
            api.stop();
            api.start(db, oidc);
            assertEquals(
                    instance,
                    await(api, id, customer, "COLLECTING_EVIDENCE", true)
                            .path("workflowInstanceId")
                            .asText());
            assertEquals(
                    1,
                    api.call(
                                    "GET",
                                    "/api/v1/disputes/" + id + "/evidence",
                                    customer,
                                    null,
                                    null,
                                    200)
                            .path("total")
                            .asInt());
            api.call(
                    "POST",
                    "/api/v1/disputes/" + id + "/evidence",
                    provider,
                    Map.of("body", "Provider evidence retained across restart."),
                    UUID.randomUUID().toString(),
                    200);
            await(api, id, admin, "UNDER_REVIEW", true);
            Thread.sleep(2500);
            api.stop();
            api.start(db, oidc);
            assertEquals(
                    instance,
                    await(api, id, admin, "UNDER_REVIEW", true)
                            .path("workflowInstanceId")
                            .asText());
            String outcome = captured ? "CUSTOMER_FAVORED" : "PROVIDER_FAVORED",
                    key = UUID.randomUUID().toString();
            var decision =
                    Map.of(
                            "outcome",
                            outcome,
                            "rationale",
                            "Manual local demo decision after reviewing both parties.");
            api.call("POST", "/api/v1/disputes/" + id + "/decision", admin, decision, key, 202);
            var resolved =
                    await(
                            api,
                            id,
                            admin,
                            captured ? "RESOLVED_CUSTOMER" : "RESOLVED_PROVIDER",
                            false);
            assertTrue(resolved.path("decision").path("processed").asBoolean());
            assertEquals(instance, resolved.path("workflowInstanceId").asText());
            assertTrue(
                    api.call(
                                    "POST",
                                    "/api/v1/disputes/" + id + "/decision",
                                    admin,
                                    decision,
                                    key,
                                    202)
                            .path("processed")
                            .asBoolean());
            api.awaitStatus(booking, customer, captured ? "REFUNDED" : "COMPLETED");
            try (var c =
                            DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var s =
                            c.prepareStatement(
                                    "SELECT operation,count(*) FROM payment_transaction WHERE booking_id=? GROUP BY operation")) {
                s.setObject(1, UUID.fromString(booking));
                var counts = new HashMap<String, Integer>();
                try (var rows = s.executeQuery()) {
                    while (rows.next()) counts.put(rows.getString(1), rows.getInt(2));
                }
                assertEquals(
                        captured
                                ? Map.of("AUTHORIZE", 1, "CAPTURE", 1, "REFUND", 1)
                                : Map.of("AUTHORIZE", 1, "CAPTURE", 1),
                        counts);
            }
        } finally {
            api.stop();
            identity.stop();
        }
    }

    JsonNode await(
            WorkflowRestartIT api, String id, String token, String expected, boolean checkpoint)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        JsonNode result;
        do {
            result = api.call("GET", "/api/v1/disputes/" + id, token, null, null, 200);
            boolean persisted =
                    !checkpoint
                            || api.call(
                                            "GET",
                                            "/api/v1/disputes/" + id + "/execution",
                                            token,
                                            null,
                                            null,
                                            200)
                                    .path("checkpointPresent")
                                    .asBoolean();
            if (expected.equals(result.path("status").asText()) && persisted) return result;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        fail("Dispute did not reach persisted " + expected + "; inspect restart-check-server.log");
        return result;
    }
}
