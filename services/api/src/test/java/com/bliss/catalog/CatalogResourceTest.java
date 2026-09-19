package com.bliss.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bliss.catalog.application.CatalogService;
import com.bliss.catalog.persistence.ServiceOffering;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CatalogResourceTest {
    private static final String CLEANING = "10000000-0000-0000-0000-000000000004";
    private static final String OFFERING = "40000000-0000-0000-0000-000000000001";
    @Inject CatalogService catalog;

    @Test
    void categoriesArePublicAndSeededByFlyway() {
        given().get("/api/v1/categories")
                .then()
                .statusCode(200)
                .header("X-Request-ID", not(blankOrNullString()))
                .body("size()", is(9))
                .body("slug", hasItems("cleaning", "plumbing", "pet-care"));
    }

    @Test
    void offeringsFilterAndPaginateWithoutLeakingEntityFields() {
        given().queryParam("categoryId", CLEANING)
                .get("/api/v1/offerings")
                .then()
                .statusCode(200)
                .body("total", is(1))
                .body("items[0].price", is("80.00"))
                .body("items[0].currency", is("CAD"))
                .body("items[0].provider.type", is("INDIVIDUAL"))
                .body("items[0]", not(hasKey("version")));
        given().queryParam("size", 1)
                .queryParam("page", 1)
                .get("/api/v1/offerings")
                .then()
                .statusCode(200)
                .body("total", is(2))
                .body("items.size()", is(1));
        given().queryParam("page", 50)
                .get("/api/v1/offerings")
                .then()
                .statusCode(200)
                .body("items", empty());
        given().queryParam("categoryId", UUID.randomUUID())
                .get("/api/v1/offerings")
                .then()
                .statusCode(200)
                .body("total", is(0))
                .body("items", empty());
    }

    @Test
    void readsOfferingAndProvider() {
        given().get("/api/v1/offerings/" + OFFERING)
                .then()
                .statusCode(200)
                .body("title", is("Home cleaning"));
        given().get("/api/v1/providers/30000000-0000-0000-0000-000000000001")
                .then()
                .statusCode(200)
                .body("city", is("Toronto"));
    }

    @Test
    void invalidPaginationIsRejected() {
        for (int size : new int[] {0, -1, 101}) {
            given().queryParam("size", size).get("/api/v1/offerings").then().statusCode(400);
        }
        given().queryParam("page", -1).get("/api/v1/offerings").then().statusCode(400);
    }

    @Test
    void missingOfferingHasSafeCorrelatedProblemResponse() {
        var response =
                given().get("/api/v1/offerings/" + UUID.randomUUID())
                        .then()
                        .statusCode(404)
                        .contentType("application/problem+json")
                        .body("status", is(404))
                        .body("detail", is("The requested resource was not found."))
                        .extract()
                        .response();
        assertEquals(response.header("X-Request-ID"), response.path("requestId"));
    }

    @Test
    @TestTransaction
    void inactiveOfferingIsHiddenFromListsAndDetails() {
        ServiceOffering entity = ServiceOffering.findById(UUID.fromString(OFFERING));
        entity.active = false;
        entity.flush();
        assertEquals(0, catalog.offerings(UUID.fromString(CLEANING), 0, 20).total());
        assertThrows(NotFoundException.class, () -> catalog.offering(entity.id));
    }

    @Test
    void operationalEndpointsAreAvailable() {
        given().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
        given().get("/q/openapi").then().statusCode(200).body(containsString("/api/v1/offerings"));
        given().get("/q/metrics").then().statusCode(200);
    }
}
