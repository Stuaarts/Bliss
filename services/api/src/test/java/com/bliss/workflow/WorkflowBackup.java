package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Quiesced, same-version backup into an empty owned target; never accepts an external archive. */
final class WorkflowBackup {
    private WorkflowBackup() {}

    static Map<String, String> restore(
            WorkflowRestartIT api, PostgreSQLContainer source, PostgreSQLContainer restored)
            throws Exception {
        assertNotNull(api.server);
        assertFalse(api.server.isAlive(), "The sole source worker must exit before snapshotting");
        assertTrue(source.isRunning());
        assertFalse(restored.isRunning(), "Restore must create its own new destination");
        Path backup = Files.createTempFile(Path.of("target"), "workflow-backup-", ".dump");
        Map<String, String> original;
        try {
            original = snapshot(source);
            assertTrue(original.size() >= 28, "Snapshot must include all public base tables");
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
            assertTrue(snapshot(restored).isEmpty(), "Restore destination must be empty");
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
            assertTrue(
                    original.equals(snapshot(restored)),
                    "All table row counts/digests must survive restore");
        } finally {
            Files.deleteIfExists(backup);
        }
        assertFalse(Files.exists(backup), "Temporary synthetic backup must be removed");
        return original;
    }

    static long scalar(PostgreSQLContainer db, String sql) throws Exception {
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

    static Map<String, String> snapshot(PostgreSQLContainer db) throws Exception {
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
                // Comparison only: never emit records, digests or serialized Flow payloads.
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
