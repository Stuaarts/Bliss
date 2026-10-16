package com.bliss.workflow;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.booking.Booking;
import com.bliss.identity.TestIdentityProvider;
import com.bliss.payment.PaymentGateway;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
@TestProfile(BookingWorkflowTest.MetricsProfile.class)
class BookingWorkflowTest {
    public static class MetricsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "bliss.operations.backlog.enabled",
                    "true",
                    "bliss.operations.backlog.every",
                    "1s");
        }
    }

    @Inject EntityManager em;
    @Inject BookingWorkflowDispatcher dispatcher;
    @Inject BookingWorkflowSteps steps;
    @Inject FaultInjectingPayments payments;

    record Demo(String customer, String provider, String booking) {}

    String token(String role) {
        String token = TestIdentityProvider.token("flow-" + UUID.randomUUID(), role);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .post("/api/v1/me/bootstrap")
                .then()
                .statusCode(200);
        return token;
    }

    Demo create() {
        String customer = token("customer"), provider = token("provider");
        String providerId =
                given().auth()
                        .oauth2(provider)
                        .contentType("application/json")
                        .body(
                                Map.of(
                                        "displayName",
                                        "Workflow provider",
                                        "type",
                                        "INDIVIDUAL",
                                        "description",
                                        "Demo",
                                        "city",
                                        "Toronto"))
                        .post("/api/v1/providers/me")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        String request =
                given().auth()
                        .oauth2(customer)
                        .contentType("application/json")
                        .body(
                                Map.of(
                                        "title",
                                        "Workflow job",
                                        "description",
                                        "Local demo job",
                                        "city",
                                        "Toronto",
                                        "categoryId",
                                        "10000000-0000-0000-0000-000000000004",
                                        "currency",
                                        "CAD"))
                        .post("/api/v1/service-requests")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        String quote =
                given().auth()
                        .oauth2(provider)
                        .contentType("application/json")
                        .body(
                                Map.of(
                                        "providerId",
                                        providerId,
                                        "description",
                                        "Demo scope",
                                        "price",
                                        "80.50",
                                        "currency",
                                        "CAD",
                                        "expiresAt",
                                        Instant.now().plusSeconds(3600).toString()))
                        .post("/api/v1/service-requests/" + request + "/quotes")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        String booking =
                given().auth()
                        .oauth2(customer)
                        .post("/api/v1/quotes/" + quote + "/accept")
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("id");
        return new Demo(customer, provider, booking);
    }

    Response action(Demo d, String token, String action, UUID key) {
        return given().auth()
                .oauth2(token)
                .header("Idempotency-Key", key)
                .post("/api/v1/bookings/" + d.booking + "/" + action);
    }

    String status(Demo d) {
        return given().auth()
                .oauth2(d.customer)
                .get("/api/v1/bookings/" + d.booking)
                .path("status");
    }

    void await(Demo d, String status) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (status.equals(status(d))) return;
            Thread.sleep(100);
        }
        assertEquals(status, status(d));
    }

    long count(String sql, String id) {
        return QuarkusTransaction.requiringNew()
                .call(
                        () ->
                                ((Number)
                                                em.createNativeQuery(sql)
                                                        .setParameter(1, UUID.fromString(id))
                                                        .getSingleResult())
                                        .longValue());
    }

    @Test
    void engineWaitsProcessesActionsAndCapturesOnlyOnce() throws Exception {
        Demo d = create();
        dispatcher.startBooking(UUID.fromString(d.booking));
        await(d, "WAIT_PROVIDER");
        String instance =
                given().auth()
                        .oauth2(d.customer)
                        .get("/api/v1/bookings/" + d.booking)
                        .path("workflowInstanceId");
        assertNotNull(instance);
        given().auth()
                .oauth2(d.customer)
                .get("/api/v1/bookings/" + d.booking + "/payment")
                .then()
                .statusCode(200)
                .body("status", is("AUTHORIZED"), "simulated", is(true), "amount", is("80.50"));
        given().auth()
                .oauth2(d.provider)
                .get("/api/v1/bookings/" + d.booking + "/execution")
                .then()
                .statusCode(200)
                .body("instanceId", is(instance));
        dispatcher.startBooking(UUID.fromString(d.booking));
        given().auth()
                .oauth2(d.customer)
                .get("/api/v1/bookings/" + d.booking)
                .then()
                .body("workflowInstanceId", is(instance));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation='AUTHORIZE'",
                        d.booking));
        // Allow multiple engine-managed waits before moving on: checkpoints must support loops.
        Thread.sleep(4500);
        // Leave headroom for HTTP/CI latency and second-precision rounding.
        String schedule = Instant.now().plusSeconds(5).truncatedTo(ChronoUnit.SECONDS).toString();
        UUID accepted = UUID.randomUUID();
        for (int i = 0; i < 2; i++)
            given().auth()
                    .oauth2(d.provider)
                    .header("Idempotency-Key", accepted)
                    .contentType("application/json")
                    .body(Map.of("scheduledAt", schedule))
                    .post("/api/v1/bookings/" + d.booking + "/provider-accept")
                    .then()
                    .statusCode(202);
        await(d, "SCHEDULED");
        long untilStart =
                java.time.Duration.between(Instant.now(), Instant.parse(schedule)).toMillis();
        if (untilStart >= 0) Thread.sleep(untilStart + 10);
        action(d, d.provider, "start", UUID.randomUUID()).then().statusCode(202);
        await(d, "IN_PROGRESS");
        action(d, d.customer, "cancel", UUID.randomUUID()).then().statusCode(409);
        action(d, d.provider, "mark-complete", UUID.randomUUID()).then().statusCode(202);
        await(d, "WAIT_CUSTOMER");
        assertEquals(
                0,
                count(
                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation='CAPTURE'",
                        d.booking));
        UUID confirm = UUID.randomUUID();
        action(d, d.customer, "confirm-completion", confirm).then().statusCode(202);
        await(d, "COMPLETED");
        given().auth()
                .oauth2(d.customer)
                .get("/api/v1/bookings/" + d.booking + "/history?size=3")
                .then()
                .statusCode(200)
                .body("total", is(6), "items.size()", is(3));
        given().auth()
                .oauth2(d.provider)
                .get("/api/v1/bookings/" + d.booking + "/payment")
                .then()
                .statusCode(200)
                .body("status", is("CAPTURED"), "operations", contains("AUTHORIZE", "CAPTURE"));
        action(d, d.customer, "confirm-completion", confirm)
                .then()
                .statusCode(202)
                .body("processed", is(true));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation='CAPTURE'",
                        d.booking));
        assertEquals(
                6,
                count(
                        "SELECT count(*) FROM booking_status_history WHERE booking_id=?1",
                        d.booking));
        assertEquals(
                4,
                count(
                        "SELECT count(*) FROM booking_event WHERE booking_id=?1 AND processed_at IS NOT NULL",
                        d.booking));
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            Booking b =
                                    em.find(
                                            Booking.class,
                                            UUID.fromString(d.booking),
                                            LockModeType.PESSIMISTIC_WRITE);
                            payments.execute(b, PaymentGateway.Operation.REFUND);
                            payments.execute(b, PaymentGateway.Operation.REFUND);
                        });
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation='REFUND'",
                        d.booking));
    }

    @Test
    void rejectionAndTimeoutVoidAuthorizationOnce() throws Exception {
        Demo rejected = create();
        dispatcher.startBooking(UUID.fromString(rejected.booking));
        await(rejected, "WAIT_PROVIDER");
        UUID event = UUID.randomUUID();
        action(rejected, rejected.provider, "provider-reject", event).then().statusCode(202);
        await(rejected, "CANCELLED");
        action(rejected, rejected.provider, "provider-reject", event).then().statusCode(202);
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation='VOID'",
                        rejected.booking));
        Demo expired = create();
        dispatcher.startBooking(UUID.fromString(expired.booking));
        await(expired, "WAIT_PROVIDER");
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "UPDATE booking SET decision_deadline=?2 WHERE id=?1")
                                        .setParameter(1, UUID.fromString(expired.booking))
                                        .setParameter(2, Instant.now().minusSeconds(1))
                                        .executeUpdate());
        await(expired, "EXPIRED");
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation='VOID'",
                        expired.booking));
    }

    @Test
    void cancellationBeforeDispatchDoesNotAuthorizeAndOwnershipIsEnforced() throws Exception {
        Demo d = create();
        String outsider = token("provider");
        for (String endpoint : List.of("history", "payment", "execution")) {
            given().auth()
                    .oauth2(outsider)
                    .get("/api/v1/bookings/" + d.booking + "/" + endpoint)
                    .then()
                    .statusCode(404);
            given().get("/api/v1/bookings/" + d.booking + "/" + endpoint).then().statusCode(401);
        }
        action(d, outsider, "provider-reject", UUID.randomUUID()).then().statusCode(404);
        action(d, d.customer, "provider-reject", UUID.randomUUID()).then().statusCode(403);
        action(d, d.provider, "confirm-completion", UUID.randomUUID()).then().statusCode(403);
        action(d, token("customer"), "cancel", UUID.randomUUID()).then().statusCode(404);
        given().auth()
                .oauth2(d.customer)
                .post("/api/v1/bookings/" + d.booking + "/cancel")
                .then()
                .statusCode(400);
        UUID event = UUID.randomUUID();
        action(d, d.customer, "cancel", event).then().statusCode(202);
        action(d, d.customer, "confirm-completion", event).then().statusCode(409);
        dispatcher.startBooking(UUID.fromString(d.booking));
        await(d, "CANCELLED");
        assertEquals(
                0,
                count("SELECT count(*) FROM payment_transaction WHERE booking_id=?1", d.booking));
    }

    @Test
    void transientFailuresRetryAndCommitOneAuthorization() throws Exception {
        Demo d = create();
        UUID id = UUID.fromString(d.booking);
        payments.failNext(id, 2);
        dispatcher.startBooking(id);
        await(d, "WAIT_PROVIDER");
        assertEquals(3, payments.attempts(id));
        assertEquals(
                1,
                count("SELECT count(*) FROM payment_transaction WHERE booking_id=?1", d.booking));
        assertEquals(
                2,
                count(
                        "SELECT count(*) FROM booking_status_history WHERE booking_id=?1",
                        d.booking));
        action(d, d.customer, "cancel", UUID.randomUUID()).then().statusCode(202);
        await(d, "CANCELLED");
    }

    @Test
    void abandonedStartClaimRecoversWithoutLosingBooking() throws Exception {
        Demo d = create();
        UUID id = UUID.fromString(d.booking);
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            em.createNativeQuery(
                                            "UPDATE booking SET workflow_instance_id='abandoned-before-engine-start' WHERE id=?1")
                                    .setParameter(1, id)
                                    .executeUpdate();
                            em.createNativeQuery(
                                            "UPDATE booking_workflow_start SET claimed_at=?2 WHERE booking_id=?1")
                                    .setParameter(1, id)
                                    .setParameter(2, Instant.now().minusSeconds(60))
                                    .executeUpdate();
                        });
        dispatcher.startBooking(id);
        await(d, "WAIT_PROVIDER");
        given().auth()
                .oauth2(d.customer)
                .get("/api/v1/bookings/" + d.booking)
                .then()
                .body("workflowInstanceId", not("abandoned-before-engine-start"));
        action(d, d.customer, "cancel", UUID.randomUUID()).then().statusCode(202);
        await(d, "CANCELLED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exhaustedRetryIsDurableAndNeverReclaimed(boolean authorization) throws Exception {
        double before = awaitFailureMetrics(-1);
        Demo d = create();
        UUID id = UUID.fromString(d.booking);
        UUID cancel = UUID.randomUUID();
        if (!authorization) {
            dispatcher.startBooking(id);
            await(d, "WAIT_PROVIDER");
        }
        payments.failNext(
                id, 10); // Fail after simulated ledger writes, more than all four attempts.
        if (authorization) dispatcher.startBooking(id);
        else action(d, d.customer, "cancel", cancel).then().statusCode(202);
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (count(
                                    "SELECT count(*) FROM booking WHERE id=?1 AND workflow_failed_at IS NOT NULL",
                                    d.booking)
                            == 1
                    && count(
                                    "SELECT count(*) FROM workflow_instance_entity WHERE instance_id=(SELECT workflow_instance_id FROM booking WHERE id=?1)",
                                    d.booking)
                            == 0) break;
            Thread.sleep(100);
        }
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM booking WHERE id=?1 AND workflow_failed_at IS NOT NULL",
                        d.booking));
        assertEquals(
                0,
                count(
                        "SELECT count(*) FROM workflow_instance_entity WHERE instance_id=(SELECT workflow_instance_id FROM booking WHERE id=?1)",
                        d.booking));
        assertEquals(4, payments.attempts(id));
        assertEquals(
                authorization ? 1 : 0,
                count(
                        "SELECT count(*) FROM booking_workflow_start WHERE booking_id=?1 AND started=false",
                        d.booking));
        String instance =
                given().auth()
                        .oauth2(d.customer)
                        .get("/api/v1/bookings/" + d.booking)
                        .path("workflowInstanceId");
        String marker =
                QuarkusTransaction.requiringNew()
                        .call(
                                () ->
                                        em.createNativeQuery(
                                                        "SELECT workflow_failed_at::text FROM booking WHERE id=?1")
                                                .setParameter(1, id)
                                                .getSingleResult()
                                                .toString());
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "UPDATE booking_workflow_start SET claimed_at=?2 WHERE booking_id=?1")
                                        .setParameter(1, id)
                                        .setParameter(2, Instant.now().minusSeconds(60))
                                        .executeUpdate());
        for (int i = 0; i < 2; i++) dispatcher.startBooking(id);
        assertTrue(steps.advance(instance, new BookingWorkflowSteps.State(id, false)).done());
        Thread.sleep(2300); // Beyond another engine polling interval.
        assertEquals(4, payments.attempts(id));
        given().auth()
                .oauth2(d.customer)
                .get("/api/v1/bookings/" + d.booking)
                .then()
                .statusCode(200)
                .body(
                        "workflowInstanceId",
                        is(instance),
                        "status",
                        is(authorization ? "CREATED" : "WAIT_PROVIDER"));
        for (String owner : List.of(d.customer, d.provider))
            given().auth()
                    .oauth2(owner)
                    .get("/api/v1/bookings/" + d.booking + "/execution")
                    .then()
                    .statusCode(200)
                    .body(
                            "instanceId",
                            is(instance),
                            "status",
                            is("ERROR"),
                            "checkpointPresent",
                            is(false));
        given().auth()
                .oauth2(token("customer"))
                .get("/api/v1/bookings/" + d.booking + "/execution")
                .then()
                .statusCode(404);
        action(d, d.customer, "cancel", UUID.randomUUID())
                .then()
                .statusCode(409)
                .body("detail", is("This record changed. Refresh and try again."));
        if (!authorization) {
            action(d, d.customer, "cancel", cancel)
                    .then()
                    .statusCode(202)
                    .body("processed", is(false));
            action(d, d.customer, "confirm-completion", cancel).then().statusCode(409);
        }
        assertEquals(
                authorization ? 0 : 1,
                count("SELECT count(*) FROM payment_transaction WHERE booking_id=?1", d.booking));
        assertEquals(
                0,
                count(
                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation='VOID'",
                        d.booking));
        assertEquals(
                authorization ? 1 : 2,
                count(
                        "SELECT count(*) FROM booking_status_history WHERE booking_id=?1",
                        d.booking));
        assertEquals(
                authorization ? 0 : 1,
                count(
                        "SELECT count(*) FROM booking_event WHERE booking_id=?1 AND processed_at IS NULL",
                        d.booking));
        assertEquals(
                marker,
                QuarkusTransaction.requiringNew()
                        .call(
                                () ->
                                        em.createNativeQuery(
                                                        "SELECT workflow_failed_at::text FROM booking WHERE id=?1")
                                                .setParameter(1, id)
                                                .getSingleResult()
                                                .toString()));
        given().auth()
                .oauth2(d.customer)
                .get("/api/v1/bookings/" + d.booking + "/payment")
                .then()
                .statusCode(200)
                .body(
                        "status",
                        is(authorization ? "NOT_AUTHORIZED" : "AUTHORIZED"),
                        "operations",
                        authorization ? empty() : contains("AUTHORIZE"));
        assertEquals(before + 1, awaitFailureMetrics(before + 1));
        System.out.println(
                "Booking failure proof: authorization="
                        + authorization
                        + " attempts=4 checkpoint_removed=true marker_retained=true expired_claim_not_restarted=true new_action_rejected=true rollback=true metric_delta=1 positive_age=true; simulated gateway only.");
    }

    private double awaitFailureMetrics(double expected) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        do {
            String body = given().get("/q/metrics").then().statusCode(200).extract().asString();
            var values = new HashMap<String, Double>();
            for (String line : body.split("\n")) {
                if (!line.startsWith("bliss_workflow_booking_failed_")) continue;
                String[] parts = line.trim().split("\\s+");
                assertEquals(2, parts.length);
                assertTrue(
                        Set.of(
                                        "bliss_workflow_booking_failed_bookings",
                                        "bliss_workflow_booking_failed_oldest_seconds")
                                .contains(parts[0]));
                values.put(parts[0], Double.parseDouble(parts[1]));
            }
            assertEquals(2, values.size());
            double bookings = values.get("bliss_workflow_booking_failed_bookings");
            if (expected < 0 && Double.isFinite(bookings)) return bookings;
            if (bookings == expected
                    && values.get("bliss_workflow_booking_failed_oldest_seconds") > 0)
                return bookings;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        fail("Durable booking failure metrics did not reach expected state");
        return Double.NaN;
    }
}
