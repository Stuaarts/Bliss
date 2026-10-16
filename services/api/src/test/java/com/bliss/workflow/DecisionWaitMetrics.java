package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Shared HTTP assertions and reversible history faults for owned restart fixtures only. */
final class DecisionWaitMetrics {
    enum Kind {
        BOOKING(
                "bliss_workflow_booking_waiting_",
                "bookings",
                "booking_status_history",
                "booking_id",
                Map.of("provider_decision", "WAIT_PROVIDER", "customer_decision", "WAIT_CUSTOMER")),
        DISPUTE(
                "bliss_workflow_dispute_waiting_",
                "cases",
                "dispute_history",
                "dispute_id",
                Map.of("party_evidence", "COLLECTING_EVIDENCE", "admin_review", "UNDER_REVIEW"));
        final String prefix, countName, historyTable, recordColumn;
        final Map<String, String> states;

        Kind(
                String prefix,
                String countName,
                String historyTable,
                String recordColumn,
                Map<String, String> states) {
            this.prefix = prefix;
            this.countName = countName;
            this.historyTable = historyTable;
            this.recordColumn = recordColumn;
            this.states = states;
        }
    }

    private final WorkflowRestartIT api;
    private final Kind kind;

    DecisionWaitMetrics(WorkflowRestartIT api, Kind kind) {
        this.api = api;
        this.kind = kind;
    }

    double await(String selected, boolean missingHistory) throws Exception {
        assertTrue(selected == null || kind.states.containsKey(selected));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        do {
            var response =
                    api.client.send(
                            HttpRequest.newBuilder(api.uri("/q/metrics"))
                                    .timeout(Duration.ofSeconds(5))
                                    .header(
                                            "Authorization",
                                            "Bearer "
                                                    + TestIdentityProvider.token(
                                                            "waiting-probe", "operator"))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            var values = new HashMap<String, Double>();
            for (String line : response.body().split("\n")) {
                if (!line.startsWith(kind.prefix)) continue;
                var parts = line.trim().split("\\s+");
                assertEquals(2, parts.length);
                values.put(parts[0], Double.parseDouble(parts[1]));
            }
            var expectedNames = new HashSet<String>();
            boolean matches = true;
            double age = 0;
            for (String stage : kind.states.keySet()) {
                String tag = "{stage=\"" + stage + "\"}";
                for (String name : List.of(kind.countName, "oldest_seconds", "history_missing"))
                    expectedNames.add(kind.prefix + name + tag);
                boolean active = stage.equals(selected);
                double count = values.getOrDefault(kind.prefix + kind.countName + tag, Double.NaN);
                double oldest =
                        values.getOrDefault(kind.prefix + "oldest_seconds" + tag, Double.NaN);
                double missing =
                        values.getOrDefault(kind.prefix + "history_missing" + tag, Double.NaN);
                matches &=
                        count == (active ? 1 : 0) && missing == (active && missingHistory ? 1 : 0);
                matches &=
                        active ? (missingHistory ? Double.isNaN(oldest) : oldest > 0) : oldest == 0;
                if (active) age = oldest;
            }
            assertEquals(
                    expectedNames,
                    values.keySet(),
                    "Six code-defined stage samples without private labels");
            if (matches) return age;
            assertTrue(api.server.isAlive());
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        fail("Wait metrics did not reach expected saved state/history condition");
        return Double.NaN;
    }

    void exerciseMissingHistory(PostgreSQLContainer db, String record, String stage)
            throws Exception {
        String status = Objects.requireNonNull(kind.states.get(stage));
        UUID recordId = UUID.fromString(record), historyId;
        Object actor, occurred;
        try (var connection =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT id,actor_user_id,occurred_at FROM "
                                        + kind.historyTable
                                        + " WHERE "
                                        + kind.recordColumn
                                        + "=? AND status=?")) {
            statement.setQueryTimeout(10);
            statement.setObject(1, recordId);
            statement.setString(2, status);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                historyId = rows.getObject(1, UUID.class);
                actor = rows.getObject(2);
                occurred = rows.getObject(3);
                assertFalse(rows.next());
            }
        }
        // Tables come only from Kind; the caller supplies its own fresh container and API ID.
        try {
            try (var connection =
                            DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement =
                            connection.prepareStatement(
                                    "DELETE FROM " + kind.historyTable + " WHERE id=?")) {
                statement.setQueryTimeout(10);
                statement.setObject(1, historyId);
                assertEquals(1, statement.executeUpdate());
            }
            await(stage, true);
        } finally {
            try (var connection =
                            DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement =
                            connection.prepareStatement(
                                    "INSERT INTO "
                                            + kind.historyTable
                                            + "(id,"
                                            + kind.recordColumn
                                            + ",status,actor_user_id,occurred_at) VALUES (?,?,?,?,?) ON CONFLICT (id) DO UPDATE SET "
                                            + kind.recordColumn
                                            + "=EXCLUDED."
                                            + kind.recordColumn
                                            + ",status=EXCLUDED.status,actor_user_id=EXCLUDED.actor_user_id,occurred_at=EXCLUDED.occurred_at")) {
                statement.setQueryTimeout(10);
                statement.setObject(1, historyId);
                statement.setObject(2, recordId);
                statement.setString(3, status);
                statement.setObject(4, actor);
                statement.setObject(5, occurred);
                assertEquals(1, statement.executeUpdate());
            }
        }
        await(stage, false);
    }
}
