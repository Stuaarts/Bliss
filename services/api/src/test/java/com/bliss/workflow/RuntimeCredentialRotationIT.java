package com.bliss.workflow;

import static com.bliss.workflow.RuntimeDatabaseFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Revoke only this container's generated login and sessions, with its worker fenced. */
class RuntimeCredentialRotationIT {
    private final WorkflowRestartIT api = new WorkflowRestartIT();
    private final RuntimeDatabaseFixture roles = new RuntimeDatabaseFixture();

    @Test
    @Timeout(300)
    void rotatedCredentialRestoresWaitingDisputeAfterExplicitSessionRevocation() throws Exception {
        var identity = new TestIdentityProvider();
        String rotated = UUID.randomUUID().toString();
        try (var db =
                new PostgreSQLContainer("postgres:17.6-alpine")
                        .withEnv("POSTGRES_HOST_AUTH_METHOD", "scram-sha-256")
                        .withEnv("POSTGRES_INITDB_ARGS", "--auth-host=scram-sha-256")) {
            try {
                db.start();
                roles.provision(db);
                roles.migrate(db, "latest");
                roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
                assertEquals(
                        "0",
                        roles.value(
                                db,
                                "SELECT count(*)::text FROM pg_hba_file_rules WHERE type LIKE 'host%' AND auth_method <> 'scram-sha-256'"));
                deniedConnection(db, UUID.randomUUID().toString(), "28P01");
                roles.admin(db, "ALTER ROLE bliss_migrator NOLOGIN");
                var config = new HashMap<>(identity.start());
                config.put("quarkus.flyway.migrate-at-start", "false");
                config.put("quarkus.flyway.validate-at-start", "true");
                config.put("bliss.operations.backlog.every", "1s");
                config.put("quarkus.datasource.jdbc.acquisition-timeout", "2s");
                try (var socket = new ServerSocket(0)) {
                    api.port = socket.getLocalPort();
                }
                api.start(db, config, RUNTIME, roles.runtimePassword);
                var f = DisputeRecoveryFixture.create(api, false);
                String path = "/api/v1/disputes/" + f.dispute();
                String instance =
                        DisputeRestartIT.await(
                                        api, f.dispute(), f.customer(), "COLLECTING_EVIDENCE", true)
                                .path("workflowInstanceId")
                                .asText();
                api.awaitLoopCheckpoints(db, instance);
                var waits = new DecisionWaitMetrics(api, DecisionWaitMetrics.Kind.DISPUTE);
                double age = waits.await("party_evidence", false);

                // Stop the actual worker before touching authentication. The probe API restores
                // no Flow executions and claims no dispatch work; ingress is absent in this drill.
                api.stop();
                var paused = new HashMap<>(config);
                paused.put("bliss.workflow.dispatch-enabled", "false");
                paused.put("quarkus.flow.persistence.auto-restore", "false");
                api.start(db, paused, RUNTIME, roles.runtimePassword);
                long oldProcess = api.server.pid();
                var fencedState = WorkflowBackup.snapshot(db);
                try (var existing =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), RUNTIME, roles.runtimePassword)) {
                    String backend = roles.query(existing, "SELECT pg_backend_pid()::text");
                    // UUIDs are generated internally, contain no quotes, and never enter proof
                    // logs.
                    roles.admin(db, "ALTER ROLE bliss_runtime PASSWORD '" + rotated + "'");
                    deniedConnection(db, roles.runtimePassword, "28P01");
                    try (var fresh =
                            DriverManager.getConnection(db.getJdbcUrl(), RUNTIME, rotated)) {
                        assertEquals(RUNTIME, roles.query(fresh, "SELECT current_user"));
                    }
                    roles.admin(db, "ALTER ROLE bliss_runtime NOLOGIN");
                    deniedConnection(db, rotated, "28000");
                    assertEquals(backend, roles.query(existing, "SELECT pg_backend_pid()::text"));
                    assertEquals("1", roles.query(existing, "SELECT 1"));
                    health("/q/health/ready", 200, "UP");
                    assertEquals(oldProcess, api.server.pid());

                    // This administrator belongs only to the disposable database. Bound both
                    // database and role: do not terminate admin/other-role sessions.
                    try (var admin =
                                    DriverManager.getConnection(
                                            db.getJdbcUrl(), db.getUsername(), db.getPassword());
                            var terminate =
                                    admin.prepareStatement(
                                            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=? AND usename=? AND pid<>pg_backend_pid()")) {
                        terminate.setQueryTimeout(10);
                        terminate.setString(1, db.getDatabaseName());
                        terminate.setString(2, RUNTIME);
                        int terminated = 0;
                        try (var rows = terminate.executeQuery()) {
                            while (rows.next()) {
                                assertTrue(rows.getBoolean(1));
                                terminated++;
                            }
                        }
                        assertTrue(terminated >= 2, "Revoke the observer and API pool sessions");
                    }
                    assertThrows(SQLException.class, () -> roles.query(existing, "SELECT 1"));
                    awaitNotReady();
                    health("/q/health/live", 200, "UP");
                    assertTrue(api.server.isAlive());
                    assertEquals(oldProcess, api.server.pid());
                    assertEquals(
                            "0",
                            roles.value(
                                    db,
                                    "SELECT count(*)::text FROM pg_stat_activity WHERE datname=current_database() AND usename='bliss_runtime'"));
                }
                api.stop();
                assertEquals(
                        fencedState,
                        WorkflowBackup.snapshot(db),
                        "Fenced database records changed during credential revocation");
                roles.admin(db, "ALTER ROLE bliss_runtime LOGIN");
                deniedConnection(db, roles.runtimePassword, "28P01");
                api.start(db, config, RUNTIME, rotated);
                assertNotEquals(oldProcess, api.server.pid());
                roles.assertRuntimeSessions(db);
                roles.assertRuntimeBoundary(db, rotated);
                assertEquals(
                        instance,
                        DisputeRestartIT.await(
                                        api, f.dispute(), f.customer(), "COLLECTING_EVIDENCE", true)
                                .path("workflowInstanceId")
                                .asText());
                assertTrue(waits.await("party_evidence", false) >= age);
                assertEquals(
                        1,
                        api.call("GET", path + "/evidence", f.customer(), null, null, 200)
                                .path("total")
                                .asInt());
                assertEquals(
                        "0",
                        roles.value(
                                db,
                                "SELECT count(*)::text FROM dispute WHERE workflow_failed_at IS NOT NULL"));
                api.call(
                        "POST",
                        path + "/evidence",
                        f.provider(),
                        Map.of("body", "Synthetic evidence after credential rotation"),
                        UUID.randomUUID().toString(),
                        200);
                DisputeRestartIT.await(api, f.dispute(), f.admin(), "UNDER_REVIEW", true);
                String key = UUID.randomUUID().toString();
                var decision =
                        Map.of(
                                "outcome",
                                "PROVIDER_FAVORED",
                                "rationale",
                                "Independent synthetic rotation review");
                api.call("POST", path + "/decision", f.admin(), decision, key, 202);
                var resolved =
                        DisputeRestartIT.await(
                                api, f.dispute(), f.admin(), "RESOLVED_PROVIDER", false);
                assertEquals(instance, resolved.path("workflowInstanceId").asText());
                assertTrue(
                        api.call("POST", path + "/decision", f.admin(), decision, key, 202)
                                .path("processed")
                                .asBoolean());
                api.awaitStatus(f.booking(), f.customer(), "COMPLETED");
                var payment =
                        api.call(
                                "GET",
                                "/api/v1/bookings/" + f.booking() + "/payment",
                                f.customer(),
                                null,
                                null,
                                200);
                assertTrue(payment.path("simulated").asBoolean());
                assertEquals("CAPTURED", payment.path("status").asText());
                assertEquals(
                        List.of("AUTHORIZE", "CAPTURE"),
                        api.json.convertValue(payment.path("operations"), List.class));
                assertEquals(
                        "2", roles.value(db, "SELECT count(*)::text FROM payment_transaction"));
                System.out.println(
                        "Credential rotation proof: scram=true wrong_password_denied=true old_password_denied=true nologin_denied=true existing_session_survives_auth_changes=true worker_fenced=true records_unchanged_during_revocation=true sessions_terminated=true old_api_ready_down_live_up=true rotated_api_ready=true same_instance=true age_retained=true evidence_retained=true no_failure_marker=true decision_replay=true authorize=1 capture=1 restricted_permissions_retained=true; owned synthetic database/identity/payment only.");
            } finally {
                api.stop();
            }
        } finally {
            identity.stop();
            api.client.close();
        }
    }

    private void deniedConnection(PostgreSQLContainer db, String password, String state) {
        assertEquals(
                state,
                assertThrows(
                                SQLException.class,
                                () -> {
                                    try (var unexpected =
                                            DriverManager.getConnection(
                                                    db.getJdbcUrl(), RUNTIME, password)) {
                                        fail("Owned login unexpectedly connected");
                                    }
                                })
                        .getSQLState(),
                "Authentication refusal must come from PostgreSQL");
    }

    private void awaitNotReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        do {
            var response = request("/q/health/ready");
            if (response.statusCode() == 503) {
                var body = api.json.readTree(response.body());
                assertEquals("DOWN", body.path("status").asText());
                for (var check : body.path("checks")) {
                    if ("Database connections health check".equals(check.path("name").asText())) {
                        assertEquals("DOWN", check.path("status").asText());
                        return;
                    }
                }
                fail("Missing database health check");
            }
            assertEquals(200, response.statusCode());
            assertTrue(api.server.isAlive());
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        fail("Revoked probe API did not become unready");
    }

    private void health(String path, int status, String state) throws Exception {
        var response = request(path);
        assertEquals(status, response.statusCode());
        assertEquals(state, api.json.readTree(response.body()).path("status").asText());
    }

    private HttpResponse<String> request(String path) throws Exception {
        return api.client.send(
                HttpRequest.newBuilder(api.uri(path))
                        .timeout(Duration.ofSeconds(5))
                        .header(
                                "Authorization",
                                "Bearer "
                                        + TestIdentityProvider.token("rotation-probe", "operator"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
