package com.bliss.dispute;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.booking.Booking;
import com.bliss.identity.TestIdentityProvider;
import com.bliss.payment.PaymentGateway;
import com.bliss.workflow.FaultInjectingPayments;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
@TestProfile(DisputeTest.MetricsProfile.class)
class DisputeTest {
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
    @Inject DisputeDispatcher dispatcher;
    @Inject DisputeService service;
    @Inject FaultInjectingPayments payments;

    record Actor(String token, String id, String subject) {}

    record Fixture(Actor customer, Actor owner, String provider, String booking) {}

    Actor actor(String... roles) {
        String subject = "dispute-" + UUID.randomUUID();
        String token = TestIdentityProvider.token(subject, roles);
        String id =
                given().auth()
                        .oauth2(token)
                        .contentType("application/json")
                        .post("/api/v1/me/bootstrap")
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("id");
        return new Actor(token, id, subject);
    }

    Fixture fixture() {
        Actor customer = actor("customer"), owner = actor("customer", "provider");
        String provider =
                given().auth()
                        .oauth2(owner.token)
                        .contentType("application/json")
                        .body(
                                Map.of(
                                        "displayName",
                                        "Review provider",
                                        "type",
                                        "COMPANY",
                                        "description",
                                        "Demo",
                                        "city",
                                        "Toronto"))
                        .post("/api/v1/providers/me")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        String offering =
                given().auth()
                        .oauth2(owner.token)
                        .contentType("application/json")
                        .body(
                                Map.of(
                                        "title",
                                        "Review service",
                                        "description",
                                        "Demo scope",
                                        "price",
                                        "10.00",
                                        "currency",
                                        "CAD",
                                        "categoryId",
                                        "10000000-0000-0000-0000-000000000004"))
                        .post("/api/v1/providers/me/" + provider + "/offerings")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        String booking =
                given().auth()
                        .oauth2(customer.token)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .body(Map.of("offeringVersion", 0, "city", "Toronto"))
                        .post("/api/v1/offerings/" + offering + "/bookings")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        given().auth()
                .oauth2(owner.token)
                .delete(
                        "/api/v1/providers/me/"
                                + provider
                                + "/offerings/"
                                + offering
                                + "?version=0")
                .then()
                .statusCode(204);
        return new Fixture(customer, owner, provider, booking);
    }

    // Isolate dispute rules from booking transition timing; packaged restart
    // coverage exercises the entire real booking lifecycle before opening a case.
    Fixture ready(boolean captured) {
        var f = fixture();
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            Booking b = em.find(Booking.class, UUID.fromString(f.booking));
                            payments.execute(b, PaymentGateway.Operation.AUTHORIZE);
                            service.bookingStatus(
                                    b, Booking.Status.WAIT_CUSTOMER, UUID.fromString(f.owner.id));
                            if (captured) {
                                payments.execute(b, PaymentGateway.Operation.CAPTURE);
                                service.bookingStatus(
                                        b,
                                        Booking.Status.COMPLETED,
                                        UUID.fromString(f.customer.id));
                            }
                        });
        return f;
    }

    Response open(Fixture f) {
        return open(f.customer, f.booking, "The agreed work needs attention.");
    }

    Response open(Actor a, String booking, String reason) {
        return given().auth()
                .oauth2(a.token)
                .contentType("application/json")
                .body(Map.of("reason", reason))
                .post("/api/v1/bookings/" + booking + "/disputes");
    }

    Response get(Actor a, String id) {
        return given().auth().oauth2(a.token).get("/api/v1/disputes/" + id);
    }

    Response evidence(Actor a, String id, UUID key, String body) {
        return given().auth()
                .oauth2(a.token)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body(Map.of("body", body))
                .post("/api/v1/disputes/" + id + "/evidence");
    }

    Response decide(Actor a, String id, UUID key, String outcome, String rationale) {
        return given().auth()
                .oauth2(a.token)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body(Map.of("outcome", outcome, "rationale", rationale))
                .post("/api/v1/disputes/" + id + "/decision");
    }

    Response await(Actor actor, String id, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        Response r;
        do {
            r = get(actor, id);
            if (expected.equals(r.path("status"))) return r;
            Thread.sleep(75);
        } while (System.nanoTime() < deadline);
        assertEquals(expected, r.path("status"));
        return r;
    }

    void expireEvidence(String id) {
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            var dispute = em.find(Dispute.class, UUID.fromString(id));
                            // Model a case opened before its deadline, then let that deadline pass.
                            dispute.createdAt = Instant.now().minusSeconds(86401);
                            dispute.evidenceDeadline = Instant.now().minusSeconds(1);
                        });
    }

    long count(String query, String id) {
        return QuarkusTransaction.requiringNew()
                .call(
                        () ->
                                ((Number)
                                                em.createNativeQuery(query)
                                                        .setParameter(1, UUID.fromString(id))
                                                        .getSingleResult())
                                        .longValue());
    }

    @Test
    void ownershipValidationFilingWindowAndBookingRaceAreEnforced() {
        var f = fixture();
        String initialBooking = f.booking;
        var outsider = actor("customer");
        var admin = actor("admin");
        for (var status : Booking.Status.values()) {
            if (status == Booking.Status.WAIT_CUSTOMER || status == Booking.Status.COMPLETED)
                continue;
            QuarkusTransaction.requiringNew()
                    .run(
                            () ->
                                    em.find(Booking.class, UUID.fromString(initialBooking)).status =
                                            status);
            open(f).then().statusCode(409);
        }
        f = ready(false);
        final var eligible = f;
        for (var a : List.of(f.owner, outsider, admin))
            open(a, f.booking, "Claim").then().statusCode(404);
        var lost =
                new Actor(
                        TestIdentityProvider.token(f.customer.subject),
                        f.customer.id,
                        f.customer.subject);
        open(lost, f.booking, "Claim").then().statusCode(404);
        for (String text : List.of(" ", "x".repeat(2001), "bad\0text"))
            open(f.customer, f.booking, text).then().statusCode(400);
        given().contentType("application/json")
                .body(Map.of("reason", "Claim"))
                .post("/api/v1/bookings/" + f.booking + "/disputes")
                .then()
                .statusCode(401);
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/bookings/" + f.booking + "/disputes")
                .then()
                .body("eligible", is(true));
        // Pending confirmation wins admission. Dispute must not overwrite its state.
        given().auth()
                .oauth2(f.customer.token)
                .header("Idempotency-Key", UUID.randomUUID())
                .post("/api/v1/bookings/" + f.booking + "/confirm-completion")
                .then()
                .statusCode(202);
        open(f).then().statusCode(409);
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            em.createNativeQuery(
                                            "UPDATE booking_event SET processed_at=now() WHERE booking_id=?1")
                                    .setParameter(1, UUID.fromString(eligible.booking))
                                    .executeUpdate();
                            em.createNativeQuery(
                                            "UPDATE booking_status_history SET occurred_at=now()-interval '8 days' WHERE booking_id=?1 AND status='WAIT_CUSTOMER'")
                                    .setParameter(1, UUID.fromString(eligible.booking))
                                    .executeUpdate();
                        });
        open(f).then().statusCode(409);
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/bookings/" + f.booking + "/disputes")
                .then()
                .body("eligible", is(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realWorkflowSettlesOnceWithAuthorizedIndependentAdminAndRetry(boolean captured)
            throws Exception {
        var f = ready(captured);
        var admin = actor("admin");
        String id = open(f).then().statusCode(201).extract().path("id");
        open(f).then().statusCode(200).body("id", is(id));
        open(f.customer, f.booking, "Changed claim").then().statusCode(409);
        given().auth()
                .oauth2(f.customer.token)
                .header("Idempotency-Key", UUID.randomUUID())
                .post("/api/v1/bookings/" + f.booking + "/confirm-completion")
                .then()
                .statusCode(409);
        String outcome = captured ? "CUSTOMER_FAVORED" : "PROVIDER_FAVORED";
        UUID key = UUID.randomUUID();
        decide(admin, id, key, outcome, "Reviewed evidence").then().statusCode(409);
        evidence(f.owner, id, UUID.randomUUID(), "Provider response with service details.")
                .then()
                .statusCode(200);
        dispatcher.startDispute(UUID.fromString(id));
        String instance = await(admin, id, "UNDER_REVIEW").path("workflowInstanceId");
        assertNotNull(instance);
        var customerAdmin =
                new Actor(
                        TestIdentityProvider.token(f.customer.subject, "customer", "admin"),
                        f.customer.id,
                        f.customer.subject);
        var providerAdmin =
                new Actor(
                        TestIdentityProvider.token(f.owner.subject, "provider", "admin"),
                        f.owner.id,
                        f.owner.subject);
        for (var a : List.of(f.customer, f.owner, customerAdmin, providerAdmin))
            decide(a, id, key, outcome, "Self decision").then().statusCode(403);
        payments.failNext(UUID.fromString(f.booking), 1);
        decide(admin, id, key, outcome, "Reviewed evidence").then().statusCode(202);
        String resolved = captured ? "RESOLVED_CUSTOMER" : "RESOLVED_PROVIDER";
        await(admin, id, resolved)
                .then()
                .body("workflowInstanceId", is(instance), "decision.processed", is(true));
        decide(admin, id, key, outcome, "Reviewed evidence")
                .then()
                .statusCode(202)
                .body("processed", is(true));
        decide(admin, id, key, outcome, "Changed rationale").then().statusCode(409);
        assertEquals(2, payments.attempts(UUID.fromString(f.booking)));
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/bookings/" + f.booking)
                .then()
                .body("status", is(captured ? "REFUNDED" : "COMPLETED"));
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/bookings/" + f.booking + "/payment")
                .then()
                .body(
                        "status",
                        is(captured ? "REFUNDED" : "CAPTURED"),
                        "operations",
                        captured
                                ? containsInAnyOrder("AUTHORIZE", "CAPTURE", "REFUND")
                                : contains("AUTHORIZE", "CAPTURE"));
        given().auth()
                .oauth2(admin.token)
                .get("/api/v1/disputes/" + id + "/history")
                .then()
                .body("items.status", contains("COLLECTING_EVIDENCE", "UNDER_REVIEW", resolved));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM audit_event WHERE resource_id=?1 AND action='DISPUTE_DECISION_ADMITTED'",
                        id));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM dispute_decision WHERE dispute_id=?1 AND processed_at IS NOT NULL",
                        id));
    }

    @Test
    void evidenceTimeoutRequiresAdminAndCustomerFavorVoidsOnlyTheHold() throws Exception {
        var f = ready(false);
        var admin = actor("admin");
        String id = open(f).path("id");
        UUID key = UUID.randomUUID();
        evidence(f.customer, id, key, "Additional detail").then().statusCode(200);
        expireEvidence(id);
        evidence(f.customer, id, key, "Additional detail").then().statusCode(200);
        evidence(f.customer, id, UUID.randomUUID(), "Late detail").then().statusCode(409);
        dispatcher.startDispute(UUID.fromString(id));
        await(admin, id, "UNDER_REVIEW");
        Thread.sleep(2300);
        get(admin, id).then().body("status", is("UNDER_REVIEW"), "decision", nullValue());
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/bookings/" + f.booking + "/payment")
                .then()
                .body("status", is("AUTHORIZED"));
        decide(
                        admin,
                        id,
                        UUID.randomUUID(),
                        "CUSTOMER_FAVORED",
                        "Provider did not respond; local demo decision.")
                .then()
                .statusCode(202);
        await(admin, id, "RESOLVED_CUSTOMER");
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/bookings/" + f.booking + "/payment")
                .then()
                .body("status", is("VOIDED"), "operations", contains("AUTHORIZE", "VOID"));
    }

    @Test
    void evidenceAndReadsArePrivateValidatedAndBounded() {
        var f = ready(false);
        var admin = actor("admin");
        var outsider = actor("customer");
        String id = open(f).path("id");
        get(outsider, id).then().statusCode(404);
        for (String suffix : List.of("/evidence", "/history", "/execution"))
            given().auth()
                    .oauth2(outsider.token)
                    .get("/api/v1/disputes/" + id + suffix)
                    .then()
                    .statusCode(404);
        given().auth().oauth2(outsider.token).get("/api/v1/disputes").then().body("items", empty());
        evidence(outsider, id, UUID.randomUUID(), "Intrusion").then().statusCode(404);
        evidence(admin, id, UUID.randomUUID(), "Not a party").then().statusCode(403);
        UUID key = UUID.randomUUID();
        String evidence = evidence(f.owner, id, key, "  Provider note  ").path("id");
        evidence(f.owner, id, key, "Provider note").then().statusCode(200).body("id", is(evidence));
        evidence(f.owner, id, key, "Changed").then().statusCode(409);
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/disputes/" + id + "/evidence?size=1")
                .then()
                .body("total", is(2), "items.role", contains("CUSTOMER"));
        given().auth()
                .oauth2(f.owner.token)
                .get("/api/v1/disputes/" + id + "/evidence?size=1&page=1")
                .then()
                .body("items.role", contains("PROVIDER"), "items.mine", contains(true));
        for (String text : List.of(" ", "x".repeat(2001), "bad\0text"))
            evidence(f.owner, id, UUID.randomUUID(), text).then().statusCode(400);
        given().auth()
                .oauth2(f.owner.token)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .body(Map.of("body", "Spoof", "actorId", f.customer.id))
                .post("/api/v1/disputes/" + id + "/evidence")
                .then()
                .statusCode(400);
        for (String path :
                List.of(
                        "/api/v1/disputes?size=101",
                        "/api/v1/disputes/" + id + "/evidence?page=-1",
                        "/api/v1/disputes/" + id + "/history?size=0"))
            given().auth().oauth2(admin.token).get(path).then().statusCode(400);
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "UPDATE provider_member SET member_role='MEMBER' WHERE provider_id=?1")
                                        .setParameter(1, UUID.fromString(f.provider))
                                        .executeUpdate());
        get(f.owner, id).then().statusCode(404);
        evidence(f.owner, id, key, "Provider note").then().statusCode(404);
    }

    @Test
    void concurrentOpenAndOpposingDecisionsHaveOneWinner() throws Exception {
        var f = ready(false);
        var admin = actor("admin");
        var secondAdmin = actor("admin");
        try (var pool = Executors.newFixedThreadPool(4)) {
            var opened = pool.invokeAll(Collections.nCopies(4, (Callable<Response>) () -> open(f)));
            Set<String> ids = new HashSet<>();
            int created = 0;
            for (var r : opened) {
                Response response = r.get();
                assertTrue(Set.of(200, 201).contains(response.statusCode()));
                if (response.statusCode() == 201) created++;
                ids.add(response.path("id"));
            }
            assertEquals(1, created);
            assertEquals(1, ids.size());
            String id = ids.iterator().next();
            expireEvidence(id);
            dispatcher.startDispute(UUID.fromString(id));
            await(admin, id, "UNDER_REVIEW");
            var decisions =
                    pool.invokeAll(
                            List.<Callable<Response>>of(
                                    () ->
                                            decide(
                                                    admin,
                                                    id,
                                                    UUID.randomUUID(),
                                                    "CUSTOMER_FAVORED",
                                                    "Decision A"),
                                    () ->
                                            decide(
                                                    secondAdmin,
                                                    id,
                                                    UUID.randomUUID(),
                                                    "PROVIDER_FAVORED",
                                                    "Decision B")));
            List<Integer> codes = new ArrayList<>();
            for (var r : decisions) codes.add(r.get().statusCode());
            Collections.sort(codes);
            assertEquals(List.of(202, 409), codes);
            String outcome = get(admin, id).path("decision.outcome");
            await(
                    admin,
                    id,
                    outcome.equals("CUSTOMER_FAVORED") ? "RESOLVED_CUSTOMER" : "RESOLVED_PROVIDER");
            assertEquals(
                    1,
                    count("SELECT count(*) FROM dispute_workflow_start WHERE dispute_id=?1", id));
            assertEquals(1, count("SELECT count(*) FROM dispute_decision WHERE dispute_id=?1", id));
        }
    }

    @Test
    void exhaustedSettlementRetryRollsBackAndExposesOnlyWorkflowStatus() throws Exception {
        double failedBefore = awaitFailureMetrics(-1);
        var f = ready(false);
        var admin = actor("admin");
        String id = open(f).path("id");
        expireEvidence(id);
        dispatcher.startDispute(UUID.fromString(id));
        await(admin, id, "UNDER_REVIEW");
        payments.failNext(UUID.fromString(f.booking), 10);
        decide(
                        admin,
                        id,
                        UUID.randomUUID(),
                        "PROVIDER_FAVORED",
                        "Review completed; simulate unavailable gateway.")
                .then()
                .statusCode(202);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        String state;
        do {
            state =
                    given().auth()
                            .oauth2(admin.token)
                            .get("/api/v1/disputes/" + id + "/execution")
                            .then()
                            .statusCode(200)
                            .extract()
                            .path("status");
            if (Set.of("FAULTED", "ERROR").contains(state)) break;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertTrue(
                Set.of("FAULTED", "ERROR").contains(state), "Persisted execution status: " + state);
        dispatcher.startDispute(UUID.fromString(id));
        Thread.sleep(2300);
        assertEquals(4, payments.attempts(UUID.fromString(f.booking)));
        get(admin, id).then().body("status", is("UNDER_REVIEW"), "decision.processed", is(false));
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/bookings/" + f.booking + "/payment")
                .then()
                .body("status", is("AUTHORIZED"), "operations", contains("AUTHORIZE"));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM dispute WHERE id=?1 AND workflow_failed_at IS NOT NULL AND resolved_at IS NULL",
                        id));
        assertEquals(
                0,
                count(
                        "SELECT count(*) FROM workflow_instance_entity WHERE instance_id=(SELECT workflow_instance_id FROM dispute WHERE id=?1)",
                        id));
        assertEquals(failedBefore + 1, awaitFailureMetrics(failedBefore + 1));
        // The engine checkpoint has gone; the durable domain marker still drives the gauge.
        System.out.println(
                "Dispute failure metrics proof: actual_retry_exhaustion=true attempts=4 checkpoint_removed=true unresolved_failed_delta=1 positive_oldest_age=true fixed_series=2 simulated_authorization_unchanged=true; no automatic recovery/alert claim.");
    }

    private double awaitFailureMetrics(double expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            String body = given().get("/q/metrics").then().statusCode(200).extract().asString();
            var values = new HashMap<String, Double>();
            for (String line : body.split("\n")) {
                if (!line.startsWith("bliss_workflow_dispute_failed_")) continue;
                String[] parts = line.trim().split("\\s+");
                assertEquals(2, parts.length);
                assertTrue(
                        Set.of(
                                        "bliss_workflow_dispute_failed_cases",
                                        "bliss_workflow_dispute_failed_oldest_seconds")
                                .contains(parts[0]));
                values.put(parts[0], Double.parseDouble(parts[1]));
            }
            assertEquals(2, values.size());
            double cases = values.get("bliss_workflow_dispute_failed_cases");
            double oldest = values.get("bliss_workflow_dispute_failed_oldest_seconds");
            if (expected < 0 && Double.isFinite(cases)) return cases;
            if (cases == expected && oldest > 0) return cases;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        fail("Durable dispute failure metrics did not reach expected state");
        return Double.NaN;
    }
}
