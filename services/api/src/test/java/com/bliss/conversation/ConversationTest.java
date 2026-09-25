package com.bliss.conversation;

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
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestIdentityProvider.class)
class ConversationTest {
    @Inject EntityManager em;

    record Actor(String token, String id, String subject) {}

    record Fixture(Actor customer, Actor owner, String provider, String booking) {}

    Actor actor(String... roles) {
        String subject = "chat-" + UUID.randomUUID();
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
                                        "Chat provider",
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
                                        "Chat service",
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

    Response open(Actor actor, String booking) {
        return given().auth()
                .oauth2(actor.token)
                .post("/api/v1/bookings/" + booking + "/conversation");
    }

    Response send(Actor actor, String id, UUID key, String text) {
        return given().auth()
                .oauth2(actor.token)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body(Map.of("body", text))
                .post("/api/v1/conversations/" + id + "/messages");
    }

    Response messages(Actor actor, String id, String query) {
        return given().auth()
                .oauth2(actor.token)
                .get("/api/v1/conversations/" + id + "/messages" + query);
    }

    long count(String sql, String id) {
        return QuarkusTransaction.requiringNew()
                .call(
                        () ->
                                ((Number)
                                                em.createNativeQuery(sql)
                                                        .setParameter(1, UUID.fromString(id))
                                                        .getSingleResult())
                                        .longValue());
    }

    void membership(Fixture f, Actor actor, String role) {
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "INSERT INTO provider_member(provider_id,user_id,member_role) VALUES (?1,?2,?3) ON CONFLICT(provider_id,user_id) DO UPDATE SET member_role=EXCLUDED.member_role")
                                        .setParameter(1, UUID.fromString(f.provider))
                                        .setParameter(2, UUID.fromString(actor.id))
                                        .setParameter(3, role)
                                        .executeUpdate());
    }

    @Test
    void participantsExchangeMessagesAndRetriesAreIdempotent() {
        var f = fixture();
        String id =
                open(f.customer, f.booking)
                        .then()
                        .statusCode(200)
                        .body("bookingId", is(f.booking), "lastSequence", is(0))
                        .extract()
                        .path("id");
        open(f.owner, f.booking).then().statusCode(200).body("id", is(id));
        assertEquals(
                2,
                count(
                        "SELECT count(*) FROM conversation_participant WHERE conversation_id=?1",
                        id));
        UUID key = UUID.randomUUID();
        String message =
                send(f.customer, id, key, "  Please arrive at ten.  ")
                        .then()
                        .statusCode(200)
                        .body(
                                "body",
                                is("Please arrive at ten."),
                                "mine",
                                is(true),
                                "senderRole",
                                is("CUSTOMER"),
                                "sequence",
                                is(1))
                        .extract()
                        .path("id");
        send(f.customer, id, key, "Please arrive at ten.")
                .then()
                .statusCode(200)
                .body("id", is(message));
        send(f.customer, id, key, "Changed text").then().statusCode(409);
        send(f.owner, id, key, "Confirmed.")
                .then()
                .statusCode(200)
                .body("senderRole", is("PROVIDER"), "sequence", is(2));
        messages(f.owner, id, "")
                .then()
                .statusCode(200)
                .body(
                        "items.body",
                        contains("Please arrive at ten.", "Confirmed."),
                        "items.mine",
                        contains(false, true),
                        "hasMore",
                        is(false));
        for (Actor actor : List.of(f.customer, f.owner))
            given().auth()
                    .oauth2(actor.token)
                    .get("/api/v1/conversations")
                    .then()
                    .statusCode(200)
                    .body("items.id", hasItem(id));
        assertEquals(
                2, count("SELECT count(*) FROM conversation_message WHERE conversation_id=?1", id));
        assertEquals(
                1,
                count(
                        "SELECT count(*) FROM audit_event WHERE resource_id=?1 AND action='MESSAGE_SENT'",
                        message));
    }

    @Test
    void outsidersMembersAndCapabilityLossCannotReadOrWrite() {
        var f = fixture();
        String id = open(f.customer, f.booking).then().statusCode(200).extract().path("id");
        Actor outsider = actor("customer", "provider"), admin = actor("admin");
        membership(f, outsider, "MEMBER");
        for (Actor actor : List.of(outsider, admin)) {
            open(actor, f.booking).then().statusCode(404);
            given().auth()
                    .oauth2(actor.token)
                    .get("/api/v1/conversations/" + id)
                    .then()
                    .statusCode(404);
            messages(actor, id, "").then().statusCode(404);
            send(actor, id, UUID.randomUUID(), "Unwanted").then().statusCode(404);
            given().auth()
                    .oauth2(actor.token)
                    .get("/api/v1/conversations")
                    .then()
                    .statusCode(200)
                    .body("total", is(0));
        }
        String noRole = TestIdentityProvider.token(f.customer.subject, "admin");
        given().auth().oauth2(noRole).get("/api/v1/conversations/" + id).then().statusCode(404);
        given().get("/api/v1/conversations/" + id + "/messages").then().statusCode(401);
        given().get("/api/v1/conversations").then().statusCode(401);
        given().post("/api/v1/bookings/" + f.booking + "/conversation").then().statusCode(401);
    }

    @Test
    void ownerRevocationAndReplacementDoNotLeakHistoricalChat() {
        var f = fixture();
        String id = open(f.customer, f.booking).then().statusCode(200).extract().path("id");
        send(f.owner, id, UUID.randomUUID(), "Private history").then().statusCode(200);
        membership(f, f.owner, "MEMBER");
        messages(f.owner, id, "").then().statusCode(404);
        send(f.owner, id, UUID.randomUUID(), "No longer allowed").then().statusCode(404);
        Actor replacement = actor("provider");
        membership(f, replacement, "OWNER");
        open(replacement, f.booking).then().statusCode(404);
        messages(replacement, id, "").then().statusCode(404);
        given().auth()
                .oauth2(replacement.token)
                .get("/api/v1/conversations")
                .then()
                .statusCode(200)
                .body("total", is(0));
        messages(f.customer, id, "")
                .then()
                .statusCode(200)
                .body("items.body", contains("Private history"));
    }

    @Test
    void cursorsAreBoundedAndStableWhenNewMessagesArrive() {
        var f = fixture();
        String id = open(f.customer, f.booking).path("id");
        for (int i = 1; i <= 5; i++)
            send(f.customer, id, UUID.randomUUID(), "Message " + i).then().statusCode(200);
        messages(f.customer, id, "?size=2")
                .then()
                .statusCode(200)
                .body("items.sequence", contains(4, 5), "hasMore", is(true));
        send(f.owner, id, UUID.randomUUID(), "New arrival").then().statusCode(200);
        messages(f.customer, id, "?before=4&size=2")
                .then()
                .statusCode(200)
                .body("items.sequence", contains(2, 3), "hasMore", is(true));
        messages(f.customer, id, "?before=2&size=2")
                .then()
                .statusCode(200)
                .body("items.sequence", contains(1), "hasMore", is(false));
        messages(f.customer, id, "?after=5&size=2")
                .then()
                .statusCode(200)
                .body("items.sequence", contains(6), "hasMore", is(false));
        messages(f.customer, id, "?after=0&size=2")
                .then()
                .statusCode(200)
                .body("items.sequence", contains(1, 2), "hasMore", is(true));
        given().auth()
                .oauth2(f.customer.token)
                .get("/api/v1/conversations?page=1&size=1")
                .then()
                .statusCode(200)
                .body("items", empty(), "total", is(1));
    }

    @Test
    void validationRejectsMalformedTextKeysCursorsAndSpoofedSender() {
        var f = fixture();
        String id = open(f.customer, f.booking).path("id");
        for (String body : List.of(" ", "x".repeat(2001), "bad\0text"))
            send(f.customer, id, UUID.randomUUID(), body).then().statusCode(400);
        for (String query :
                List.of("?size=0", "?size=101", "?before=0", "?after=-1", "?before=2&after=0"))
            messages(f.customer, id, query).then().statusCode(400);
        given().auth()
                .oauth2(f.customer.token)
                .contentType("application/json")
                .body(Map.of("body", "missing key"))
                .post("/api/v1/conversations/" + id + "/messages")
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(f.customer.token)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .body(Map.of("body", "spoof", "senderId", f.owner.id))
                .post("/api/v1/conversations/" + id + "/messages")
                .then()
                .statusCode(400);
        messages(f.customer, id, "").then().statusCode(200).body("items", empty());
    }

    @Test
    void concurrentOpenAndSendsProduceOneConversationAndOrderedMessages() throws Exception {
        var f = fixture();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var opened =
                    pool.invokeAll(
                            List.<Callable<String>>of(
                                    () ->
                                            open(f.customer, f.booking)
                                                    .then()
                                                    .statusCode(200)
                                                    .extract()
                                                    .path("id"),
                                    () ->
                                            open(f.owner, f.booking)
                                                    .then()
                                                    .statusCode(200)
                                                    .extract()
                                                    .path("id")));
            String id = opened.getFirst().get();
            assertEquals(id, opened.getLast().get());
            UUID key = UUID.randomUUID();
            var sent =
                    pool.invokeAll(
                            Collections.nCopies(
                                    4,
                                    (Callable<String>)
                                            () ->
                                                    send(f.customer, id, key, "One delivery")
                                                            .then()
                                                            .statusCode(200)
                                                            .extract()
                                                            .path("id")));
            for (var result : sent) assertEquals(sent.getFirst().get(), result.get());
            var distinct =
                    pool.invokeAll(
                            Collections.nCopies(
                                    4,
                                    (Callable<Integer>)
                                            () ->
                                                    send(
                                                                    f.owner,
                                                                    id,
                                                                    UUID.randomUUID(),
                                                                    "Distinct delivery")
                                                            .then()
                                                            .statusCode(200)
                                                            .extract()
                                                            .path("sequence")));
            Set<Integer> sequences = new HashSet<>();
            for (var result : distinct) sequences.add(result.get());
            assertEquals(Set.of(2, 3, 4, 5), sequences);
            messages(f.customer, id, "?after=0")
                    .then()
                    .statusCode(200)
                    .body("items.sequence", contains(1, 2, 3, 4, 5));
            assertEquals(
                    1,
                    count(
                            "SELECT count(*) FROM audit_event WHERE resource_id=?1 AND action='CONVERSATION_OPENED'",
                            id));
        }
    }
}
