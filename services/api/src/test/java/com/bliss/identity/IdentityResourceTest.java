package com.bliss.identity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class IdentityResourceTest {
    private static final String PROVIDER_SUBJECT = "50000000-0000-0000-0000-000000000002";
    private static final String PROVIDER_ID = "30000000-0000-0000-0000-000000000001";

    private String bootstrap(String subject, String... roles) {
        String token = TestIdentityProvider.token(subject, roles);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .post("/api/v1/me/bootstrap")
                .then()
                .statusCode(200);
        return token;
    }

    @Test
    void anonymousRequestsAreRejectedAndCatalogStaysPublic() {
        given().get("/api/v1/me").then().statusCode(401);
        given().contentType("application/json").post("/api/v1/me/bootstrap").then().statusCode(401);
        given().get("/api/v1/providers/me").then().statusCode(401);
        given().get("/api/v1/categories").then().statusCode(200);
    }

    @Test
    void rejectsWrongIssuerAudienceExpiredAndMalformedTokens() {
        long future = Instant.now().plusSeconds(300).getEpochSecond();
        for (String token :
                new String[] {
                    TestIdentityProvider.tokenWithoutSubject(),
                    TestIdentityProvider.token(
                            "invalid-user", "https://wrong.test", "bliss-api", future, "admin"),
                    TestIdentityProvider.token(
                            "invalid-user",
                            TestIdentityProvider.ISSUER,
                            "other-api",
                            future,
                            "admin"),
                    TestIdentityProvider.token(
                            "invalid-user",
                            TestIdentityProvider.ISSUER,
                            "bliss-api",
                            Instant.now().minusSeconds(120).getEpochSecond(),
                            "admin"),
                    "not.a.valid-token"
                }) {
            given().auth().oauth2(token).get("/api/v1/me").then().statusCode(401);
        }
        String valid = TestIdentityProvider.token("signature-user", "customer");
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        given().auth().oauth2(String.join(".", parts)).get("/api/v1/me").then().statusCode(401);
    }

    @Test
    void bootstrapIsIdempotentAndCapabilitiesCanCoexist() {
        String token = bootstrap("dual-" + UUID.randomUUID(), "customer", "provider");
        String id =
                given().auth()
                        .oauth2(token)
                        .get("/api/v1/me")
                        .then()
                        .statusCode(200)
                        .body("capabilities", containsInAnyOrder("customer", "provider"))
                        .extract()
                        .path("id");
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .post("/api/v1/me/bootstrap")
                .then()
                .statusCode(200)
                .body("id", is(id));
    }

    @Test
    void profileUpdatesAreBoundToTokenSubjectAndRejectIdentityInjection() {
        String a = bootstrap("profile-a-" + UUID.randomUUID(), "customer");
        String b = bootstrap("profile-b-" + UUID.randomUUID(), "customer");
        String otherName = given().auth().oauth2(b).get("/api/v1/me").path("displayName");
        given().auth()
                .oauth2(a)
                .contentType("application/json")
                .body(Map.of("displayName", "New name"))
                .put("/api/v1/me")
                .then()
                .statusCode(200)
                .body("displayName", is("New name"));
        given().auth().oauth2(b).get("/api/v1/me").then().body("displayName", is(otherName));
        given().auth()
                .oauth2(a)
                .contentType("application/json")
                .body(Map.of("displayName", "Bad", "userId", "someone-else"))
                .put("/api/v1/me")
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(a)
                .contentType("application/json")
                .body(Map.of("displayName", "  "))
                .put("/api/v1/me")
                .then()
                .statusCode(400);
    }

    @Test
    void customerRoleCannotAccessProviderManagement() {
        String token = bootstrap("customer-" + UUID.randomUUID(), "customer");
        given().auth().oauth2(token).get("/api/v1/providers/me").then().statusCode(403);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(update(0))
                .put("/api/v1/providers/me/" + PROVIDER_ID)
                .then()
                .statusCode(403);
    }

    @Test
    void providerCannotEditAnotherProvidersProfile() {
        String token = bootstrap("outsider-" + UUID.randomUUID(), "customer", "provider");
        given().auth()
                .oauth2(token)
                .get("/api/v1/providers/me")
                .then()
                .statusCode(200)
                .body("size()", is(0));
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(update(0))
                .put("/api/v1/providers/me/" + PROVIDER_ID)
                .then()
                .statusCode(404);
    }

    @Test
    void ownerCanUpdateAndStaleVersionIsRejected() {
        String token = bootstrap(PROVIDER_SUBJECT, "customer", "provider");
        given().auth()
                .oauth2(token)
                .get("/api/v1/providers/me")
                .then()
                .statusCode(200)
                .body("[0].providerId", is(PROVIDER_ID));
        Number version =
                given().auth()
                        .oauth2(token)
                        .contentType("application/json")
                        .body(update(0))
                        .put("/api/v1/providers/me/" + PROVIDER_ID)
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("version");
        assertEquals(1, version.longValue());
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(update(0))
                .put("/api/v1/providers/me/" + PROVIDER_ID)
                .then()
                .statusCode(409);
    }

    private Map<String, Object> update(long version) {
        return Map.of(
                "displayName",
                "Alex Home Services",
                "description",
                "Updated demo profile",
                "city",
                "Toronto",
                "version",
                version);
    }
}
