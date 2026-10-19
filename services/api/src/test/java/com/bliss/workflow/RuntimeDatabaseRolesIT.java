package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Dedicated synthetic roles: migrate separately, then run real workflows without DDL authority. */
class RuntimeDatabaseRolesIT {
    private static final String RUNTIME = "bliss_runtime";
    private static final String MIGRATOR = "bliss_migrator";
    private final String runtimePassword = UUID.randomUUID().toString();
    private final String migrationPassword = UUID.randomUUID().toString();

    @Test
    @Timeout(120)
    void runtimeCannotUpgradeSchemaAndGrantPolicyRefusesInvalidTargets() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            provision(db);
            migrate(db, "10");
            applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
            assertEquals(
                    "10", value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
            assertThrows(
                    org.flywaydb.core.api.FlywayException.class,
                    () ->
                            Flyway.configure()
                                    .dataSource(db.getJdbcUrl(), RUNTIME, runtimePassword)
                                    .load()
                                    .migrate());
            assertEquals(
                    "10", value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
            assertEquals(
                    "0",
                    value(
                            db,
                            "SELECT count(*)::text FROM information_schema.columns WHERE table_schema='public' AND table_name='booking' AND column_name='workflow_failed_at'"));
            migrate(db, "latest");
            applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
            applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
            assertRuntimeBoundary(db);
            String before =
                    value(db, "SELECT relacl::text FROM pg_class WHERE oid='booking'::regclass");
            applyPolicy(db, RUNTIME, "unexpected_database", false);
            applyPolicy(
                    db, MIGRATOR, db.getDatabaseName(), false); // Runtime must not own the schema.
            applyPolicy(db, db.getUsername(), db.getDatabaseName(), false);
            admin(db, "GRANT bliss_migrator TO bliss_runtime");
            applyPolicy(db, RUNTIME, db.getDatabaseName(), false);
            admin(db, "REVOKE bliss_migrator FROM bliss_runtime");
            assertRuntimeBoundary(db);
            assertEquals(
                    before,
                    value(db, "SELECT relacl::text FROM pg_class WHERE oid='booking'::regclass"));
            assertEquals(
                    "11", value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
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
                provision(db);
                migrate(db, "latest");
                applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
                assertRuntimeBoundary(db);
                // Revoke the migrator's ability to connect before either packaged API starts.
                admin(db, "ALTER ROLE bliss_migrator NOLOGIN");
                assertThrows(
                        SQLException.class,
                        () ->
                                DriverManager.getConnection(
                                        db.getJdbcUrl(), MIGRATOR, migrationPassword));
                var config = new HashMap<>(identity.start());
                config.put("quarkus.flyway.migrate-at-start", "false");
                config.put("quarkus.flyway.validate-at-start", "true");
                config.put("bliss.operations.backlog.every", "1s");
                try (var socket = new ServerSocket(0)) {
                    api.port = socket.getLocalPort();
                }
                api.start(db, config, RUNTIME, runtimePassword);
                assertRuntimeSessions(db);
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
                api.start(db, config, RUNTIME, runtimePassword);
                assertRuntimeSessions(db);
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
                assertEquals("2", value(db, "SELECT count(*)::text FROM payment_transaction"));
                assertEquals(
                        "1",
                        value(
                                db,
                                "SELECT count(*)::text FROM dispute_decision WHERE processed_at IS NOT NULL"));
                assertRuntimeBoundary(db);
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

    private void provision(PostgreSQLContainer db) throws Exception {
        // Passwords are generated UUIDs (no quote characters), only for this owned container.
        admin(
                db,
                "CREATE ROLE "
                        + MIGRATOR
                        + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '"
                        + migrationPassword
                        + "'");
        admin(
                db,
                "CREATE ROLE "
                        + RUNTIME
                        + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '"
                        + runtimePassword
                        + "'");
        admin(db, "ALTER SCHEMA public OWNER TO bliss_migrator");
    }

    private void migrate(PostgreSQLContainer db, String target) {
        Flyway.configure()
                .dataSource(db.getJdbcUrl(), MIGRATOR, migrationPassword)
                .target(target)
                .load()
                .migrate();
    }

    private void applyPolicy(
            PostgreSQLContainer db, String runtime, String database, boolean success)
            throws Exception {
        db.copyFileToContainer(
                MountableFile.forHostPath(
                        Path.of("../../infrastructure/postgresql/grant-runtime.sql")),
                "/tmp/bliss-runtime-grants.sql");
        var result =
                db.execInContainer(
                        "psql",
                        "-X",
                        "--quiet",
                        "--username",
                        db.getUsername(),
                        "--dbname",
                        db.getDatabaseName(),
                        "--set=database=" + database,
                        "--set=runtime_role=" + runtime,
                        "--set=migration_role=" + MIGRATOR,
                        "--file=/tmp/bliss-runtime-grants.sql");
        assertEquals(success ? 0 : 3, result.getExitCode(), "Owned role policy result");
    }

    private void assertRuntimeBoundary(PostgreSQLContainer db) throws Exception {
        try (var c = DriverManager.getConnection(db.getJdbcUrl(), RUNTIME, runtimePassword)) {
            assertEquals(RUNTIME, query(c, "SELECT current_user"));
            assertEquals(
                    "11", query(c, "SELECT max(version::int)::text FROM flyway_schema_history"));
            for (String sql :
                    List.of(
                            "CREATE SCHEMA forbidden_runtime_schema",
                            "CREATE TABLE public.forbidden_runtime_table(id int)",
                            "ALTER TABLE public.booking ADD COLUMN forbidden_runtime_column text",
                            "DROP TABLE public.service_category",
                            "CREATE TEMP TABLE forbidden_runtime_temp(id int)",
                            "TRUNCATE public.booking",
                            "SET ROLE bliss_migrator",
                            "GRANT bliss_migrator TO bliss_runtime",
                            "ALTER ROLE bliss_runtime SUPERUSER",
                            "UPDATE public.flyway_schema_history SET checksum=checksum WHERE false",
                            "DELETE FROM public.flyway_schema_history WHERE false",
                            "INSERT INTO public.flyway_schema_history SELECT * FROM public.flyway_schema_history WHERE false")) {
                try (var s = c.createStatement()) {
                    s.setQueryTimeout(10);
                    assertEquals(
                            "42501",
                            assertThrows(SQLException.class, () -> s.execute(sql)).getSQLState(),
                            "Restricted operation must fail with insufficient privilege");
                }
            }
        }
    }

    private void assertRuntimeSessions(PostgreSQLContainer db) throws Exception {
        assertEquals(
                "true",
                value(
                        db,
                        "SELECT (count(*)>0)::text FROM pg_stat_activity WHERE usename='bliss_runtime' AND datname=current_database()"));
        assertEquals(
                "0",
                value(
                        db,
                        "SELECT count(*)::text FROM pg_stat_activity WHERE usename='bliss_migrator' AND datname=current_database()"));
    }

    private void admin(PostgreSQLContainer db, String sql) throws Exception {
        try (var c =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var s = c.createStatement()) {
            s.setQueryTimeout(10);
            s.execute(sql);
        }
    }

    private String value(PostgreSQLContainer db, String sql) throws Exception {
        try (var c =
                DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
            return query(c, sql);
        }
    }

    private String query(Connection c, String sql) throws Exception {
        try (var s = c.createStatement()) {
            s.setQueryTimeout(10);
            try (var rows = s.executeQuery(sql)) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }
}
