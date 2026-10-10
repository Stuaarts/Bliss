package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Bounded authenticated contention/restart evidence on an owned packaged API/database. */
class AuthenticatedWorkloadIT {
    private static final int CUSTOMERS = 8;
    private final WorkflowRestartIT api = new WorkflowRestartIT();
    private final Queue<Sample> samples = new ConcurrentLinkedQueue<>();
    private final Set<String> requestIds = ConcurrentHashMap.newKeySet();

    private record Actor(String subject, String role) {}

    private record Reply(int status, JsonNode body) {}

    private record Booking(Actor customer, String id, String cancelKey) {}

    private record Sample(String phase, int status, long nanos) {}

    @Test
    @Timeout(300)
    void concurrentRetriesAndEightWaitingWorkflowsSurviveRestartAndSettleOnce() throws Exception {
        var identity = new TestIdentityProvider();
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine");
                var pool = Executors.newFixedThreadPool(4)) {
            db.start();
            var oidc = identity.start();
            try (var socket = new ServerSocket(0)) {
                api.port = socket.getLocalPort();
            }
            api.start(db, oidc);
            Actor provider = new Actor("workload-provider", "provider");
            call("setup", "POST", "/api/v1/me/bootstrap", provider, null, null, 200);
            String providerId =
                    call(
                                    "setup",
                                    "POST",
                                    "/api/v1/providers/me",
                                    provider,
                                    Map.of(
                                            "displayName",
                                            "Workload provider",
                                            "type",
                                            "INDIVIDUAL",
                                            "description",
                                            "Isolated synthetic workload",
                                            "city",
                                            "Toronto"),
                                    null,
                                    201)
                            .body()
                            .path("id")
                            .asText();
            String offering =
                    call(
                                    "setup",
                                    "POST",
                                    "/api/v1/providers/me/" + providerId + "/offerings",
                                    provider,
                                    Map.of(
                                            "title",
                                            "Workload service",
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
                            .body()
                            .path("id")
                            .asText();
            var customers = new ArrayList<Actor>();
            var createKeys = new ArrayList<String>();
            var creates = new ArrayList<Callable<Reply>>();
            for (int index = 0; index < CUSTOMERS; index++) {
                Actor customer = new Actor("workload-customer-" + index, "customer");
                customers.add(customer);
                call("setup", "POST", "/api/v1/me/bootstrap", customer, null, null, 200);
                String key = UUID.randomUUID().toString();
                createKeys.add(key);
                for (int retry = 0; retry < 2; retry++)
                    creates.add(
                            () ->
                                    call(
                                            "create",
                                            "POST",
                                            "/api/v1/offerings/" + offering + "/bookings",
                                            customer,
                                            Map.of("offeringVersion", 0, "city", "Toronto"),
                                            key,
                                            200,
                                            201));
            }
            var created = batch(pool, creates);
            var bookings = new ArrayList<Booking>();
            var bookingIds = new HashSet<String>();
            for (int index = 0; index < CUSTOMERS; index++) {
                Reply first = created.get(index * 2), second = created.get(index * 2 + 1);
                assertEquals(Set.of(200, 201), Set.of(first.status(), second.status()));
                String id = first.body().path("id").asText();
                assertEquals(id, second.body().path("id").asText());
                assertTrue(bookingIds.add(id));
                assertEquals(offering, first.body().path("offeringId").asText());
                assertEquals("80.50", first.body().path("price").asText());
                bookings.add(new Booking(customers.get(index), id, UUID.randomUUID().toString()));
            }
            var instances = awaitAll(bookings, "WAIT_PROVIDER");
            assertEquals(CUSTOMERS, new HashSet<>(instances.values()).size());
            awaitCheckpoints(db);
            long originalProcess = api.server.pid();
            api.stop();
            assertEquals(CUSTOMERS, scalar(db, "SELECT count(*) FROM workflow_instance_entity"));
            assertEquals(CUSTOMERS * 3, scalar(db, "SELECT count(*) FROM task_info_entity"));
            assertEquals(
                    CUSTOMERS,
                    scalar(db, "SELECT count(*) FROM booking_workflow_start WHERE started"));
            assertEquals(
                    CUSTOMERS,
                    scalar(
                            db,
                            "SELECT count(*) FROM payment_transaction WHERE operation='AUTHORIZE'"));
            api.start(db, oidc);
            assertNotEquals(originalProcess, api.server.pid());
            assertEquals(instances, awaitAll(bookings, "WAIT_PROVIDER"));

            var reads = new ArrayList<Callable<Reply>>();
            for (int index = 0; index < CUSTOMERS; index++) {
                Booking booking = bookings.get(index);
                Actor other = customers.get((index + 1) % CUSTOMERS);
                reads.add(
                        () ->
                                call(
                                        "read",
                                        "GET",
                                        path(booking),
                                        booking.customer(),
                                        null,
                                        null,
                                        200));
                reads.add(() -> call("read", "GET", path(booking), provider, null, null, 200));
                reads.add(() -> call("denied_read", "GET", path(booking), other, null, null, 404));
            }
            var readResults = batch(pool, reads);
            for (int index = 0; index < CUSTOMERS; index++) {
                for (int actor = 0; actor < 2; actor++) {
                    JsonNode body = readResults.get(index * 3 + actor).body();
                    assertEquals(bookings.get(index).id(), body.path("id").asText());
                    assertEquals(
                            instances.get(bookings.get(index).id()),
                            body.path("workflowInstanceId").asText());
                    assertEquals("WAIT_PROVIDER", body.path("status").asText());
                }
            }
            var cancels = new ArrayList<Callable<Reply>>();
            for (Booking booking : bookings)
                for (int retry = 0; retry < 2; retry++) {
                    cancels.add(
                            () ->
                                    call(
                                            "cancel",
                                            "POST",
                                            path(booking) + "/cancel",
                                            booking.customer(),
                                            null,
                                            booking.cancelKey(),
                                            202));
                }
            assertReceipts(bookings, batch(pool, cancels));
            assertEquals(instances, awaitAll(bookings, "CANCELLED"));
            var replays = new ArrayList<Callable<Reply>>();
            for (Booking booking : bookings) {
                replays.add(
                        () ->
                                call(
                                        "replay",
                                        "POST",
                                        path(booking) + "/cancel",
                                        booking.customer(),
                                        null,
                                        booking.cancelKey(),
                                        202));
            }
            var replayed = batch(pool, replays);
            for (int index = 0; index < CUSTOMERS; index++) {
                assertEquals(
                        bookings.get(index).cancelKey(),
                        replayed.get(index).body().path("eventId").asText());
                assertTrue(replayed.get(index).body().path("processed").asBoolean());
                var payment =
                        call(
                                        "settlement_read",
                                        "GET",
                                        path(bookings.get(index)) + "/payment",
                                        customers.get(index),
                                        null,
                                        null,
                                        200)
                                .body();
                assertEquals("VOIDED", payment.path("status").asText());
                assertEquals("80.50", payment.path("amount").asText());
                assertEquals("CAD", payment.path("currency").asText());
                assertTrue(payment.path("simulated").asBoolean());
                assertEquals(
                        List.of("AUTHORIZE", "VOID"),
                        api.json.convertValue(payment.path("operations"), List.class));
            }
            assertEquals(CUSTOMERS, scalar(db, "SELECT count(*) FROM booking"));
            assertEquals(
                    CUSTOMERS, scalar(db, "SELECT count(*) FROM booking WHERE status='CANCELLED'"));
            assertEquals(
                    CUSTOMERS,
                    scalar(
                            db,
                            "SELECT count(*) FROM payment WHERE status='VOIDED' AND amount=80.50 AND currency='CAD' AND simulated"));
            assertEquals(CUSTOMERS, scalar(db, "SELECT count(*) FROM booking_event"));
            assertEquals(
                    CUSTOMERS,
                    scalar(
                            db,
                            "SELECT count(*) FROM booking_event WHERE event_type='CANCEL' AND processed_at IS NOT NULL"));
            assertEquals(CUSTOMERS * 2, scalar(db, "SELECT count(*) FROM payment_transaction"));
            assertEquals(
                    CUSTOMERS,
                    scalar(
                            db,
                            "SELECT count(*) FROM (SELECT booking_id FROM payment_transaction GROUP BY booking_id HAVING count(*)=2 AND count(*) FILTER (WHERE operation='AUTHORIZE')=1 AND count(*) FILTER (WHERE operation='VOID')=1) settled"));
            assertEquals(CUSTOMERS * 3, scalar(db, "SELECT count(*) FROM booking_status_history"));
            assertEquals(
                    CUSTOMERS,
                    scalar(
                            db,
                            "SELECT count(*) FROM (SELECT booking_id FROM booking_status_history GROUP BY booking_id HAVING count(*)=3 AND count(*) FILTER (WHERE status='CREATED')=1 AND count(*) FILTER (WHERE status='WAIT_PROVIDER')=1 AND count(*) FILTER (WHERE status='CANCELLED')=1) histories"));
            // Replay each original creation key after settlement: never create another booking.
            for (int index = 0; index < CUSTOMERS; index++) {
                var replay =
                        call(
                                        "create_replay",
                                        "POST",
                                        "/api/v1/offerings/" + offering + "/bookings",
                                        customers.get(index),
                                        Map.of("offeringVersion", 0, "city", "Toronto"),
                                        createKeys.get(index),
                                        200)
                                .body();
                assertEquals(bookings.get(index).id(), replay.path("id").asText());
                assertEquals("CANCELLED", replay.path("status").asText());
            }
            assertEquals(CUSTOMERS, scalar(db, "SELECT count(*) FROM booking"));
            assertEquals(CUSTOMERS * 2, scalar(db, "SELECT count(*) FROM payment_transaction"));
            assertEquals(samples.size(), requestIds.size());
            printMeasurements();
            System.out.printf(
                    "Authenticated workload proof: customers=8 threads=4 bookings=8 restored_instances=8 authorize=8 void=8 processed_cancel_events=8 cross_account_denials=8 requests=%d; no production capacity/SLO claim.%n",
                    samples.size());
        } finally {
            try {
                api.stop();
            } finally {
                identity.stop();
                api.client.close();
            }
        }
    }

    private void assertReceipts(List<Booking> bookings, List<Reply> receipts) {
        for (int index = 0; index < CUSTOMERS; index++)
            for (int retry = 0; retry < 2; retry++) {
                JsonNode receipt = receipts.get(index * 2 + retry).body();
                assertEquals(bookings.get(index).id(), receipt.path("bookingId").asText());
                assertEquals(bookings.get(index).cancelKey(), receipt.path("eventId").asText());
                assertEquals("CANCEL", receipt.path("type").asText());
            }
    }

    private Map<String, String> awaitAll(List<Booking> bookings, String status) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        var instances = new HashMap<String, String>();
        do {
            boolean all = true;
            for (Booking booking : bookings) {
                var body =
                        call("poll", "GET", path(booking), booking.customer(), null, null, 200)
                                .body();
                if (!status.equals(body.path("status").asText())) all = false;
                String instance = body.path("workflowInstanceId").asText();
                if (instance.isBlank()) all = false;
                instances.put(booking.id(), instance);
            }
            if (all) return instances;
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        fail("All owned bookings did not reach " + status + "; inspect restart-check-server.log");
        return Map.of();
    }

    private void awaitCheckpoints(PostgreSQLContainer db) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        do {
            if (scalar(
                            db,
                            "SELECT count(*) FROM (SELECT workflow_instance_id FROM task_info_entity GROUP BY workflow_instance_id HAVING count(*)=3) checkpoints")
                    == CUSTOMERS) return;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        fail("All eight waiting workflows must persist three checkpoints before crash");
    }

    private long scalar(PostgreSQLContainer db, String sql) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private <T> List<T> batch(ExecutorService pool, List<Callable<T>> calls) throws Exception {
        var gate = new CountDownLatch(1);
        var futures = new ArrayList<Future<T>>();
        for (Callable<T> call : calls)
            futures.add(
                    pool.submit(
                            () -> {
                                assertTrue(gate.await(5, TimeUnit.SECONDS));
                                return call.call();
                            }));
        gate.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        var results = new ArrayList<T>();
        try {
            for (Future<T> future : futures)
                results.add(
                        future.get(
                                Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            return results;
        } finally {
            futures.forEach(
                    future -> {
                        if (!future.isDone()) future.cancel(true);
                    });
        }
    }

    private Reply call(
            String phase,
            String method,
            String path,
            Actor actor,
            Object body,
            String key,
            int... expected)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(api.uri(path))
                        .timeout(Duration.ofSeconds(5))
                        .header(
                                "Authorization",
                                "Bearer "
                                        + TestIdentityProvider.token(actor.subject(), actor.role()))
                        .header("Content-Type", "application/json");
        if (key != null) builder.header("Idempotency-Key", key);
        var request =
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(
                                                api.json.writeValueAsString(body)))
                        .build();
        long start = System.nanoTime();
        var response = api.client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(
                Arrays.stream(expected).anyMatch(value -> value == response.statusCode()),
                () -> "Unexpected " + response.statusCode() + " in workload phase " + phase);
        String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertDoesNotThrow(() -> UUID.fromString(requestId));
        assertTrue(
                requestIds.add(requestId), "Each response must have a distinct server request ID");
        JsonNode json = api.json.readTree(response.body());
        samples.add(new Sample(phase, response.statusCode(), System.nanoTime() - start));
        return new Reply(response.statusCode(), json);
    }

    private String path(Booking booking) {
        return "/api/v1/bookings/" + booking.id();
    }

    private void printMeasurements() {
        samples.stream()
                .map(Sample::phase)
                .distinct()
                .sorted()
                .forEach(
                        phase -> {
                            var timings =
                                    samples.stream()
                                            .filter(sample -> phase.equals(sample.phase()))
                                            .map(Sample::nanos)
                                            .sorted()
                                            .toList();
                            var statuses = new TreeMap<Integer, Long>();
                            samples.stream()
                                    .filter(sample -> phase.equals(sample.phase()))
                                    .forEach(
                                            sample ->
                                                    statuses.merge(sample.status(), 1L, Long::sum));
                            int count = timings.size();
                            System.out.printf(
                                    Locale.ROOT,
                                    "Authenticated workload phase=%s requests=%d statuses=%s p50_ms=%.3f p95_ms=%.3f max_ms=%.3f%n",
                                    phase,
                                    count,
                                    statuses,
                                    timings.get((int) Math.ceil(count * .5) - 1) / 1_000_000.0,
                                    timings.get((int) Math.ceil(count * .95) - 1) / 1_000_000.0,
                                    timings.get(count - 1) / 1_000_000.0);
                        });
    }
}
