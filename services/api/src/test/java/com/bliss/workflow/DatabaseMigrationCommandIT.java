package com.bliss.workflow;

import static com.bliss.workflow.RuntimeDatabaseFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.io.File;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Execute the shipped standalone class from fast-jar libraries, without Quarkus bootstrap. */
class DatabaseMigrationCommandIT {
    private final RuntimeDatabaseFixture roles = new RuntimeDatabaseFixture();

    @Test
    @Timeout(180)
    void freshMigrationExcludesDemoDataAndRestrictedApiStarts() throws Exception {
        var api = new WorkflowRestartIT();
        var identity = new TestIdentityProvider();
        try (var db = database()) {
            try {
                db.start();
                roles.provision(db);
                run(
                        db,
                        MIGRATOR,
                        roles.migrationPassword,
                        true,
                        0,
                        "bliss_migration success=true action=migrate applied=11",
                        "migrate",
                        "--database",
                        db.getDatabaseName());
                assertEquals(
                        "11",
                        roles.value(
                                db, "SELECT max(version::int)::text FROM flyway_schema_history"));
                assertEquals(
                        "11", roles.value(db, "SELECT count(*)::text FROM flyway_schema_history"));
                assertEquals(
                        "28",
                        roles.value(
                                db,
                                "SELECT count(*)::text FROM pg_tables WHERE schemaname='public'"));
                for (String table :
                        List.of("bliss_user", "provider_profile", "workflow_instance_entity")) {
                    assertEquals("0", roles.value(db, "SELECT count(*)::text FROM " + table));
                }
                run(
                        db,
                        MIGRATOR,
                        roles.migrationPassword,
                        true,
                        0,
                        "bliss_migration success=true action=migrate applied=0",
                        "migrate",
                        "--database",
                        db.getDatabaseName());
                run(
                        db,
                        MIGRATOR,
                        roles.migrationPassword,
                        true,
                        0,
                        "bliss_migration success=true action=validate applied=0",
                        "validate",
                        "--database",
                        db.getDatabaseName());
                roles.applyPolicy(db, RUNTIME, db.getDatabaseName(), true);
                roles.assertRuntimeBoundary(db);
                roles.admin(db, "ALTER ROLE bliss_migrator NOLOGIN");
                var config = new HashMap<>(identity.start());
                config.put("quarkus.flyway.migrate-at-start", "false");
                config.put("quarkus.flyway.validate-at-start", "true");
                try (var socket = new ServerSocket(0)) {
                    api.port = socket.getLocalPort();
                }
                api.start(db, config, RUNTIME, roles.runtimePassword);
                roles.assertRuntimeSessions(db);
                String customer =
                        TestIdentityProvider.token("migration-command-customer", "customer");
                api.call("POST", "/api/v1/me/bootstrap", customer, null, null, 200);
                assertEquals("1", roles.value(db, "SELECT count(*)::text FROM bliss_user"));
                assertEquals(
                        "0",
                        roles.value(db, "SELECT count(*)::text FROM workflow_instance_entity"));
                System.out.println(
                        "Migration command fresh proof: shipped_fast_jar=true schema=11 tables=28 demo_data_absent=true no_workflow_bootstrap=true repeated_migration_noop=true validation=true runtime_policy=true migrator_nologin=true restricted_api_ready_and_write=true; owned synthetic database/identity only.");
            } finally {
                api.stop();
            }
        } finally {
            identity.stop();
            api.client.close();
        }
    }

    @Test
    @Timeout(180)
    void preflightAndHistoryFailuresPreservePopulatedDatabaseBeforeUpgrade() throws Exception {
        try (var db = database()) {
            db.start();
            roles.provision(db);
            roles.migrate(db, "10");
            roles.admin(
                    db,
                    "INSERT INTO bliss_user(id,oidc_subject,display_name) VALUES ('00000000-0000-0000-0000-000000000055','migration-preserved','Synthetic preserved profile')");
            var before = WorkflowBackup.snapshot(db);
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    false,
                    2,
                    "bliss_migration success=false stage=configuration",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    3,
                    "bliss_migration success=false stage=preflight",
                    "migrate",
                    "--database",
                    "unexpected_database");
            run(
                    db,
                    RUNTIME,
                    roles.runtimePassword,
                    true,
                    3,
                    "bliss_migration success=false stage=preflight",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            run(
                    db,
                    db.getUsername(),
                    db.getPassword(),
                    true,
                    3,
                    "bliss_migration success=false stage=preflight",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            run(
                    db,
                    MIGRATOR,
                    UUID.randomUUID().toString(),
                    true,
                    3,
                    "bliss_migration success=false stage=preflight",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            for (String forbidden : List.of("clean", "repair", "baseline", "undo")) {
                run(
                        db,
                        MIGRATOR,
                        roles.migrationPassword,
                        true,
                        2,
                        "bliss_migration success=false stage=configuration",
                        forbidden,
                        "--database",
                        db.getDatabaseName());
            }
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    2,
                    "bliss_migration success=false stage=configuration",
                    "migrate",
                    "--database",
                    db.getDatabaseName(),
                    "--target=10");
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    4,
                    "bliss_migration success=false stage=validation",
                    "validate",
                    "--database",
                    db.getDatabaseName());
            roles.admin(db, "GRANT bliss_runtime TO bliss_migrator");
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    3,
                    "bliss_migration success=false stage=preflight",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            roles.admin(db, "REVOKE bliss_runtime FROM bliss_migrator");
            assertEquals(
                    before,
                    WorkflowBackup.snapshot(db),
                    "Preflight/refusal changed application records");

            String checksum =
                    roles.value(
                            db,
                            "SELECT checksum::text FROM flyway_schema_history WHERE version='1'");
            roles.admin(
                    db, "UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='1'");
            var drifted = WorkflowBackup.snapshot(db);
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    4,
                    "bliss_migration success=false stage=migration",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    4,
                    "bliss_migration success=false stage=validation",
                    "validate",
                    "--database",
                    db.getDatabaseName());
            assertEquals(
                    drifted,
                    WorkflowBackup.snapshot(db),
                    "Checksum failure changed records or history");
            assertEquals(
                    "10",
                    roles.value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
            // Only undo this fixture's injected corruption; the shipped command has no repair mode.
            roles.admin(
                    db,
                    "UPDATE flyway_schema_history SET checksum=" + checksum + " WHERE version='1'");
            assertEquals(before, WorkflowBackup.snapshot(db));
            roles.admin(
                    db,
                    "INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) VALUES (12,'99','Synthetic future','SQL','V99__synthetic_future.sql',1,'bliss_migrator',0,true)");
            var future = WorkflowBackup.snapshot(db);
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    4,
                    "bliss_migration success=false stage=migration",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            assertEquals(future, WorkflowBackup.snapshot(db));
            roles.admin(db, "DELETE FROM flyway_schema_history WHERE version='99'");
            assertEquals(before, WorkflowBackup.snapshot(db));
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    0,
                    "bliss_migration success=true action=migrate applied=1",
                    "migrate",
                    "--database",
                    db.getDatabaseName());
            assertEquals(
                    "11",
                    roles.value(db, "SELECT max(version::int)::text FROM flyway_schema_history"));
            assertEquals(
                    "Synthetic preserved profile",
                    roles.value(
                            db,
                            "SELECT display_name FROM bliss_user WHERE oidc_subject='migration-preserved'"));
            assertEquals(
                    "0",
                    roles.value(
                            db,
                            "SELECT count(*)::text FROM booking WHERE workflow_failed_at IS NOT NULL"));
            run(
                    db,
                    MIGRATOR,
                    roles.migrationPassword,
                    true,
                    0,
                    "bliss_migration success=true action=validate applied=0",
                    "validate",
                    "--database",
                    db.getDatabaseName());
            System.out.println(
                    "Migration command upgrade proof: populated_v10_to_v11=true profile_preserved=true missing_config_refused=true wrong_database_refused=true runtime_admin_member_roles_refused=true wrong_password_refused=true unsafe_commands_refused=true pending_validation_refused=true checksum_drift_refused=true future_history_refused=true failed_attempts_preserve_records=true fixed_output_without_secrets=true; owned synthetic database only.");
        }
    }

    private PostgreSQLContainer database() {
        return new PostgreSQLContainer("postgres:17.6-alpine")
                .withEnv("POSTGRES_HOST_AUTH_METHOD", "scram-sha-256")
                .withEnv("POSTGRES_INITDB_ARGS", "--auth-host=scram-sha-256");
    }

    private void run(
            PostgreSQLContainer db,
            String user,
            String password,
            boolean credentials,
            int code,
            String expected,
            String... args)
            throws Exception {
        String classpath =
                String.join(
                        File.pathSeparator,
                        "target/quarkus-app/app/*",
                        "target/quarkus-app/lib/main/*",
                        "target/quarkus-app/lib/boot/*");
        var command =
                new ArrayList<>(
                        List.of(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-cp",
                                classpath,
                                "com.bliss.operations.DatabaseMigration"));
        command.addAll(List.of(args));
        if (System.getProperty("os.name").startsWith("Windows")
                && code == 0
                && args[0].equals("validate")) {
            command =
                    new ArrayList<>(
                            List.of(
                                    "pwsh",
                                    "-NoProfile",
                                    "-File",
                                    Path.of("../../infrastructure/scripts/migrate-db.ps1")
                                            .toAbsolutePath()
                                            .toString(),
                                    "-Action",
                                    "Validate",
                                    "-Database",
                                    db.getDatabaseName(),
                                    "-ApplicationDirectory",
                                    Path.of("target/quarkus-app").toAbsolutePath().toString()));
        }
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        var env = builder.environment();
        for (String name :
                List.of(
                        "BLISS_MIGRATION_DB_URL",
                        "BLISS_MIGRATION_DB_USER",
                        "BLISS_MIGRATION_DB_PASSWORD")) env.remove(name);
        if (credentials) {
            env.put("BLISS_MIGRATION_DB_URL", db.getJdbcUrl());
            env.put("BLISS_MIGRATION_DB_USER", user);
            env.put("BLISS_MIGRATION_DB_PASSWORD", password);
        }
        // Prove unrelated runtime/local/Flyway environment cannot select seeds or another target.
        env.put("BLISS_DB_URL", "jdbc:postgresql://invalid.invalid/unused");
        env.put("QUARKUS_PROFILE", "local");
        env.put("FLYWAY_LOCATIONS", "filesystem:target/not-a-migration-source");
        env.put("FLYWAY_CLEAN_DISABLED", "false");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Standalone command did not exit");
            String output =
                    new String(
                                    process.getInputStream().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8)
                            .strip();
            Files.writeString(
                    Path.of("target/migration-command-last.log"),
                    output.replace(password, "[generated secret]"));
            assertEquals(code, process.exitValue(), "Standalone command exit status");
            // Do not echo unexpected SQL/connection/secret text through assertion diagnostics.
            assertTrue(expected.equals(output), "Command must emit only the expected fixed result");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
