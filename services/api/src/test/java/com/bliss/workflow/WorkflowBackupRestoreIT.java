package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Restore an actual paused workflow into a fresh owned database, then continue through HTTP. */
class WorkflowBackupRestoreIT {
    private final WorkflowRestartIT api = new WorkflowRestartIT();

    @Test
    @Timeout(240)
    void restoredBackupRetainsWaitingWorkflowAndCancelsExactlyOnce() throws Exception {
        var identity = new TestIdentityProvider();
        Path backup = Files.createTempFile(Path.of("target"), "workflow-backup-", ".dump");
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
                var original = snapshot(source);
                assertTrue(
                        original.size() >= 28,
                        "Snapshot must include every public application table");
                var dump =
                        source.execInContainer(
                                "pg_dump",
                                "--username",
                                source.getUsername(),
                                "--dbname",
                                source.getDatabaseName(),
                                "--format=custom",
                                "--no-owner",
                                "--no-privileges",
                                "--file=/tmp/bliss-workflow.dump");
                assertEquals(0, dump.getExitCode(), "Owned database backup failed");
                source.copyFileFromContainer("/tmp/bliss-workflow.dump", backup.toString());
                assertTrue(Files.size(backup) > 0);
                restored.start();
                assertNotEquals(source.getContainerId(), restored.getContainerId());
                assertNotEquals(source.getJdbcUrl(), restored.getJdbcUrl());
                assertEquals(Map.of(), snapshot(restored), "Restore destination must be empty");
                restored.copyFileToContainer(
                        MountableFile.forHostPath(backup), "/tmp/bliss-workflow.dump");
                var restore =
                        restored.execInContainer(
                                "pg_restore",
                                "--username",
                                restored.getUsername(),
                                "--dbname",
                                restored.getDatabaseName(),
                                "--no-owner",
                                "--no-privileges",
                                "--exit-on-error",
                                "/tmp/bliss-workflow.dump");
                assertEquals(0, restore.getExitCode(), "Owned database restore failed");
                assertEquals(
                        original,
                        snapshot(restored),
                        "All public table row counts/digests must survive restore");
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
                assertEquals(original, snapshot(source));
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
            Files.deleteIfExists(backup);
        }
        assertFalse(Files.exists(backup), "Temporary synthetic backup must be removed");
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

    private long scalar(PostgreSQLContainer db, String sql) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery(sql)) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private Map<String, String> snapshot(PostgreSQLContainer db) throws Exception {
        var result = new TreeMap<String, String>();
        try (var connection =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            var tables = new ArrayList<String>();
            try (var rows =
                    statement.executeQuery(
                            "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name")) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            for (String table : tables) {
                assertTrue(table.matches("[a-z_]+"));
                // Digests stay in memory; never emit records or serialized Flow payloads.
                try (var rows =
                        statement.executeQuery(
                                "SELECT count(*),md5(coalesce(string_agg(row_to_json(t)::text, E'\\n' ORDER BY row_to_json(t)::text),'')) FROM public.\""
                                        + table
                                        + "\" t")) {
                    assertTrue(rows.next());
                    result.put(table, rows.getLong(1) + ":" + rows.getString(2));
                }
            }
        }
        return result;
    }
}
