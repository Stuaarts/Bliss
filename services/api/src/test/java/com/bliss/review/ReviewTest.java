package com.bliss.review;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.booking.Booking;
import com.bliss.identity.TestIdentityProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class ReviewTest {
    @Inject EntityManager em;

    record Actor(String token, String id, String subject) {}

    record Fixture(Actor customer, Actor owner, String provider, String booking) {}

    Actor actor(String... roles) {
        String subject = "review-" + UUID.randomUUID();
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

    // This suite isolates review policy. Workflow/restart tests separately prove
    // that only the real lifecycle reaches COMPLETED.
    void status(Fixture f, Booking.Status status) {
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            Booking b = em.find(Booking.class, UUID.fromString(f.booking));
                            b.status = status;
                        });
    }

    Response submit(Actor actor, String booking, Object body) {
        return given().auth()
                .oauth2(actor.token)
                .contentType("application/json")
                .body(body)
                .post("/api/v1/bookings/" + booking + "/reviews");
    }

    Response state(Actor actor, String booking) {
        return given().auth().oauth2(actor.token).get("/api/v1/bookings/" + booking + "/reviews");
    }

    Response list(String provider, String query) {
        return given().get("/api/v1/providers/" + provider + "/reviews" + query);
    }

    @Test
    void onlyCompletedCustomerCanReviewAndPublicDataOmitsIdentity() {
        var f = fixture();
        list(f.provider, "")
                .then()
                .statusCode(200)
                .body("items", empty(), "total", is(0), "averageRating", nullValue());
        for (var s : Booking.Status.values()) {
            if (s == Booking.Status.COMPLETED) continue;
            status(f, s);
            state(f.customer, f.booking)
                    .then()
                    .statusCode(200)
                    .body("eligible", is(false), "review", nullValue());
            submit(f.customer, f.booking, Map.of("rating", 5)).then().statusCode(409);
        }
        status(f, Booking.Status.COMPLETED);
        state(f.customer, f.booking).then().body("eligible", is(true));
        state(f.owner, f.booking).then().body("eligible", is(false));
        var result =
                submit(
                                f.customer,
                                f.booking,
                                Map.of("rating", 5, "comment", "  Great <b>work</b> 🌿  "))
                        .then()
                        .statusCode(201)
                        .body("comment", is("Great <b>work</b> 🌿"))
                        .extract();
        String id = result.path("id");
        submit(f.customer, f.booking, Map.of("rating", 5, "comment", "Great <b>work</b> 🌿"))
                .then()
                .statusCode(200)
                .body("id", is(id));
        submit(f.customer, f.booking, Map.of("rating", 4, "comment", "Changed"))
                .then()
                .statusCode(409);
        state(f.customer, f.booking).then().body("eligible", is(false), "review.id", is(id));
        state(f.owner, f.booking).then().body("review.id", is(id));
        var pub =
                list(f.provider, "")
                        .then()
                        .statusCode(200)
                        .body("total", is(1), "averageRating", is("5.00"), "items.id", contains(id))
                        .extract();
        Map<String, Object> row = pub.path("items[0]");
        assertEquals(Set.of("id", "rating", "comment", "createdAt"), row.keySet());
        assertFalse(pub.asString().contains(f.customer.id));
        assertFalse(pub.asString().contains(f.booking));
    }

    @Test
    void outsiderProviderAdminAndLostCapabilityCannotWriteOrInspectEligibility() {
        var f = fixture();
        status(f, Booking.Status.COMPLETED);
        for (var a : List.of(actor("customer"), actor("admin"), f.owner))
            submit(a, f.booking, Map.of("rating", 5)).then().statusCode(404);
        var outsider = actor("customer");
        state(outsider, f.booking).then().statusCode(404);
        var lost =
                new Actor(
                        TestIdentityProvider.token(f.customer.subject),
                        f.customer.id,
                        f.customer.subject);
        state(lost, f.booking).then().statusCode(404);
        submit(lost, f.booking, Map.of("rating", 5)).then().statusCode(404);
        given().contentType("application/json")
                .body(Map.of("rating", 5))
                .post("/api/v1/bookings/" + f.booking + "/reviews")
                .then()
                .statusCode(401);
        list(f.provider, "").then().body("total", is(0));
    }

    @Test
    void validatesRatingCommentIdentityAndPagination() {
        var f = fixture();
        status(f, Booking.Status.COMPLETED);
        for (Object body :
                List.of(
                        Map.of(),
                        Map.of("rating", 0),
                        Map.of("rating", 6),
                        Map.of("rating", 4.5),
                        Map.of("rating", 5, "comment", "x".repeat(2001)),
                        Map.of("rating", 5, "comment", "bad\0text"),
                        Map.of("rating", 5, "customerId", f.customer.id)))
            submit(f.customer, f.booking, body).then().statusCode(400);
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=100001"))
            list(f.provider, query).then().statusCode(400);
        list(UUID.randomUUID().toString(), "").then().statusCode(404);
        submit(f.customer, UUID.randomUUID().toString(), Map.of("rating", 5))
                .then()
                .statusCode(404);
        submit(f.customer, f.booking, Map.of("rating", 1))
                .then()
                .statusCode(201)
                .body("comment", is(""));
        submit(f.customer, f.booking, Map.of("rating", 1, "comment", " ")).then().statusCode(200);
    }

    @Test
    void concurrentSubmissionsCreateOneRowAndAuditAndDatabaseRejectsDuplicates() throws Exception {
        var f = fixture();
        status(f, Booking.Status.COMPLETED);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var results =
                    pool.invokeAll(
                            Collections.nCopies(
                                    4,
                                    (Callable<Response>)
                                            () ->
                                                    submit(
                                                            f.customer,
                                                            f.booking,
                                                            Map.of("rating", 4))));
            Set<String> ids = new HashSet<>();
            int created = 0;
            for (var future : results) {
                var response = future.get();
                assertTrue(Set.of(200, 201).contains(response.statusCode()));
                if (response.statusCode() == 201) created++;
                ids.add(response.path("id"));
            }
            assertEquals(1, created);
            assertEquals(1, ids.size());
            long audits =
                    QuarkusTransaction.requiringNew()
                            .call(
                                    () ->
                                            ((Number)
                                                            em.createNativeQuery(
                                                                            "SELECT count(*) FROM audit_event WHERE resource_id=?1 AND action='REVIEW_CREATED'")
                                                                    .setParameter(
                                                                            1,
                                                                            UUID.fromString(
                                                                                    ids.iterator()
                                                                                            .next()))
                                                                    .getSingleResult())
                                                    .longValue());
            assertEquals(1, audits);
        }
        assertThrows(
                RuntimeException.class,
                () ->
                        QuarkusTransaction.requiringNew()
                                .run(
                                        () ->
                                                em.createNativeQuery(
                                                                "INSERT INTO booking_review(id,booking_id,rating) VALUES (?1,?2,3)")
                                                        .setParameter(1, UUID.randomUUID())
                                                        .setParameter(2, UUID.fromString(f.booking))
                                                        .executeUpdate()));
    }

    @Test
    void aggregatesAllProviderReviewsIndependentlyOfPageAndOtherProviders() {
        var a = fixture();
        var b = fixture();
        var c = fixture();
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.find(Booking.class, UUID.fromString(b.booking)).providerId =
                                        UUID.fromString(a.provider));
        for (var f : List.of(a, b, c)) status(f, Booking.Status.COMPLETED);
        submit(a.customer, a.booking, Map.of("rating", 5)).then().statusCode(201);
        submit(b.customer, b.booking, Map.of("rating", 2)).then().statusCode(201);
        submit(c.customer, c.booking, Map.of("rating", 1)).then().statusCode(201);
        String first =
                list(a.provider, "?size=1")
                        .then()
                        .body("total", is(2), "averageRating", is("3.50"), "items.size()", is(1))
                        .extract()
                        .path("items[0].id");
        list(a.provider, "?size=1&page=1")
                .then()
                .body("total", is(2), "averageRating", is("3.50"), "items[0].id", not(first));
        list(a.provider, "?size=1&page=2")
                .then()
                .body("items", empty(), "total", is(2), "averageRating", is("3.50"));
        list(c.provider, "").then().body("total", is(1), "averageRating", is("1.00"));
    }
}
