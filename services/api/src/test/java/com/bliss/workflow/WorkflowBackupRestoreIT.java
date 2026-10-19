package com.bliss.workflow;

import static com.bliss.workflow.WorkflowBackup.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Restore an actual paused workflow into a fresh owned database, then continue through HTTP. */
class WorkflowBackupRestoreIT {
    private final WorkflowRestartIT api = new WorkflowRestartIT();

    @Test
    @Timeout(240)
    void restoredBackupRetainsWaitingWorkflowAndCancelsExactlyOnce() throws Exception {
        var identity = new TestIdentityProvider();
        try (var source = new PostgreSQLContainer("postgres:17.6-alpine");
                var restored = new PostgreSQLContainer("postgres:17.6-alpine")) {
            try {
                source.start();
                var oidc = identity.start();
                try (var socket = new ServerSocket(0)) {
                    api.port = socket.getLocalPort();
                }
                api.start(source, oidc);
                String customer = TestIdentityProvider.token("backup-customer", "customer");
                String provider = TestIdentityProvider.token("backup-provider", "provider");
                api.call("POST", "/api/v1/me/bootstrap", customer, null, null, 200);
                api.call("POST", "/api/v1/me/bootstrap", provider, null, null, 200);
                String providerId =
                        api.call(
                                        "POST",
                                        "/api/v1/providers/me",
                                        provider,
                                        Map.of(
                                                "displayName",
                                                "Backup drill provider",
                                                "type",
                                                "INDIVIDUAL",
                                                "description",
                                                "Isolated synthetic recovery",
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
                                                "Backup service",
                                                "description",
                                                "Fixed synthetic scope",
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
                String creationKey = UUID.randomUUID().toString();
                String creationPath = "/api/v1/offerings/" + offering + "/bookings";
                Map<String, Object> creationBody = Map.of("offeringVersion", 0, "city", "Toronto");
                String booking =
                        api.call("POST", creationPath, customer, creationBody, creationKey, 201)
                                .path("id")
                                .asText();
                String path = "/api/v1/bookings/" + booking;
                String instance =
                        api.awaitStatus(booking, customer, "WAIT_PROVIDER")
                                .path("workflowInstanceId")
                                .asText();
                assertFalse(instance.isBlank());
                api.awaitLoopCheckpoints(source, instance);
                long oldProcess = api.server.pid();
                // Quiesce the sole worker before snapshotting: no second copy may execute.
                api.stop();
                assertFalse(api.server.isAlive());
                assertWaiting(source);
                var original = WorkflowBackup.restore(api, source, restored);
                assertWaiting(restored);

                api.start(restored, oidc);
                assertNotEquals(oldProcess, api.server.pid());
                assertEquals(
                        instance,
                        api.awaitStatus(booking, customer, "WAIT_PROVIDER")
                                .path("workflowInstanceId")
                                .asText());
                String key = UUID.randomUUID().toString();
                var receipt = api.call("POST", path + "/cancel", customer, null, key, 202);
                assertEquals(key, receipt.path("eventId").asText());
                assertEquals(booking, receipt.path("bookingId").asText());
                assertEquals(
                        instance,
                        api.awaitStatus(booking, customer, "CANCELLED")
                                .path("workflowInstanceId")
                                .asText());
                for (int retry = 0; retry < 2; retry++) {
                    var replay = api.call("POST", path + "/cancel", customer, null, key, 202);
                    assertEquals(key, replay.path("eventId").asText());
                    assertTrue(replay.path("processed").asBoolean());
                }
                var payment = api.call("GET", path + "/payment", customer, null, null, 200);
                assertEquals("VOIDED", payment.path("status").asText());
                assertTrue(payment.path("simulated").asBoolean());
                assertEquals("80.50", payment.path("amount").asText());
                assertEquals("CAD", payment.path("currency").asText());
                assertEquals(
                        List.of("AUTHORIZE", "VOID"),
                        api.json.convertValue(payment.path("operations"), List.class));
                var replay =
                        api.call("POST", creationPath, customer, creationBody, creationKey, 200);
                assertEquals(booking, replay.path("id").asText());
                assertEquals("CANCELLED", replay.path("status").asText());
                assertEquals(1, scalar(restored, "SELECT count(*) FROM booking"));
                assertEquals(
                        1,
                        scalar(restored, "SELECT count(*) FROM booking WHERE status='CANCELLED'"));
                assertEquals(
                        1,
                        scalar(
                                restored,
                                "SELECT count(*) FROM booking_workflow_start WHERE started"));
                assertEquals(1, scalar(restored, "SELECT count(*) FROM booking_event"));
                assertEquals(
                        1,
                        scalar(
                                restored,
                                "SELECT count(*) FROM booking_event WHERE event_type='CANCEL' AND processed_at IS NOT NULL"));
                assertEquals(2, scalar(restored, "SELECT count(*) FROM payment_transaction"));
                assertEquals(
                        1,
                        scalar(
                                restored,
                                "SELECT count(*) FROM payment_transaction WHERE operation='AUTHORIZE'"));
                assertEquals(
                        1,
                        scalar(
                                restored,
                                "SELECT count(*) FROM payment_transaction WHERE operation='VOID'"));
                assertEquals(3, scalar(restored, "SELECT count(*) FROM booking_status_history"));
                for (String status : List.of("CREATED", "WAIT_PROVIDER", "CANCELLED"))
                    assertEquals(
                            1,
                            scalar(
                                    restored,
                                    "SELECT count(*) FROM booking_status_history WHERE status='"
                                            + status
                                            + "'"));
                // The frozen source remains unchanged; only the restored copy executes.
                assertTrue(
                        original.equals(snapshot(source)),
                        "Frozen source rows must remain unchanged");
                assertWaiting(source);
                System.out.printf(
                        "Workflow backup restore proof: tables=%d restored_waits=1 same_instance=true authorize=1 void=1 processed_cancel=1 source_unchanged=true; offline synthetic drill, no RPO/RTO claim.%n",
                        original.size());
            } finally {
                api.stop();
            }
        } finally {
            identity.stop();
            api.client.close();
        }
    }

    private void assertWaiting(PostgreSQLContainer db) throws Exception {
        assertEquals(
                1,
                scalar(
                        db,
                        "SELECT count(*) FROM booking WHERE status='WAIT_PROVIDER' AND workflow_instance_id IS NOT NULL"));
        assertEquals(1, scalar(db, "SELECT count(*) FROM workflow_instance_entity"));
        assertEquals(3, scalar(db, "SELECT count(*) FROM task_info_entity"));
        assertEquals(1, scalar(db, "SELECT count(*) FROM payment_transaction"));
        assertEquals(
                1,
                scalar(db, "SELECT count(*) FROM payment_transaction WHERE operation='AUTHORIZE'"));
        assertEquals(0, scalar(db, "SELECT count(*) FROM booking_event"));
    }
}
