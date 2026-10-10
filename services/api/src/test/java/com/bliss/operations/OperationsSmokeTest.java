package com.bliss.operations;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Local/test-profile readiness evidence, not a production capacity or SLO test. */
@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class OperationsSmokeTest {
    @TestHTTPResource URI base;
    @Inject ObjectMapper json;

    @Test
    void healthAndOpenApiAreAvailableInTestProfile() {
        for (String path : new String[] {"/q/health/live", "/q/health/ready"}) {
            given().get(path).then().statusCode(200).body("status", is("UP"));
        }
        given().accept("application/json")
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .body("info.title", is("Bliss Marketplace API"))
                .body("paths", hasKey("/api/v1/bookings/{id}/disputes"));
    }

    @Test
    void validationErrorsUseServerCorrelationWithoutEchoingRequestContent() {
        String supplied = "untrusted-client-correlation";
        var response =
                given().header("X-Request-ID", supplied)
                        .queryParam("size", 101)
                        .get("/api/v1/offerings")
                        .then()
                        .statusCode(400)
                        .contentType("application/problem+json")
                        .extract()
                        .response();
        String requestId = response.header("X-Request-ID");
        assertDoesNotThrow(() -> UUID.fromString(requestId));
        assertNotEquals(supplied, requestId);
        assertEquals(requestId, response.path("requestId"));
        assertEquals("Check request parameters and try again.", response.path("detail"));
        assertFalse(response.asString().contains(supplied));
        assertFalse(response.asString().contains("ConstraintViolationException"));
    }

    @Test
    void endpointReturnConstraintRemainsAGenericServerError() {
        var response =
                given().get("/test/operations/invalid-return")
                        .then()
                        .statusCode(500)
                        .contentType("application/problem+json")
                        .extract()
                        .response();
        assertEquals("The request could not be completed.", response.path("detail"));
        assertDoesNotThrow(() -> UUID.fromString(response.header("X-Request-ID")));
        assertEquals(response.header("X-Request-ID"), response.path("requestId"));
        assertFalse(response.asString().contains("brokenContract"));
        assertFalse(response.asString().contains("violations"));
    }

    @Test
    void prometheusRecordsPublicCatalogRequests() {
        given().get("/api/v1/categories").then().statusCode(200);
        String metrics = given().get("/q/metrics").then().statusCode(200).extract().asString();
        assertTrue(
                metrics.lines()
                        .anyMatch(
                                line ->
                                        line.startsWith("http_server_requests_seconds_count{")
                                                && line.contains("uri=\"/api/v1/categories\"")
                                                && line.contains("status=\"200\"")
                                                && Double.parseDouble(
                                                                line.substring(
                                                                        line.lastIndexOf(' ') + 1))
                                                        >= 1),
                "Expected a successful catalog request in the actual Prometheus registry");
    }

    @Test
    @Timeout(60)
    void boundedConcurrentCatalogReadsRemainCorrectAndSeparatelyCorrelated() throws Exception {
        // Fixed, small read-only workload: never points at a user-supplied host,
        // never authenticates or mutates domain records, and always shuts down.
        record Sample(long nanos, String requestId) {}
        int count = 80;
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                var pool = Executors.newFixedThreadPool(4)) {
            var requests = new ArrayList<Callable<Sample>>();
            for (int index = 0; index < count; index++) {
                requests.add(
                        () -> {
                            long started = System.nanoTime();
                            var request =
                                    HttpRequest.newBuilder(base.resolve("/api/v1/categories"))
                                            .timeout(Duration.ofSeconds(5))
                                            .header("X-Request-ID", "client-smoke-value")
                                            .GET()
                                            .build();
                            var response =
                                    client.send(request, HttpResponse.BodyHandlers.ofString());
                            assertEquals(200, response.statusCode());
                            var body = json.readTree(response.body());
                            assertTrue(body.isArray());
                            assertEquals(9, body.size());
                            assertTrue(body.findValuesAsText("slug").contains("gardening"));
                            String id = response.headers().firstValue("X-Request-ID").orElseThrow();
                            assertDoesNotThrow(() -> UUID.fromString(id));
                            return new Sample(System.nanoTime() - started, id);
                        });
            }
            var results = pool.invokeAll(requests, 30, TimeUnit.SECONDS);
            var timings = new ArrayList<Long>();
            var ids = new HashSet<String>();
            for (var result : results) {
                assertFalse(result.isCancelled(), "Bounded catalog workload exceeded its deadline");
                var sample = result.get();
                timings.add(sample.nanos());
                assertTrue(
                        ids.add(sample.requestId()),
                        "Request IDs must be unique across concurrent reads");
            }
            timings.sort(Long::compareTo);
            assertEquals(count, ids.size());
            System.out.printf(
                    "Operations smoke: requests=%d concurrency=4 p50_ms=%.3f p95_ms=%.3f max_ms=%.3f; no production SLO asserted.%n",
                    count,
                    timings.get(39) / 1_000_000.0,
                    timings.get(75) / 1_000_000.0,
                    timings.get(79) / 1_000_000.0);
        }
    }
}
