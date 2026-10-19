package com.bliss.workflow;

import static com.bliss.workflow.WorkflowBackup.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.util.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Restore both dispute wait stages, including an admitted decision, into a fresh database. */
class DisputeBackupRestoreIT {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(300)
    void restoredDisputePreservesEvidenceAndSettlesExactlyOnce(boolean captured) throws Exception {
        var api = new WorkflowRestartIT();
        var identity = new TestIdentityProvider();
        try (var source = new PostgreSQLContainer("postgres:17.6-alpine");
                var restored = new PostgreSQLContainer("postgres:17.6-alpine")) {
            try {
                source.start();
                var oidc = new HashMap<>(identity.start());
                oidc.put("bliss.operations.backlog.every", "1s");
                try (var socket = new ServerSocket(0)) {
                    api.port = socket.getLocalPort();
                }
                api.start(source, oidc);
                var f = DisputeRecoveryFixture.create(api, captured);
                String path = "/api/v1/disputes/" + f.dispute();
                String outsider = TestIdentityProvider.token("dispute-backup-outsider", "customer");
                api.call("POST", "/api/v1/me/bootstrap", outsider, null, null, 200);
                String evidenceKey = UUID.randomUUID().toString();
                Map<String, String> evidenceBody =
                        Map.of("body", "Synthetic customer evidence retained in the owned backup.");
                JsonNode evidenceReceipt =
                        api.call(
                                "POST",
                                path + "/evidence",
                                f.customer(),
                                evidenceBody,
                                evidenceKey,
                                200);
                if (captured) providerEvidence(api, f);
                String status = captured ? "UNDER_REVIEW" : "COLLECTING_EVIDENCE";
                String stage = captured ? "admin_review" : "party_evidence";
                var waiting = DisputeRestartIT.await(api, f.dispute(), f.admin(), status, true);
                String instance = waiting.path("workflowInstanceId").asText();
                api.awaitLoopCheckpoints(source, instance);
                var waits = new DecisionWaitMetrics(api, DecisionWaitMetrics.Kind.DISPUTE);
                double ageBefore = waits.await(stage, false);
                JsonNode evidenceBefore =
                        api.call("GET", path + "/evidence", f.customer(), null, null, 200);
                assertEquals(captured ? 3 : 2, evidenceBefore.path("total").asInt());
                String decisionKey = UUID.randomUUID().toString();
                var decisionBody =
                        Map.of(
                                "outcome",
                                captured ? "CUSTOMER_FAVORED" : "PROVIDER_FAVORED",
                                "rationale",
                                "Independent synthetic decision after reviewing the preserved evidence.");
                JsonNode pendingDecision = null;
                api.stop();
                // Disabling dispatch alone does not pause restored instances. Fence both
                // mechanisms.
                oidc.put("bliss.workflow.dispatch-enabled", "false");
                oidc.put("quarkus.flow.persistence.auto-restore", "false");
                if (captured) {
                    api.start(source, oidc);
                    pendingDecision =
                            api.call(
                                    "POST",
                                    path + "/decision",
                                    f.admin(),
                                    decisionBody,
                                    decisionKey,
                                    202);
                    assertFalse(pendingDecision.path("processed").asBoolean());
                    Thread.sleep(2300);
                    assertPaused(source, status, captured);
                    api.stop();
                }
                assertPaused(source, status, captured);
                var original = WorkflowBackup.restore(api, source, restored);
                assertPaused(restored, status, captured);

                // Inspect the restored state before either restored workflow may execute.
                api.start(restored, oidc);
                var recovered = DisputeRestartIT.await(api, f.dispute(), f.admin(), status, true);
                assertEquals(instance, recovered.path("workflowInstanceId").asText());
                assertTrue(
                        waits.await(stage, false) >= ageBefore,
                        "Saved wait age must survive database restore");
                assertEquals(
                        evidenceBefore,
                        api.call("GET", path + "/evidence", f.customer(), null, null, 200));
                assertEquals(
                        evidenceReceipt,
                        api.call(
                                "POST",
                                path + "/evidence",
                                f.customer(),
                                evidenceBody,
                                evidenceKey,
                                200));
                api.call(
                        "POST",
                        path + "/evidence",
                        f.customer(),
                        Map.of("body", "Conflicting key reuse"),
                        evidenceKey,
                        409);
                for (String suffix : List.of("", "/evidence", "/history", "/execution"))
                    api.call("GET", path + suffix, outsider, null, null, 404);
                api.call("POST", path + "/decision", f.customer(), decisionBody, decisionKey, 403);
                if (captured) {
                    assertEquals(
                            pendingDecision,
                            api.call(
                                    "POST",
                                    path + "/decision",
                                    f.admin(),
                                    decisionBody,
                                    decisionKey,
                                    202));
                    assertFalse(recovered.path("decision").path("processed").asBoolean());
                }
                assertPaused(restored, status, captured);
                api.stop();
                oidc.put("bliss.workflow.dispatch-enabled", "true");
                oidc.put("quarkus.flow.persistence.auto-restore", "true");
                api.start(restored, oidc);
                if (!captured) {
                    assertEquals(
                            instance,
                            DisputeRestartIT.await(api, f.dispute(), f.customer(), status, true)
                                    .path("workflowInstanceId")
                                    .asText());
                    providerEvidence(api, f);
                    DisputeRestartIT.await(api, f.dispute(), f.admin(), "UNDER_REVIEW", true);
                    api.call("POST", path + "/decision", f.admin(), decisionBody, decisionKey, 202);
                }
                var resolved =
                        DisputeRestartIT.await(
                                api,
                                f.dispute(),
                                f.admin(),
                                captured ? "RESOLVED_CUSTOMER" : "RESOLVED_PROVIDER",
                                false);
                assertEquals(instance, resolved.path("workflowInstanceId").asText());
                assertTrue(resolved.path("decision").path("processed").asBoolean());
                waits.await(null, false);
                for (int i = 0; i < 2; i++) {
                    var replay =
                            api.call(
                                    "POST",
                                    path + "/decision",
                                    f.admin(),
                                    decisionBody,
                                    decisionKey,
                                    202);
                    assertTrue(replay.path("processed").asBoolean());
                    assertEquals(
                            resolved.path("decision").path("admittedAt"),
                            replay.path("admittedAt"));
                }
                api.call(
                        "POST",
                        path + "/decision",
                        f.admin(),
                        decisionBody,
                        UUID.randomUUID().toString(),
                        409);
                api.awaitStatus(f.booking(), f.customer(), captured ? "REFUNDED" : "COMPLETED");
                var payment =
                        api.call(
                                "GET",
                                "/api/v1/bookings/" + f.booking() + "/payment",
                                f.customer(),
                                null,
                                null,
                                200);
                assertEquals(captured ? "REFUNDED" : "CAPTURED", payment.path("status").asText());
                assertTrue(payment.path("simulated").asBoolean());
                assertEquals("80.50", payment.path("amount").asText());
                assertEquals("CAD", payment.path("currency").asText());
                assertEquals(
                        captured
                                ? List.of("AUTHORIZE", "CAPTURE", "REFUND")
                                : List.of("AUTHORIZE", "CAPTURE"),
                        api.json.convertValue(payment.path("operations"), List.class));
                assertEquals(
                        1,
                        scalar(
                                restored,
                                "SELECT count(*) FROM dispute_decision WHERE processed_at IS NOT NULL"));
                assertEquals(3, scalar(restored, "SELECT count(*) FROM dispute_evidence"));
                assertEquals(3, scalar(restored, "SELECT count(*) FROM dispute_history"));
                for (String operation : List.of("AUTHORIZE", "CAPTURE", "REFUND", "VOID"))
                    assertEquals(
                            operation.equals("VOID") || operation.equals("REFUND") && !captured
                                    ? 0
                                    : 1,
                            scalar(
                                    restored,
                                    "SELECT count(*) FROM payment_transaction WHERE operation='"
                                            + operation
                                            + "'"));
                assertEquals(
                        captured ? 3 : 2,
                        scalar(restored, "SELECT count(*) FROM payment_transaction"));
                assertTrue(
                        original.equals(snapshot(source)),
                        "The fenced source rows must remain unchanged");
                assertPaused(source, status, captured);
                System.out.printf(
                        "Dispute backup restore proof: stage=%s captured=%s tables=%d same_instance=true evidence_replay=true pending_decision_restored=%s ownership_retained=true authorize=1 capture=1 refund=%d source_unchanged=true; offline synthetic drill, no RPO/RTO claim.%n",
                        stage, captured, original.size(), captured, captured ? 1 : 0);
            } finally {
                api.stop();
            }
        } finally {
            identity.stop();
            api.client.close();
        }
    }

    private void providerEvidence(WorkflowRestartIT api, DisputeRecoveryFixture f)
            throws Exception {
        api.call(
                "POST",
                "/api/v1/disputes/" + f.dispute() + "/evidence",
                f.provider(),
                Map.of("body", "Synthetic provider evidence reviewed independently."),
                UUID.randomUUID().toString(),
                200);
    }

    private void assertPaused(PostgreSQLContainer db, String status, boolean captured)
            throws Exception {
        assertEquals(
                1,
                scalar(
                        db,
                        "SELECT count(*) FROM dispute WHERE status='"
                                + status
                                + "' AND workflow_instance_id IS NOT NULL AND workflow_failed_at IS NULL"));
        assertEquals(
                1,
                scalar(
                        db,
                        "SELECT count(*) FROM booking WHERE status='DISPUTED' AND workflow_failed_at IS NULL"));
        assertEquals(
                1,
                scalar(
                        db,
                        "SELECT count(*) FROM workflow_instance_entity WHERE workflow_name='dispute-resolution'"));
        assertEquals(
                3,
                scalar(
                        db,
                        "SELECT count(*) FROM task_info_entity t JOIN workflow_instance_entity w ON w.application_id=t.application_id AND w.instance_id=t.workflow_instance_id WHERE w.workflow_name='dispute-resolution'"));
        assertEquals(1, scalar(db, "SELECT count(*) FROM dispute_workflow_start WHERE started"));
        assertEquals(captured ? 3 : 2, scalar(db, "SELECT count(*) FROM dispute_evidence"));
        assertEquals(captured ? 2 : 1, scalar(db, "SELECT count(*) FROM dispute_history"));
        assertEquals(captured ? 1 : 0, scalar(db, "SELECT count(*) FROM dispute_decision"));
        assertEquals(
                captured ? 1 : 0,
                scalar(db, "SELECT count(*) FROM dispute_decision WHERE processed_at IS NULL"));
        assertEquals(
                1,
                scalar(
                        db,
                        "SELECT count(*) FROM payment WHERE status='"
                                + (captured ? "CAPTURED" : "AUTHORIZED")
                                + "'"));
        assertEquals(captured ? 2 : 1, scalar(db, "SELECT count(*) FROM payment_transaction"));
        assertEquals(
                0,
                scalar(
                        db,
                        "SELECT count(*) FROM payment_transaction WHERE operation IN ('REFUND','VOID')"));
    }
}
