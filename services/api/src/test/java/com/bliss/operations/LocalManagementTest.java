package com.bliss.operations;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import com.bliss.identity.TestIdentityProvider;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Exercise the real local profile without ever using the developer's local database. */
@QuarkusTest
@TestProfile(LocalManagementTest.LocalProfile.class)
@QuarkusTestResource(TestIdentityProvider.class)
@QuarkusTestResource(value = LocalManagementTest.Database.class, restrictToAnnotatedClass = true)
class LocalManagementTest {
    public static class LocalProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "local";
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("bliss.workflow.dispatch-enabled", "false");
        }
    }

    public static class Database implements QuarkusTestResourceLifecycleManager {
        private PostgreSQLContainer db;

        @Override
        public Map<String, String> start() {
            db = new PostgreSQLContainer("postgres:17.6-alpine");
            try {
                db.start();
                return Map.of(
                        "quarkus.datasource.jdbc.url",
                        db.getJdbcUrl(),
                        "quarkus.datasource.username",
                        db.getUsername(),
                        "quarkus.datasource.password",
                        db.getPassword());
            } catch (RuntimeException failure) {
                db.close();
                throw failure;
            }
        }

        @Override
        public void stop() {
            if (db != null) db.close();
        }
    }

    @Test
    void localDemoManagementRemainsAvailableWithoutAuthentication() {
        given().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
        for (String path : new String[] {"/q/metrics", "/q/openapi", "/q/swagger-ui/"}) {
            given().get(path).then().statusCode(200);
        }
    }
}
