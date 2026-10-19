package com.bliss.workflow;

import com.bliss.identity.TestIdentityProvider;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Actual HTTP booking lifecycle and dispute admission shared by isolated recovery drills. */
record DisputeRecoveryFixture(
        String customer, String provider, String admin, String booking, String dispute) {
    static DisputeRecoveryFixture create(WorkflowRestartIT api, boolean captured) throws Exception {
        String customer = TestIdentityProvider.token("dispute-restart-customer", "customer");
        String provider = TestIdentityProvider.token("dispute-restart-provider", "provider");
        String admin = TestIdentityProvider.token("dispute-restart-admin", "admin");
        for (String token : List.of(customer, provider, admin))
            api.call("POST", "/api/v1/me/bootstrap", token, null, null, 200);
        String providerId =
                api.call(
                                "POST",
                                "/api/v1/providers/me",
                                provider,
                                Map.of(
                                        "displayName",
                                        "Dispute restart provider",
                                        "type",
                                        "INDIVIDUAL",
                                        "description",
                                        "Demo",
                                        "city",
                                        "Toronto"),
                                null,
                                201)
                        .path("id")
                        .asText();
        String offering =
                api.call(
                                "POST",
                                "/api/v1/providers/me/" + providerId + "/offerings",
                                provider,
                                Map.of(
                                        "title",
                                        "Dispute restart service",
                                        "description",
                                        "Fixed scope",
                                        "price",
                                        "80.50",
                                        "currency",
                                        "CAD",
                                        "categoryId",
                                        "10000000-0000-0000-0000-000000000004"),
                                null,
                                201)
                        .path("id")
                        .asText();
        String booking =
                api.call(
                                "POST",
                                "/api/v1/offerings/" + offering + "/bookings",
                                customer,
                                Map.of("offeringVersion", 0, "city", "Toronto"),
                                UUID.randomUUID().toString(),
                                201)
                        .path("id")
                        .asText();
        api.awaitStatus(booking, customer, "WAIT_PROVIDER");
        api.call(
                "POST",
                "/api/v1/bookings/" + booking + "/provider-accept",
                provider,
                Map.of(
                        "scheduledAt",
                        Instant.now().plusSeconds(10).truncatedTo(ChronoUnit.MICROS).toString()),
                UUID.randomUUID().toString(),
                202);
        var scheduled = api.awaitStatus(booking, customer, "SCHEDULED");
        long wait =
                Duration.between(
                                Instant.now(),
                                Instant.parse(scheduled.path("scheduledAt").asText()))
                        .toMillis();
        if (wait > 0) Thread.sleep(wait + 50);
        api.call(
                "POST",
                "/api/v1/bookings/" + booking + "/start",
                provider,
                null,
                UUID.randomUUID().toString(),
                202);
        api.awaitStatus(booking, customer, "IN_PROGRESS");
        api.call(
                "POST",
                "/api/v1/bookings/" + booking + "/mark-complete",
                provider,
                null,
                UUID.randomUUID().toString(),
                202);
        api.awaitStatus(booking, customer, "WAIT_CUSTOMER");
        if (captured) {
            api.call(
                    "POST",
                    "/api/v1/bookings/" + booking + "/confirm-completion",
                    customer,
                    null,
                    UUID.randomUUID().toString(),
                    202);
            api.awaitStatus(booking, customer, "COMPLETED");
        }
        String id =
                api.call(
                                "POST",
                                "/api/v1/bookings/" + booking + "/disputes",
                                customer,
                                Map.of("reason", "Work needs review; restart demo."),
                                null,
                                201)
                        .path("id")
                        .asText();
        return new DisputeRecoveryFixture(customer, provider, admin, booking, id);
    }
}
