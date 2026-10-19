package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Generated credentials and grants for owned, disposable role drills only. */
final class RuntimeDatabaseFixture {
    static final String RUNTIME = "bliss_runtime";
    static final String MIGRATOR = "bliss_migrator";
    final String runtimePassword = UUID.randomUUID().toString();
    final String migrationPassword = UUID.randomUUID().toString();

    void provision(PostgreSQLContainer db) throws Exception {
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

    void migrate(PostgreSQLContainer db, String target) {
        Flyway.configure()
                .dataSource(db.getJdbcUrl(), MIGRATOR, migrationPassword)
                .target(target)
                .load()
                .migrate();
    }

    void applyPolicy(PostgreSQLContainer db, String runtime, String database, boolean success)
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

    void assertRuntimeBoundary(PostgreSQLContainer db) throws Exception {
        assertRuntimeBoundary(db, runtimePassword);
    }

    void assertRuntimeBoundary(PostgreSQLContainer db, String password) throws Exception {
        try (var c = DriverManager.getConnection(db.getJdbcUrl(), RUNTIME, password)) {
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

    void assertRuntimeSessions(PostgreSQLContainer db) throws Exception {
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

    void admin(PostgreSQLContainer db, String sql) throws Exception {
        try (var c =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var s = c.createStatement()) {
            s.setQueryTimeout(10);
            s.execute(sql);
        }
    }

    String value(PostgreSQLContainer db, String sql) throws Exception {
        try (var c =
                DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
            return query(c, sql);
        }
    }

    String query(Connection c, String sql) throws Exception {
        try (var s = c.createStatement()) {
            s.setQueryTimeout(10);
            try (var rows = s.executeQuery(sql)) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }
}
