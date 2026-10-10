package com.bliss.operations;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Capture actual application log records while making HTTP requests. */
@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class RequestDiagnosticsTest {
    @TestHTTPResource URI base;
    private final ConcurrentLinkedQueue<LogRecord> records = new ConcurrentLinkedQueue<>();
    private final Handler capture =
            new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {}

                @Override
                public void close() {}
            };
    private final List<Logger> loggers =
            List.of(
                    Logger.getLogger("com.bliss.http.requests"),
                    Logger.getLogger("com.bliss.common.api.ApiErrors"));

    @BeforeEach
    void captureLogs() {
        loggers.forEach(logger -> logger.addHandler(capture));
    }

    @AfterEach
    void removeCapture() {
        loggers.forEach(logger -> logger.removeHandler(capture));
    }

    @Test
    void successValidationAndPrivatePathsLogOnlyOperationMetadata() {
        String marker = "private-request-" + UUID.randomUUID();
        String token = TestIdentityProvider.token("private-log-subject", "provider");
        var success =
                given().header("X-Request-ID", marker)
                        .header("Authorization", "Bearer " + token)
                        .header("Cookie", "session=" + marker)
                        .queryParam("private", marker)
                        .get("/api/v1/categories");
        assertEquals(200, success.statusCode());
        completion(success.header("X-Request-ID"), "GET", "CatalogResource.categories", 200);
        String resourceId = UUID.randomUUID().toString();
        var missing = given().get("/api/v1/offerings/" + resourceId);
        assertEquals(404, missing.statusCode());
        completion(missing.header("X-Request-ID"), "GET", "CatalogResource.offering", 404);
        var invalid =
                given().header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .body(
                                java.util.Map.of(
                                        "displayName",
                                        marker,
                                        "type",
                                        "INDIVIDUAL",
                                        "description",
                                        marker,
                                        "city",
                                        ""))
                        .post("/api/v1/providers/me");
        assertEquals(400, invalid.statusCode());
        assertEquals(invalid.header("X-Request-ID"), invalid.path("requestId"));
        completion(
                invalid.header("X-Request-ID"), "POST", "ProviderManagementResource.create", 400);
        String logs = messages();
        for (String privateValue : List.of(marker, token, "private-log-subject", resourceId)) {
            assertFalse(
                    logs.contains(privateValue), "Private request material entered diagnostics");
        }
        assertTrue(records.stream().allMatch(record -> record.getThrown() == null));
    }

    @Test
    void mappedFailureOmitsExceptionMessagesCausesAndStackTraces() {
        String marker = "private-exception-" + UUID.randomUUID();
        var response = given().get("/test/operations/private-failure/" + marker);
        assertEquals(500, response.statusCode());
        String id = response.header("X-Request-ID");
        assertEquals(id, response.path("requestId"));
        assertFalse(response.asString().contains(marker));
        completion(id, "GET", "PrivateFailureResource.fail", 500);
        var failures =
                records.stream()
                        .filter(
                                record ->
                                        message(record)
                                                .startsWith(
                                                        "event=api_failure requestId=" + id + " "))
                        .toList();
        assertEquals(1, failures.size());
        assertEquals(
                "event=api_failure requestId="
                        + id
                        + " exceptionType=java.lang.IllegalStateException",
                message(failures.getFirst()));
        assertNull(
                failures.getFirst().getThrown(),
                "Throwable must not carry private text into JSON logs");
        assertFalse(messages().contains(marker));
        assertFalse(messages().contains("private-cause-"));
    }

    @Test
    @Timeout(60)
    void concurrentResponsesKeepOneDiagnosticPerGeneratedRequestId() throws Exception {
        String supplied = "private-concurrent-" + UUID.randomUUID();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
                var pool = Executors.newFixedThreadPool(4)) {
            var calls = new ArrayList<Callable<String>>();
            for (int index = 0; index < 24; index++)
                calls.add(
                        () -> {
                            var response =
                                    client.send(
                                            HttpRequest.newBuilder(
                                                            base.resolve("/api/v1/categories"))
                                                    .timeout(Duration.ofSeconds(5))
                                                    .header("X-Request-ID", supplied)
                                                    .GET()
                                                    .build(),
                                            HttpResponse.BodyHandlers.ofString());
                            assertEquals(200, response.statusCode());
                            return response.headers().firstValue("X-Request-ID").orElseThrow();
                        });
            var ids = new HashSet<String>();
            for (var call : pool.invokeAll(calls, 30, TimeUnit.SECONDS)) {
                assertFalse(call.isCancelled());
                String id = call.get();
                assertTrue(ids.add(id));
                completion(id, "GET", "CatalogResource.categories", 200);
            }
            assertEquals(24, ids.size());
            assertEquals(
                    24,
                    records.stream()
                            .filter(record -> message(record).startsWith("event=rest_response "))
                            .count());
            assertFalse(messages().contains(supplied));
        }
    }

    private void completion(String id, String method, String operation, int status) {
        assertDoesNotThrow(() -> UUID.fromString(id));
        var matches =
                records.stream()
                        .map(this::message)
                        .filter(
                                value ->
                                        value.startsWith(
                                                "event=rest_response requestId=" + id + " "))
                        .toList();
        assertEquals(
                1, matches.size(), "Expected exactly one completion for this server request ID");
        assertTrue(
                matches.getFirst()
                        .matches(
                                "event=rest_response requestId="
                                        + id
                                        + " method="
                                        + method
                                        + " operation="
                                        + operation.replace(".", "\\.")
                                        + " status="
                                        + status
                                        + " durationMs=[0-9]+"));
    }

    private String messages() {
        return records.stream().map(this::message).reduce("", (a, b) -> a + "\n" + b);
    }

    private String message(LogRecord record) {
        return ExtLogRecord.wrap(record).getFormattedMessage();
    }
}
