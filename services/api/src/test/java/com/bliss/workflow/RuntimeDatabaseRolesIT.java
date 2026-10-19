package com.bliss.workflow;

import static com.bliss.workflow.RuntimeDatabaseFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Dedicated synthetic roles: migrate separately, then run real workflows without DDL authority. */
class RuntimeDatabaseRolesIT {
    private final RuntimeDatabaseFixture roles = new RuntimeDatabaseFixture();

    @Test
    @Timeout(120)
    void runtimeCannotUpgradeSchemaAndGrantPolicyRefusesInvalidTargets() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            roles.provision(db);
            roles.migrate(db, "10");
            roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
            assertEquals(
                    "10",
                    roles.value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
            assertThrows(
                    org.flywaydb.core.api.FlywayException.class,
                    () ->
                            Flyway.configure()
                                    .dataSource(db.getJdbcUrl(), RUNTIME, roles.runtimePassword)
                                    .load()
                                    .migrate());
            assertEquals(
                    "10",
                    roles.value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
            assertEquals(
                    "0",
                    roles.value(
                            db,
                            "SELECT count(*)::text FROM information_schema.columns WHERE table_schema='public' AND table_name='booking' AND column_name='workflow_failed_at'"));
            roles.migrate(db, "latest");
            roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
            roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
            roles.assertRuntimeBoundary(db);
            String before =
                    roles.value(
                            db, "SELECT relacl::text FROM pg_class WHERE oid='booking'::regclass");
            roles.applyPolicy(db, RUNTIME, "unexpected_database", false);
            roles.applyPolicy(
                    db, MIGRATOR, db.getDatabaseName(), false); // Runtime must not own the schema.
            roles.applyPolicy(db, db.getUsername(), db.getDatabaseName(), false);
            roles.admin(db, "GRANT bliss_migrator TO bliss_runtime");
            roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), false);
            roles.admin(db, "REVOKE bliss_migrator FROM bliss_runtime");
            roles.assertRuntimeBoundary(db);
            assertEquals(
                    before,
                    roles.value(
                            db, "SELECT relacl::text FROM pg_class WHERE oid='booking'::regclass"));
            assertEquals(
                    "11",
                    roles.value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
            System.out.println(
                    "Runtime database role proof: runtime_migration_denied=true v10_preserved=true migrator_v11_upgrade=true policy_repeatable=true wrong_database_refused=true owner_role_refused=true privileged_role_refused=true member_role_refused=true ddl_history_role_temp_truncate_denied=true; owned synthetic database only.");
        }
    }

    @Test
    @Timeout(240)
    void restrictedApiRestoresDisputeAndCapturesWithoutMigrationLogin() throws Exception {
        var api = new WorkflowRestartIT();
        var identity = new TestIdentityProvider();
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            try {
                db.start();
                roles.provision(db);
                roles.migrate(db, "latest");
                roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
                roles.assertRuntimeBoundary(db);
                // Revoke the migrator's ability to connect before either packaged API starts.
                roles.admin(db, "ALTER ROLE bliss_migrator NOLOGIN");
                assertThrows(
                        SQLException.class,
                        () ->
                                DriverManager.getConnection(
                                        db.getJdbcUrl(), MIGRATOR, roles.migrationPassword));
                var config = new HashMap<>(identity.start());
                config.put("quarkus.flyway.migrate-at-start", "false");
                config.put("quarkus.flyway.validate-at-start", "true");
                config.put("bliss.operations.backlog.every", "1s");
                try (var socket = new ServerSocket(0)) {
                    api.port = socket.getLocalPort();
                }
                api.start(db, config, RUNTIME, roles.runtimePassword);
                roles.assertRuntimeSessions(db);
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
                api.stop();
                api.start(db, config, RUNTIME, roles.runtimePassword);
                roles.assertRuntimeSessions(db);
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
                api.call(
                        "POST",
                        path + "/evidence",
                        f.provider(),
                        Map.of("body", "Synthetic runtime-role evidence"),
                        UUID.randomUUID().toString(),
                        200);
                DisputeRestartIT.await(api, f.dispute(), f.admin(), "UNDER_REVIEW", true);
                String key = UUID.randomUUID().toString();
                var decision =
                        Map.of(
                                "outcome",
                                "PROVIDER_FAVORED",
                                "rationale",
                                "Independent synthetic role-separation review");
                api.call("POST", path + "/decision", f.admin(), decision, key, 202);
                var resolved =
                        DisputeRestartIT.await(
                                api, f.dispute(), f.admin(), "RESOLVED_PROVIDER", false);
                assertEquals(instance, resolved.path("workflowInstanceId").asText());
                assertTrue(resolved.path("decision").path("processed").asBoolean());
                assertTrue(
                        api.call("POST", path + "/decision", f.admin(), decision, key, 202)
                                .path("processed")
                                .asBoolean());
                api.awaitStatus(f.booking(), f.customer(), "COMPLETED");
                waits.await(null, false);
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
                assertEquals(
                        "1",
                        roles.value(
                                db,
                                "SELECT count(*)::text FROM dispute_decision WHERE processed_at IS NOT NULL"));
                roles.assertRuntimeBoundary(db);
                System.out.println(
                        "Restricted API role proof: migrator_nologin=true runtime_sessions=true flyway_validation=true actual_booking_and_dispute_writes=true checkpoint_restored=true same_instance=true age_retained=true evidence_retained=true decision_replay=true authorize=1 capture=1 privileged_operations_denied=true; synthetic identity/payment only.");
            } finally {
                api.stop();
            }
        } finally {
            identity.stop();
            api.client.close();
        }
    }
}
