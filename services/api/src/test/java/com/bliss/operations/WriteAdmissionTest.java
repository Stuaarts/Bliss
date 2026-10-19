package com.bliss.operations;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.*;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(WriteAdmissionTest.Paused.class)
@QuarkusTestResource(TestIdentityProvider.class)
class WriteAdmissionTest {
    public static class Paused implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "bliss.operations.writes-paused",
                    "true",
                    "bliss.workflow.dispatch-enabled",
                    "false",
                    "quarkus.flow.persistence.auto-restore",
                    "false");
        }
    }

    @Inject EntityManager em;

    @Test
    void readsAndLivenessRemainAvailableWhileAdmissionMakesReadinessDown() {
        given().get("/q/health/ready")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("checks.find { it.name == 'API write admission' }.status", is("DOWN"))
                .body(
                        "checks.find { it.name == 'Database connections health check' }.status",
                        is("UP"));
        given().get("/q/health/live").then().statusCode(200).body("status", is("UP"));
        given().get("/api/v1/categories").then().statusCode(200).body("size()", is(9));
        given().head("/api/v1/categories").then().statusCode(200);
        given().options("/api/v1/categories").then().statusCode(anyOf(is(200), is(204)));
        given().get("/api/v1/me").then().statusCode(401);
    }

    @Test
    void matchedWritesAreRefusedBeforeBodyValidationWithoutSavingOrEchoingInput() {
        long before =
                ((Number) em.createNativeQuery("SELECT count(*) FROM bliss_user").getSingleResult())
                        .longValue();
        var ids = new HashSet<String>();
        for (String role : List.of("customer", "provider", "admin", "operator")) {
            String token = TestIdentityProvider.token("paused-" + role, role);
            for (String method :
                    role.equals("provider")
                            ? List.of("POST", "PUT", "DELETE")
                            : List.of("POST", "PUT")) {
                String path =
                        switch (method) {
                            case "POST" -> "/api/v1/me/bootstrap";
                            case "PUT" -> "/api/v1/me";
                            default ->
                                    "/api/v1/providers/me/00000000-0000-0000-0000-000000000001/offerings/00000000-0000-0000-0000-000000000002";
                        };
                var response =
                        given().auth()
                                .oauth2(token)
                                .header("X-Request-ID", "private-untrusted-correlation")
                                .contentType("application/json")
                                .body("{malformed-private-input")
                                .request(method, path)
                                .then()
                                .statusCode(503)
                                .contentType("application/problem+json")
                                .extract()
                                .response();
                String id = response.header("X-Request-ID");
                assertDoesNotThrow(() -> UUID.fromString(id));
                assertTrue(ids.add(id));
                assertEquals(id, response.path("requestId"));
                assertEquals("API writes are temporarily paused.", response.path("detail"));
                assertFalse(response.asString().contains("private"));
                assertFalse(response.asString().contains(token));
                assertFalse(response.asString().contains("Exception"));
            }
        }
        assertEquals(
                before,
                ((Number) em.createNativeQuery("SELECT count(*) FROM bliss_user").getSingleResult())
                        .longValue());
    }
}
