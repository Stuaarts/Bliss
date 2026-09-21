package com.bliss.provider;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.identity.TestIdentityProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class OfferingManagementTest {
    private static final String CLEANING = "10000000-0000-0000-0000-000000000004";
    private final List<UUID> createdProviders = new ArrayList<>();
    @Inject EntityManager em;

    @AfterEach
    void hideTestOfferings() {
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            for (var id : createdProviders) {
                                em.createNativeQuery(
                                                "UPDATE service_offering SET active=false WHERE provider_id=?1")
                                        .setParameter(1, id)
                                        .executeUpdate();
                            }
                        });
    }

    private String token(String... roles) {
        String token = TestIdentityProvider.token("offering-test-" + UUID.randomUUID(), roles);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .post("/api/v1/me/bootstrap")
                .then()
                .statusCode(200);
        return token;
    }

    private Map<String, Object> providerInput(String type) {
        return Map.of(
                "displayName",
                "Test business",
                "description",
                "Local help",
                "city",
                "Toronto",
                "type",
                type);
    }

    private String provider(String token, String type) {
        String id =
                given().auth()
                        .oauth2(token)
                        .contentType("application/json")
                        .body(providerInput(type))
                        .post("/api/v1/providers/me")
                        .then()
                        .statusCode(201)
                        .body("type", is(type))
                        .extract()
                        .path("id");
        createdProviders.add(UUID.fromString(id));
        return id;
    }

    private Map<String, Object> input(String price) {
        return new HashMap<>(
                Map.of(
                        "title",
                        "Window cleaning",
                        "description",
                        "A careful clean",
                        "categoryId",
                        CLEANING,
                        "price",
                        price,
                        "currency",
                        "CAD"));
    }

    private String path(String provider) {
        return "/api/v1/providers/me/" + provider + "/offerings";
    }

    private String create(String token, String provider) {
        return given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(input("45.50"))
                .post(path(provider))
                .then()
                .statusCode(201)
                .body("price", is("45.50"))
                .body("version", is(0))
                .extract()
                .path("id");
    }

    @Test
    void onboardingCreatesOwnedIndividualAndCompanyWithoutGrantingRoles() {
        String owner = token("provider", "customer");
        String individual = provider(owner, "INDIVIDUAL");
        String company = provider(owner, "COMPANY");
        given().auth()
                .oauth2(owner)
                .get("/api/v1/providers/me")
                .then()
                .statusCode(200)
                .body("providerId", containsInAnyOrder(individual, company))
                .body("role", everyItem(is("OWNER")));
        given().auth()
                .oauth2(owner)
                .get("/api/v1/providers/me/" + company)
                .then()
                .statusCode(200)
                .body("type", is("COMPANY"))
                .body("version", is(0));
        String customer = token("customer");
        given().auth()
                .oauth2(customer)
                .contentType("application/json")
                .body(providerInput("COMPANY"))
                .post("/api/v1/providers/me")
                .then()
                .statusCode(403);
        given().contentType("application/json")
                .body(providerInput("COMPANY"))
                .post("/api/v1/providers/me")
                .then()
                .statusCode(401);
    }

    @Test
    void ownerCanPublishEditAndArchiveWithVersionAndAudit() {
        String owner = token("provider");
        String provider = provider(owner, "INDIVIDUAL");
        String id = create(owner, provider);
        given().get("/api/v1/offerings/" + id).then().statusCode(200).body("price", is("45.50"));
        var updated = input("51.2");
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(Map.of("offering", updated, "version", 0))
                .put(path(provider) + "/" + id)
                .then()
                .statusCode(200)
                .body("version", is(1))
                .body("price", is("51.20"));
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(Map.of("offering", updated, "version", 0))
                .put(path(provider) + "/" + id)
                .then()
                .statusCode(409);
        given().auth()
                .oauth2(owner)
                .queryParam("version", 0)
                .delete(path(provider) + "/" + id)
                .then()
                .statusCode(409);
        given().auth()
                .oauth2(owner)
                .queryParam("version", 1)
                .delete(path(provider) + "/" + id)
                .then()
                .statusCode(204);
        given().get("/api/v1/offerings/" + id).then().statusCode(404);
        given().auth()
                .oauth2(owner)
                .get(path(provider))
                .then()
                .statusCode(200)
                .body("total", is(1))
                .body("items[0].active", is(false))
                .body("items[0].version", is(2));
        // Repeat with current version is a no-op, never a second archive event.
        given().auth()
                .oauth2(owner)
                .queryParam("version", 2)
                .delete(path(provider) + "/" + id)
                .then()
                .statusCode(204);
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(Map.of("offering", updated, "version", 2))
                .put(path(provider) + "/" + id)
                .then()
                .statusCode(409);
        long auditCount =
                QuarkusTransaction.requiringNew()
                        .call(
                                () ->
                                        ((Number)
                                                        em.createNativeQuery(
                                                                        "SELECT count(*) FROM audit_event WHERE resource_id=?1")
                                                                .setParameter(
                                                                        1, UUID.fromString(id))
                                                                .getSingleResult())
                                                .longValue());
        assertEquals(3, auditCount);
    }

    @Test
    void otherProviderAndOrdinaryMemberCannotMutateOrReadPrivateOfferings() {
        String owner = token("provider");
        String other = token("provider");
        String provider = provider(owner, "COMPANY");
        String otherProvider = provider(other, "INDIVIDUAL");
        String id = create(owner, provider);
        given().auth().oauth2(other).get(path(provider)).then().statusCode(404);
        given().auth()
                .oauth2(other)
                .contentType("application/json")
                .body(input("1.00"))
                .post(path(provider))
                .then()
                .statusCode(404);
        for (String attemptedProvider : List.of(provider, otherProvider)) {
            given().auth()
                    .oauth2(other)
                    .get(path(attemptedProvider) + "/" + id)
                    .then()
                    .statusCode(404);
            given().auth()
                    .oauth2(other)
                    .contentType("application/json")
                    .body(Map.of("offering", input("1.00"), "version", 0))
                    .put(path(attemptedProvider) + "/" + id)
                    .then()
                    .statusCode(404);
            given().auth()
                    .oauth2(other)
                    .queryParam("version", 0)
                    .delete(path(attemptedProvider) + "/" + id)
                    .then()
                    .statusCode(404);
        }
        String otherUser = given().auth().oauth2(other).get("/api/v1/me").path("id");
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "INSERT INTO provider_member(provider_id,user_id,member_role) VALUES (?1,?2,'MEMBER')")
                                        .setParameter(1, UUID.fromString(provider))
                                        .setParameter(2, UUID.fromString(otherUser))
                                        .executeUpdate());
        given().auth().oauth2(other).get(path(provider)).then().statusCode(404);
        // Removing the signed role also removes access, even for an OWNER row.
        String subject =
                QuarkusTransaction.requiringNew()
                        .call(
                                () ->
                                        (String)
                                                em.createNativeQuery(
                                                                "SELECT oidc_subject FROM bliss_user u JOIN provider_member m ON m.user_id=u.id WHERE m.provider_id=?1 AND m.member_role='OWNER'")
                                                        .setParameter(1, UUID.fromString(provider))
                                                        .getSingleResult());
        given().auth()
                .oauth2(TestIdentityProvider.token(subject, "customer"))
                .get(path(provider))
                .then()
                .statusCode(403);
    }

    @Test
    void rejectsInvalidMoneyCategoriesPaginationAndIdentityInjection() {
        String owner = token("provider");
        String provider = provider(owner, "INDIVIDUAL");
        for (String price : List.of("-1", "1.001", "10000000000.00", "1e2", "NaN", "01.00")) {
            given().auth()
                    .oauth2(owner)
                    .contentType("application/json")
                    .body(input(price))
                    .post(path(provider))
                    .then()
                    .statusCode(400);
        }
        for (String currency : List.of("cad", "ZZZ", "JPY")) {
            var invalid = input("10.00");
            invalid.put("currency", currency);
            given().auth()
                    .oauth2(owner)
                    .contentType("application/json")
                    .body(invalid)
                    .post(path(provider))
                    .then()
                    .statusCode(400);
        }
        var invalid = input("10.00");
        invalid.put("categoryId", UUID.randomUUID());
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(invalid)
                .post(path(provider))
                .then()
                .statusCode(400);
        invalid = input("10.00");
        invalid.put("providerId", UUID.randomUUID());
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(invalid)
                .post(path(provider))
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(owner)
                .queryParam("size", 101)
                .get(path(provider))
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(owner)
                .queryParam("page", -1)
                .get(path(provider))
                .then()
                .statusCode(400);
        for (int i = 0; i < 3; i++) create(owner, provider);
        given().auth()
                .oauth2(owner)
                .queryParam("page", 1)
                .queryParam("size", 2)
                .get(path(provider))
                .then()
                .statusCode(200)
                .body("total", is(3))
                .body("items.size()", is(1));
        String id = given().auth().oauth2(owner).get(path(provider)).path("items[0].id");
        given().auth().oauth2(owner).delete(path(provider) + "/" + id).then().statusCode(400);
        given().auth()
                .oauth2(owner)
                .contentType("application/json")
                .body(Map.of("offering", input("10.00")))
                .put(path(provider) + "/" + id)
                .then()
                .statusCode(400);
    }
}
