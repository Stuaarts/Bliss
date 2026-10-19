package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Kills a real packaged API, preserving its database, then proves restoration through HTTP. */
class WorkflowRestartIT {
    final ObjectMapper json = new ObjectMapper();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    Process server;
    int port;
    private final DecisionWaitMetrics waits =
            new DecisionWaitMetrics(this, DecisionWaitMetrics.Kind.BOOKING);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingBookingSurvivesAbruptProcessRestart(boolean complete) throws Exception {
        Path log = Path.of("target/restart-check-server.log");
        int logStart = Files.exists(log) ? Files.readString(log).length() : 0;
        var identity = new TestIdentityProvider();
        var oidc = new HashMap<>(identity.start());
        oidc.put("bliss.operations.backlog.every", "1s");
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            try (var socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            start(db, oidc);
            String customer = TestIdentityProvider.token("restart-customer", "customer");
            String provider = TestIdentityProvider.token("restart-provider", "provider");
            call("POST", "/api/v1/me/bootstrap", customer, null, null, 200);
            call("POST", "/api/v1/me/bootstrap", provider, null, null, 200);
            String providerId =
                    call(
                                    "POST",
                                    "/api/v1/providers/me",
                                    provider,
                                    Map.of(
                                            "displayName",
                                            "Restart provider",
                                            "type",
                                            "INDIVIDUAL",
                                            "description",
                                            "Demo",
                                            "city",
                                            "Toronto"),
                                    null,
                                    201)
                            .path("id")
                            .asText();
            String booking;
            if (complete) {
                String offering =
                        call(
                                        "POST",
                                        "/api/v1/providers/me/" + providerId + "/offerings",
                                        provider,
                                        Map.of(
                                                "title",
                                                "Direct restart job",
                                                "description",
                                                "Fixed scope",
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
                booking =
                        call(
                                        "POST",
                                        "/api/v1/offerings/" + offering + "/bookings",
                                        customer,
                                        Map.of("offeringVersion", 0, "city", "Toronto"),
                                        UUID.randomUUID().toString(),
                                        201)
                                .path("id")
                                .asText();
            } else {
                String request =
                        call(
                                        "POST",
                                        "/api/v1/service-requests",
                                        customer,
                                        Map.of(
                                                "title",
                                                "Restart job",
                                                "description",
                                                "Durability check",
                                                "city",
                                                "Toronto",
                                                "categoryId",
                                                "10000000-0000-0000-0000-000000000004",
                                                "currency",
                                                "CAD"),
                                        null,
                                        201)
                                .path("id")
                                .asText();
                String quote =
                        call(
                                        "POST",
                                        "/api/v1/service-requests/" + request + "/quotes",
                                        provider,
                                        Map.of(
                                                "providerId",
                                                providerId,
                                                "description",
                                                "Scope",
                                                "price",
                                                "80.50",
                                                "currency",
                                                "CAD",
                                                "expiresAt",
                                                Instant.now().plusSeconds(3600).toString()),
                                        null,
                                        201)
                                .path("id")
                                .asText();
                booking =
                        call(
                                        "POST",
                                        "/api/v1/quotes/" + quote + "/accept",
                                        customer,
                                        null,
                                        null,
                                        200)
                                .path("id")
                                .asText();
            }
            JsonNode waiting = awaitStatus(booking, customer, "WAIT_PROVIDER");
            String instance = waiting.path("workflowInstanceId").asText();
            assertFalse(instance.isBlank());
            double providerAge = waits.await("provider_decision", false);
            if (!complete) waits.exerciseMissingHistory(db, booking, "provider_decision");
            // Domain status precedes asynchronous task persistence. Wait for an actual
            // completed loop, not a fixed sleep, before taking the crash snapshot.
            awaitLoopCheckpoints(db, instance);
            stop();
            try (var c =
                            DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var s =
                            c.prepareStatement(
                                    "SELECT count(*) FROM workflow_instance_entity WHERE instance_id=?")) {
                s.setString(1, instance);
                try (var r = s.executeQuery()) {
                    r.next();
                    assertEquals(1, r.getInt(1));
                }
            }
            try (var c =
                            DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var s =
                            c.prepareStatement(
                                    "SELECT count(*) FROM task_info_entity WHERE workflow_instance_id=?")) {
                s.setString(1, instance);
                try (var r = s.executeQuery()) {
                    r.next();
                    assertEquals(3, r.getInt(1), "One checkpoint per loop task");
                }
            }
            start(db, oidc);
            assertEquals(
                    instance,
                    awaitStatus(booking, customer, "WAIT_PROVIDER")
                            .path("workflowInstanceId")
                            .asText());
            assertTrue(
                    waits.await("provider_decision", false) >= providerAge,
                    "Provider wait age must survive process restart");
            String key = UUID.randomUUID().toString();
            String action = "provider-reject", actor = provider, terminal = "CANCELLED";
            if (complete) {
                // Leave room for HTTP/JWT processing on a busy CI runner. Rounding a
                // one-second lead down to whole seconds could make it past on arrival.
                call(
                        "POST",
                        "/api/v1/bookings/" + booking + "/provider-accept",
                        provider,
                        Map.of(
                                "scheduledAt",
                                Instant.now()
                                        .plusSeconds(10)
                                        .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                                        .toString()),
                        UUID.randomUUID().toString(),
                        202);
                var scheduled = awaitStatus(booking, customer, "SCHEDULED");
                long untilStart =
                        Duration.between(
                                        Instant.now(),
                                        Instant.parse(scheduled.path("scheduledAt").asText()))
                                .toMillis();
                if (untilStart >= 0) Thread.sleep(untilStart + 10);
                call(
                        "POST",
                        "/api/v1/bookings/" + booking + "/start",
                        provider,
                        null,
                        UUID.randomUUID().toString(),
                        202);
                awaitStatus(booking, customer, "IN_PROGRESS");
                call(
                        "POST",
                        "/api/v1/bookings/" + booking + "/mark-complete",
                        provider,
                        null,
                        UUID.randomUUID().toString(),
                        202);
                awaitStatus(booking, customer, "WAIT_CUSTOMER");
                double customerAge = waits.await("customer_decision", false);
                Thread.sleep(4500);
                stop();
                start(db, oidc);
                assertEquals(
                        instance,
                        awaitStatus(booking, customer, "WAIT_CUSTOMER")
                                .path("workflowInstanceId")
                                .asText());
                assertTrue(
                        waits.await("customer_decision", false) >= customerAge,
                        "Customer wait age must survive process restart");
                action = "confirm-completion";
                actor = customer;
                terminal = "COMPLETED";
            }
            call("POST", "/api/v1/bookings/" + booking + "/" + action, actor, null, key, 202);
            waits.await(null, false);
            assertEquals(
                    instance,
                    awaitStatus(booking, customer, terminal).path("workflowInstanceId").asText());
            call("POST", "/api/v1/bookings/" + booking + "/" + action, actor, null, key, 202);
            try (var c =
                            DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var s =
                            c.prepareStatement(
                                    "SELECT operation,count(*) FROM payment_transaction WHERE booking_id=? GROUP BY operation")) {
                s.setObject(1, UUID.fromString(booking));
                var counts = new HashMap<String, Integer>();
                try (var r = s.executeQuery()) {
                    while (r.next()) counts.put(r.getString(1), r.getInt(2));
                }
                assertEquals(Map.of("AUTHORIZE", 1, complete ? "CAPTURE" : "VOID", 1), counts);
            }
            System.out.printf(
                    "Booking waiting metrics proof: provider=true customer=%s missing_history_exercised=%s age_survives_restart=true terminal_removed=true fixed_stages=2 private_labels=false; simulated_settlement_once=true.%n",
                    complete, !complete);
        } finally {
            stop();
            identity.stop();
        }
        String runLog = Files.readString(log).substring(logStart);
        assertFalse(
                runLog.contains("Failed to initialize and warm up workflows"),
                "Workflow restoration failed; inspect restart log");
        assertFalse(
                runLog.contains("ARJUNA012094"),
                "Restoration leaked a transaction to worker threads");
        assertFalse(
                runLog.contains("Enlisted connection used without active transaction"),
                "Restoration transaction rolled back");
    }

    void awaitLoopCheckpoints(PostgreSQLContainer db, String instance) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        int count = 0;
        try (var connection =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM task_info_entity WHERE workflow_instance_id=?")) {
            statement.setString(1, instance);
            do {
                try (var result = statement.executeQuery()) {
                    result.next();
                    count = result.getInt(1);
                }
                if (count == 3) return;
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
        }
        assertEquals(
                3, count, "Workflow did not persist one checkpoint per loop task before crash");
    }

    void start(PostgreSQLContainer db, Map<String, String> oidc) throws Exception {
        start(db, oidc, db.getUsername(), db.getPassword());
    }

    void start(PostgreSQLContainer db, Map<String, String> oidc, String username, String password)
            throws Exception {
        var args = new ArrayList<String>();
        args.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        args.add("-Dquarkus.http.port=" + port);
        // Explicit properties isolate this process from CI's unit-test database environment.
        args.add("-Dquarkus.datasource.jdbc.url=" + db.getJdbcUrl());
        args.add("-Dquarkus.datasource.username=" + username);
        args.add("-Dquarkus.datasource.password=" + password);
        oidc.forEach((k, v) -> args.add("-D" + k + "=" + v));
        args.add("-jar");
        args.add("target/quarkus-app/quarkus-run.jar");
        server =
                new ProcessBuilder(args)
                        .redirectErrorStream(true)
                        .redirectOutput(
                                ProcessBuilder.Redirect.appendTo(
                                        Path.of("target/restart-check-server.log").toFile()))
                        .start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline && server.isAlive()) {
            try {
                var response =
                        client.send(
                                HttpRequest.newBuilder(uri("/q/health/ready"))
                                        .timeout(Duration.ofSeconds(2))
                                        .header(
                                                "Authorization",
                                                "Bearer "
                                                        + TestIdentityProvider.token(
                                                                "restart-probe", "operator"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return;
            } catch (java.io.IOException ignored) {
            }
            Thread.sleep(150);
        }
        fail("Packaged API did not become ready; inspect target/restart-check-server.log");
    }

    void stop() throws InterruptedException {
        if (server != null && server.isAlive()) {
            server.destroyForcibly();
            assertTrue(server.waitFor(10, TimeUnit.SECONDS), "Own API process did not exit");
        }
    }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    JsonNode call(String method, String path, String token, Object body, String key, int expected)
            throws Exception {
        var req =
                HttpRequest.newBuilder(uri(path))
                        .timeout(Duration.ofSeconds(5))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json");
        if (key != null) req.header("Idempotency-Key", key);
        req.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), method + " " + path + ": " + response.body());
        return json.readTree(response.body());
    }

    JsonNode awaitStatus(String booking, String customer, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        JsonNode result;
        do {
            result = call("GET", "/api/v1/bookings/" + booking, customer, null, null, 200);
            if (expected.equals(result.path("status").asText())) return result;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertEquals(
                expected,
                result.path("status").asText(),
                "Workflow failed to resume; inspect restart-check-server.log");
        return result;
    }
}
