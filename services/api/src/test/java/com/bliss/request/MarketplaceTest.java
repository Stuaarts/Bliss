package com.bliss.request;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class MarketplaceTest {
    private static final String CATEGORY = "10000000-0000-0000-0000-000000000004";
    @Inject EntityManager em;

    private String token(String... roles) {
        String token = TestIdentityProvider.token("market-" + UUID.randomUUID(), roles);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .post("/api/v1/me/bootstrap")
                .then()
                .statusCode(200);
        return token;
    }

    private String provider(String token) {
        return given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(
                        Map.of(
                                "displayName",
                                "Local provider",
                                "description",
                                "Demo",
                                "city",
                                "Toronto",
                                "type",
                                "INDIVIDUAL"))
                .post("/api/v1/providers/me")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private Map<String, Object> input() {
        return new HashMap<>(
                Map.of(
                        "title",
                        "Clean windows",
                        "description",
                        "Clean six ground-floor windows",
                        "city",
                        "Toronto",
                        "categoryId",
                        CATEGORY,
                        "budget",
                        "100.00",
                        "currency",
                        "CAD"));
    }

    private String request(String token) {
        return given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(input())
                .post("/api/v1/service-requests")
                .then()
                .statusCode(201)
                .body("status", is("OPEN"))
                .body("budget", is("100.00"))
                .extract()
                .path("id");
    }

    private Map<String, Object> quoteInput(String provider) {
        return new HashMap<>(
                Map.of(
                        "providerId",
                        provider,
                        "description",
                        "All six windows included",
                        "price",
                        "80.50",
                        "currency",
                        "CAD",
                        "expiresAt",
                        Instant.now().plusSeconds(3600).toString()));
    }

    private String quote(String providerToken, String providerId, String requestId) {
        return given().auth()
                .oauth2(providerToken)
                .contentType("application/json")
                .body(quoteInput(providerId))
                .post("/api/v1/service-requests/" + requestId + "/quotes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private Response accept(String token, String quoteId) {
        return given().auth().oauth2(token).post("/api/v1/quotes/" + quoteId + "/accept");
    }

    private long count(String query, String id) {
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
    void ownRequestsAreEditableVersionedAndPrivate() {
        String customer = token("customer");
        String outsider = token("customer");
        String id = request(customer);
        given().get("/api/v1/service-requests").then().statusCode(401);
        given().auth()
                .oauth2(outsider)
                .get("/api/v1/service-requests/" + id)
                .then()
                .statusCode(404);
        given().auth().oauth2(outsider).get("/api/v1/service-requests").then().body("total", is(0));
        var changed = input();
        changed.put("title", "Clean eight windows");
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(Map.of("request", changed, "version", 0))
                .put("/api/v1/service-requests/" + id)
                .then()
                .statusCode(200)
                .body("version", is(1));
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(Map.of("request", changed, "version", 0))
                .put("/api/v1/service-requests/" + id)
                .then()
                .statusCode(409);
        given().auth()
                .oauth2(outsider)
                .queryParam("version", 1)
                .delete("/api/v1/service-requests/" + id)
                .then()
                .statusCode(404);
        given().auth()
                .oauth2(customer)
                .queryParam("version", 1)
                .delete("/api/v1/service-requests/" + id)
                .then()
                .statusCode(204);
        given().auth()
                .oauth2(customer)
                .get("/api/v1/service-requests/" + id)
                .then()
                .body("status", is("CANCELLED"));
        String provider = token("provider");
        given().auth()
                .oauth2(provider)
                .get("/api/v1/provider/opportunities/" + id)
                .then()
                .statusCode(404);
        given().auth()
                .oauth2(customer)
                .queryParam("size", 101)
                .get("/api/v1/service-requests")
                .then()
                .statusCode(400);
    }

    @Test
    void quoteRequiresOwnedProviderAndImmutableAgreedScope() {
        String customer = token("customer");
        String owner = token("provider");
        String other = token("provider");
        String request = request(customer);
        String provider = provider(owner);
        given().auth()
                .oauth2(owner)
                .get("/api/v1/provider/opportunities/" + request)
                .then()
                .statusCode(200)
                .body("title", is("Clean windows"))
                .body("$", not(hasKey("customerId")));
        given().auth()
                .oauth2(customer)
                .get("/api/v1/provider/opportunities")
                .then()
                .statusCode(403);
        given().auth()
                .oauth2(other)
                .contentType("application/json")
                .body(quoteInput(provider))
                .post("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(404);
        var wrongCurrency = quoteInput(provider);
        wrongCurrency.put("currency", "USD");
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(wrongCurrency)
                .post("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(400);
        String quote = quote(owner, provider, request);
        given().auth().oauth2(customer).get("/api/v1/quotes/" + quote).then().statusCode(200);
        given().auth().oauth2(owner).get("/api/v1/quotes/" + quote).then().statusCode(200);
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(quoteInput(provider))
                .post("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(409);
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(Map.of("request", input(), "version", 0))
                .put("/api/v1/service-requests/" + request)
                .then()
                .statusCode(409);
        given().auth()
                .oauth2(owner)
                .get("/api/v1/providers/me/" + provider + "/quotes")
                .then()
                .statusCode(200)
                .body("items[0].id", is(quote));
        given().auth()
                .oauth2(other)
                .get("/api/v1/providers/me/" + provider + "/quotes")
                .then()
                .statusCode(404);
        given().auth()
                .oauth2(owner)
                .get("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(403);
    }

    @Test
    void acceptanceCreatesOneOwnedBookingAndRepeatsWithoutSideEffects() {
        String customer = token("customer");
        String outsider = token("customer");
        String owner = token("provider");
        String provider = provider(owner);
        String request = request(customer);
        String quote = quote(owner, provider, request);
        accept(outsider, quote).then().statusCode(404);
        given().auth().oauth2(outsider).get("/api/v1/quotes/" + quote).then().statusCode(404);
        accept(owner, quote).then().statusCode(403);
        String booking =
                accept(customer, quote)
                        .then()
                        .statusCode(200)
                        .body("status", is("CREATED"))
                        .body("price", is("80.50"))
                        .body("currency", is("CAD"))
                        .extract()
                        .path("id");
        accept(customer, quote).then().statusCode(200).body("id", is(booking));
        given().auth().oauth2(customer).get("/api/v1/bookings/" + booking).then().statusCode(200);
        given().auth().oauth2(owner).get("/api/v1/bookings/" + booking).then().statusCode(200);
        given().auth().oauth2(outsider).get("/api/v1/bookings/" + booking).then().statusCode(404);
        given().auth().oauth2(owner).get("/api/v1/bookings").then().body("total", is(1));
        given().auth().oauth2(outsider).get("/api/v1/bookings").then().body("total", is(0));
        given().auth()
                .oauth2(customer)
                .queryParam("version", 1)
                .delete("/api/v1/service-requests/" + request)
                .then()
                .statusCode(409);
        assertEquals(1, count("SELECT count(*) FROM booking WHERE request_id=?1", request));
        assertEquals(
                1,
                count("SELECT count(*) FROM booking_status_history WHERE booking_id=?1", booking));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM audit_event WHERE resource_id=?1 AND action='QUOTE_ACCEPTED'",
                        quote));
    }

    @Test
    void concurrentDifferentQuoteAcceptancesHaveExactlyOneWinner() throws Exception {
        String customer = token("customer");
        String owner = token("provider");
        String request = request(customer);
        String a = quote(owner, provider(owner), request);
        String b = quote(owner, provider(owner), request);
        List<Response> responses = race(() -> accept(customer, a), () -> accept(customer, b));
        assertEquals(
                List.of(200, 409), responses.stream().map(Response::statusCode).sorted().toList());
        assertEquals(1, count("SELECT count(*) FROM booking WHERE request_id=?1", request));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM quote WHERE request_id=?1 AND status='ACCEPTED'",
                        request));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM quote WHERE request_id=?1 AND status='DECLINED'",
                        request));
    }

    @Test
    void concurrentSameQuoteRetriesReturnSameBooking() throws Exception {
        String customer = token("customer");
        String owner = token("provider");
        String request = request(customer);
        String quote = quote(owner, provider(owner), request);
        List<Response> responses =
                race(() -> accept(customer, quote), () -> accept(customer, quote));
        assertEquals(List.of(200, 200), responses.stream().map(Response::statusCode).toList());
        assertEquals(responses.get(0).<String>path("id"), responses.get(1).<String>path("id"));
        assertEquals(1, count("SELECT count(*) FROM booking WHERE request_id=?1", request));
    }

    @Test
    void cancellationAndAcceptanceSerializeWithoutPartialBooking() throws Exception {
        String customer = token("customer");
        String owner = token("provider");
        String request = request(customer);
        String quote = quote(owner, provider(owner), request);
        var responses =
                race(
                        () -> accept(customer, quote),
                        () ->
                                given().auth()
                                        .oauth2(customer)
                                        .queryParam("version", 0)
                                        .delete("/api/v1/service-requests/" + request));
        if (responses.get(0).statusCode() == 200) {
            assertEquals(409, responses.get(1).statusCode());
            assertEquals(1, count("SELECT count(*) FROM booking WHERE request_id=?1", request));
        } else {
            assertEquals(409, responses.get(0).statusCode());
            assertEquals(204, responses.get(1).statusCode());
            assertEquals(0, count("SELECT count(*) FROM booking WHERE request_id=?1", request));
        }
    }

    @Test
    void expiredDeclinedCancelledAndSelfQuotesCannotBeAccepted() {
        String customer = token("customer", "provider");
        String owner = token("provider");
        String request = request(customer);
        String provider = provider(owner);
        String quote = quote(owner, provider, request);
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery("UPDATE quote SET expires_at=?1 WHERE id=?2")
                                        .setParameter(1, Instant.now().minusSeconds(5))
                                        .setParameter(2, UUID.fromString(quote))
                                        .executeUpdate());
        accept(customer, quote).then().statusCode(409);
        given().auth()
                .oauth2(customer)
                .post("/api/v1/quotes/" + quote + "/decline")
                .then()
                .statusCode(200)
                .body("status", is("DECLINED"));
        given().auth()
                .oauth2(customer)
                .post("/api/v1/quotes/" + quote + "/decline")
                .then()
                .statusCode(200);
        accept(customer, quote).then().statusCode(409);
        given().auth()
                .oauth2(customer)
                .get("/api/v1/provider/opportunities/" + request)
                .then()
                .statusCode(404);
        String ownProvider = provider(customer);
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(quoteInput(ownProvider))
                .post("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(404);
        given().auth()
                .oauth2(customer)
                .queryParam("version", 0)
                .delete("/api/v1/service-requests/" + request)
                .then()
                .statusCode(204);
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(quoteInput(provider))
                .post("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(404);
        assertEquals(0, count("SELECT count(*) FROM booking WHERE request_id=?1", request));
    }

    @Test
    void invalidAmountsCategoriesAndIdentityFieldsAreRejected() {
        String customer = token("customer");
        for (String value : List.of("-1", "0.001", "10000000000", "1e3")) {
            var data = input();
            data.put("budget", value);
            given().auth()
                    .oauth2(customer)
                    .contentType("application/json")
                    .body(data)
                    .post("/api/v1/service-requests")
                    .then()
                    .statusCode(400);
        }
        var data = input();
        data.put("customerId", UUID.randomUUID());
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(data)
                .post("/api/v1/service-requests")
                .then()
                .statusCode(400);
        data = input();
        data.put("categoryId", UUID.randomUUID());
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(data)
                .post("/api/v1/service-requests")
                .then()
                .statusCode(400);
        String owner = token("provider");
        String provider = provider(owner);
        String request = request(customer);
        var quote = quoteInput(provider);
        quote.put("expiresAt", Instant.now().minusSeconds(5).toString());
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(quote)
                .post("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(400);
        quote = quoteInput(provider);
        quote.put("price", "-10");
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(quote)
                .post("/api/v1/service-requests/" + request + "/quotes")
                .then()
                .statusCode(400);
    }

    private List<Response> race(Callable<Response> a, Callable<Response> b) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var barrier = new CyclicBarrier(2);
            var first =
                    executor.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return a.call();
                            });
            var second =
                    executor.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return b.call();
                            });
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
}
