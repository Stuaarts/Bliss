package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.sql.*;
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
        var waits = new DecisionWaitMetrics(api, DecisionWaitMetrics.Kind.DISPUTE);
        var identity = new TestIdentityProvider();
        var oidc = new HashMap<>(identity.start());
        oidc.put("bliss.operations.backlog.every", "1s");
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            try (var socket = new ServerSocket(0)) {
                api.port = socket.getLocalPort();
            }
            api.start(db, oidc);
            var fixture = DisputeRecoveryFixture.create(api, captured);
            String customer = fixture.customer(),
                    provider = fixture.provider(),
                    admin = fixture.admin(),
                    booking = fixture.booking(),
                    id = fixture.dispute();
            var collecting = await(api, id, customer, "COLLECTING_EVIDENCE", true);
            String instance = collecting.path("workflowInstanceId").asText();
            double evidenceAge = waits.await("party_evidence", false);
            if (!captured) waits.exerciseMissingHistory(db, id, "party_evidence");
            Thread.sleep(4500);
            api.stop();
            api.start(db, oidc);
            assertEquals(
                    instance,
                    await(api, id, customer, "COLLECTING_EVIDENCE", true)
                            .path("workflowInstanceId")
                            .asText());
            assertTrue(
                    waits.await("party_evidence", false) >= evidenceAge,
                    "Evidence wait age must survive process restart");
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
            double reviewAge = waits.await("admin_review", false);
            Thread.sleep(2500);
            api.stop();
            api.start(db, oidc);
            assertEquals(
                    instance,
                    await(api, id, admin, "UNDER_REVIEW", true)
                            .path("workflowInstanceId")
                            .asText());
            assertTrue(
                    waits.await("admin_review", false) >= reviewAge,
                    "Admin wait age must survive process restart");
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
            waits.await(null, false);
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
            System.out.printf(
                    "Dispute waiting metrics proof: evidence=true admin_review=true captured=%s missing_history_exercised=%s age_survives_restart=true resolved_removed=true fixed_stages=2 private_labels=false; simulated_settlement_once=true.%n",
                    captured, !captured);
        } finally {
            api.stop();
            identity.stop();
            api.client.close();
        }
    }

    static JsonNode await(
            WorkflowRestartIT api, String id, String token, String expected, boolean checkpoint)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        JsonNode result;
        do {
            result = api.call("GET", "/api/v1/disputes/" + id, token, null, null, 200);
            boolean persisted = !checkpoint;
            if (checkpoint) {
                var execution =
                        api.call(
                                "GET",
                                "/api/v1/disputes/" + id + "/execution",
                                token,
                                null,
                                null,
                                200);
                // Dispatch may occur between these reads. Do not return the earlier
                // case snapshot until it names the same persisted engine instance.
                var instance = result.path("workflowInstanceId");
                persisted =
                        instance.isTextual()
                                && !instance.asText().isBlank()
                                && instance.asText().equals(execution.path("instanceId").asText())
                                && execution.path("checkpointPresent").asBoolean();
            }
            if (expected.equals(result.path("status").asText()) && persisted) return result;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        fail("Dispute did not reach persisted " + expected + "; inspect restart-check-server.log");
        return result;
    }
}
