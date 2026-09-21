package com.bliss.booking;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.bliss.workflow.BookingWorkflowDispatcher;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class DirectBookingTest {
    @Inject EntityManager em;
    @Inject BookingWorkflowDispatcher dispatcher;
    final List<UUID> createdOfferings = new ArrayList<>();

    @AfterEach
    void hideFixturesFromOtherCatalogTests() {
        if (!createdOfferings.isEmpty())
            QuarkusTransaction.requiringNew()
                    .run(
                            () ->
                                    em.createQuery(
                                                    "update ServiceOffering set active=false where id in :ids")
                                            .setParameter("ids", createdOfferings)
                                            .executeUpdate());
    }

    record Listing(String owner, String provider, String offering) {}

    String token(String... roles) {
        String token = TestIdentityProvider.token("direct-" + UUID.randomUUID(), roles);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .post("/api/v1/me/bootstrap")
                .then()
                .statusCode(200);
        return token;
    }

    Map<String, Object> scope(String title, String price) {
        return Map.of(
                "title",
                title,
                "description",
                "Six windows, supplies included",
                "price",
                price,
                "currency",
                "CAD",
                "categoryId",
                "10000000-0000-0000-0000-000000000004");
    }

    Listing listing() {
        String owner = token("provider", "customer");
        String provider =
                given().auth()
                        .oauth2(owner)
                        .contentType("application/json")
                        .body(
                                Map.of(
                                        "displayName",
                                        "Direct provider",
                                        "description",
                                        "Demo",
                                        "city",
                                        "Toronto",
                                        "type",
                                        "COMPANY"))
                        .post("/api/v1/providers/me")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        String offering =
                given().auth()
                        .oauth2(owner)
                        .contentType("application/json")
                        .body(scope("Window cleaning", "45.50"))
                        .post("/api/v1/providers/me/" + provider + "/offerings")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        createdOfferings.add(UUID.fromString(offering));
        return new Listing(owner, provider, offering);
    }

    Response book(String customer, String offering, UUID key, long version, String city) {
        return given().auth()
                .oauth2(customer)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body(Map.of("offeringVersion", version, "city", city))
                .post("/api/v1/offerings/" + offering + "/bookings");
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
    void snapshotSurvivesEditsArchiveAndExactRetry() {
        Listing l = listing();
        String customer = token("customer");
        UUID key = UUID.randomUUID();
        given().get("/api/v1/offerings/" + l.offering)
                .then()
                .statusCode(200)
                .body("version", is(0));
        String booking =
                book(customer, l.offering, key, 0, " Toronto ")
                        .then()
                        .statusCode(201)
                        .header("Location", containsString("/api/v1/bookings/"))
                        .body(
                                "offeringId",
                                is(l.offering),
                                "offeringVersion",
                                is(0),
                                "price",
                                is("45.50"),
                                "currency",
                                is("CAD"),
                                "city",
                                is("Toronto"),
                                "requestId",
                                nullValue(),
                                "quoteId",
                                nullValue(),
                                "description",
                                is("Six windows, supplies included"),
                                "status",
                                is("CREATED"))
                        .extract()
                        .path("id");
        given().auth()
                .oauth2(l.owner)
                .contentType("application/json")
                .body(Map.of("version", 0, "offering", scope("Changed scope", "95.00")))
                .put("/api/v1/providers/me/" + l.provider + "/offerings/" + l.offering)
                .then()
                .statusCode(200);
        book(customer, l.offering, UUID.randomUUID(), 0, "Toronto").then().statusCode(409);
        given().auth()
                .oauth2(l.owner)
                .delete(
                        "/api/v1/providers/me/"
                                + l.provider
                                + "/offerings/"
                                + l.offering
                                + "?version=1")
                .then()
                .statusCode(204);
        book(customer, l.offering, key, 0, "Toronto")
                .then()
                .statusCode(200)
                .body("id", is(booking), "price", is("45.50"), "title", is("Window cleaning"));
        book(customer, l.offering, UUID.randomUUID(), 2, "Toronto").then().statusCode(404);
        for (String actor : List.of(customer, l.owner))
            given().auth()
                    .oauth2(actor)
                    .get("/api/v1/bookings/" + booking)
                    .then()
                    .statusCode(200)
                    .body("price", is("45.50"));
        String stranger = token("customer");
        for (String suffix : List.of("", "/history", "/payment", "/execution"))
            given().auth()
                    .oauth2(stranger)
                    .get("/api/v1/bookings/" + booking + suffix)
                    .then()
                    .statusCode(404);
        assertEquals(
                1,
                count("SELECT count(*) FROM booking_workflow_start WHERE booking_id=?1", booking));
        assertEquals(
                1,
                count("SELECT count(*) FROM booking_status_history WHERE booking_id=?1", booking));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM audit_event WHERE resource_id=?1 AND action='BOOKING_CREATED'",
                        booking));
    }

    @Test
    void concurrentRetriesCreateOneBookingAndConflictingPayloadsAreRejected() throws Exception {
        Listing l = listing();
        String customer = token("customer");
        UUID key = UUID.randomUUID();
        var results =
                race(
                        () -> book(customer, l.offering, key, 0, "Toronto"),
                        () -> book(customer, l.offering, key, 0, "Toronto"));
        assertEquals(
                List.of(200, 201), results.stream().map(Response::statusCode).sorted().toList());
        String booking = results.getFirst().path("id");
        assertEquals(booking, results.getLast().path("id"));
        assertEquals(1, count("SELECT count(*) FROM booking WHERE offering_id=?1", l.offering));
        book(customer, l.offering, key, 0, "Ottawa").then().statusCode(409);
        book(customer, l.offering, key, 1, "Toronto").then().statusCode(409);
        book(customer, listing().offering, key, 0, "Toronto").then().statusCode(409);
        // Keys are scoped to the authenticated customer. Another customer creates their own
        // booking.
        book(token("customer"), l.offering, key, 0, "Toronto")
                .then()
                .statusCode(201)
                .body("id", not(booking));
    }

    @Test
    void simultaneousKeyReuseAcrossDifferentOfferingsHasOneWinner() throws Exception {
        Listing a = listing(), b = listing();
        String customer = token("customer");
        UUID key = UUID.randomUUID();
        var results =
                race(
                        () -> book(customer, a.offering, key, 0, "Toronto"),
                        () -> book(customer, b.offering, key, 0, "Toronto"));
        assertEquals(
                List.of(201, 409), results.stream().map(Response::statusCode).sorted().toList());
        assertEquals(1, count("SELECT count(*) FROM booking WHERE direct_key=?1", key.toString()));
    }

    @Test
    void bookingAndArchiveRaceNeverCreatesAnUnversionedOrPartialBooking() throws Exception {
        Listing l = listing();
        String customer = token("customer");
        var results =
                race(
                        () -> book(customer, l.offering, UUID.randomUUID(), 0, "Toronto"),
                        () ->
                                given().auth()
                                        .oauth2(l.owner)
                                        .delete(
                                                "/api/v1/providers/me/"
                                                        + l.provider
                                                        + "/offerings/"
                                                        + l.offering
                                                        + "?version=0"));
        assertEquals(204, results.get(1).statusCode());
        assertTrue(List.of(201, 404).contains(results.getFirst().statusCode()));
        long expected = results.getFirst().statusCode() == 201 ? 1 : 0;
        assertEquals(
                expected, count("SELECT count(*) FROM booking WHERE offering_id=?1", l.offering));
        assertEquals(
                expected,
                count(
                        "SELECT count(*) FROM booking_workflow_start s JOIN booking b ON b.id=s.booking_id WHERE b.offering_id=?1",
                        l.offering));
    }

    @Test
    void validatesIdentityInputAndPreventsProviderSelfBooking() {
        Listing l = listing();
        String customer = token("customer");
        String path = "/api/v1/offerings/" + l.offering + "/bookings";
        given().contentType("application/json")
                .body(Map.of("offeringVersion", 0, "city", "Toronto"))
                .post(path)
                .then()
                .statusCode(401);
        book(token("provider"), l.offering, UUID.randomUUID(), 0, "Toronto").then().statusCode(403);
        book(l.owner, l.offering, UUID.randomUUID(), 0, "Toronto").then().statusCode(409);
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(Map.of("offeringVersion", 0, "city", "Toronto"))
                .post(path)
                .then()
                .statusCode(400);
        for (var body :
                List.of(
                        Map.of("offeringVersion", -1, "city", "Toronto"),
                        Map.of("city", "Toronto"),
                        Map.of("offeringVersion", 0, "city", " "),
                        Map.of("offeringVersion", 0, "city", "x".repeat(121)),
                        Map.of("offeringVersion", 0, "city", "Toronto", "price", "0.01"),
                        Map.of(
                                "offeringVersion",
                                0,
                                "city",
                                "Toronto",
                                "customerId",
                                UUID.randomUUID())))
            given().auth()
                    .oauth2(customer)
                    .header("Idempotency-Key", UUID.randomUUID())
                    .contentType("application/json")
                    .body(body)
                    .post(path)
                    .then()
                    .statusCode(400);
        book(customer, UUID.randomUUID().toString(), UUID.randomUUID(), 0, "Toronto")
                .then()
                .statusCode(404);
        String userId = given().auth().oauth2(customer).get("/api/v1/me").path("id");
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "INSERT INTO provider_member(provider_id,user_id,member_role) VALUES (?1,?2,'MEMBER')")
                                        .setParameter(1, UUID.fromString(l.provider))
                                        .setParameter(2, UUID.fromString(userId))
                                        .executeUpdate());
        book(customer, l.offering, UUID.randomUUID(), 0, "Toronto").then().statusCode(409);
        assertEquals(0, count("SELECT count(*) FROM booking WHERE offering_id=?1", l.offering));
    }

    @Test
    void directBookingDispatchesTheSameWorkflowAndCompensatesOnce() throws Exception {
        Listing l = listing();
        String customer = token("customer");
        UUID key = UUID.randomUUID();
        String booking =
                book(customer, l.offering, key, 0, "Toronto")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");
        dispatcher.startBooking(UUID.fromString(booking));
        awaitStatus(customer, booking, "WAIT_PROVIDER");
        given().auth()
                .oauth2(customer)
                .get("/api/v1/bookings/" + booking + "/payment")
                .then()
                .statusCode(200)
                .body("status", is("AUTHORIZED"), "amount", is("45.50"), "simulated", is(true));
        given().auth()
                .oauth2(l.owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .post("/api/v1/bookings/" + booking + "/provider-reject")
                .then()
                .statusCode(202);
        awaitStatus(customer, booking, "CANCELLED");
        book(customer, l.offering, key, 0, "Toronto")
                .then()
                .statusCode(200)
                .body("id", is(booking), "status", is("CANCELLED"));
        given().auth()
                .oauth2(customer)
                .get("/api/v1/bookings/" + booking + "/payment")
                .then()
                .statusCode(200)
                .body("operations", contains("AUTHORIZE", "VOID"), "status", is("VOIDED"));
    }

    void awaitStatus(String customer, String booking, String expected) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            String status =
                    given().auth()
                            .oauth2(customer)
                            .get("/api/v1/bookings/" + booking)
                            .path("status");
            if (expected.equals(status)) return;
            Thread.sleep(100);
        }
        fail("Booking did not reach " + expected);
    }

    List<Response> race(Callable<Response> first, Callable<Response> second) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var barrier = new CyclicBarrier(2);
            var a =
                    executor.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return first.call();
                            });
            var b =
                    executor.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return second.call();
                            });
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
}
