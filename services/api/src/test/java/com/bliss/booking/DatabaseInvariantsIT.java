package com.bliss.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Exercise constraints below the API and preserve populated released schemas. */
class DatabaseInvariantsIT {
    static final String CUSTOMER = "20000000-0000-0000-0000-000000000001";
    static final String PROVIDER = "20000000-0000-0000-0000-000000000002";
    static final String OFFERING = "20000000-0000-0000-0000-000000000003";
    static final String BOOKING = "20000000-0000-0000-0000-000000000004";
    static final String CONVERSATION = "20000000-0000-0000-0000-000000000005";
    static final String CASE = "20000000-0000-0000-0000-000000000006";
    static final String REQUEST = "20000000-0000-0000-0000-000000000007";
    static final String QUOTE = "20000000-0000-0000-0000-000000000008";
    static final String ADMIN = "20000000-0000-0000-0000-000000000009";
    static final String CATEGORY = "10000000-0000-0000-0000-000000000004";
    static final String CREATED = "2026-09-20T15:00:00.123456Z";

    Flyway flyway(PostgreSQLContainer db, String target) {
        return Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .target(target)
                .load();
    }

    Connection connect(PostgreSQLContainer db) throws SQLException {
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }

    void sql(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    String value(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }

    void rejects(Connection c, String state, String sql) {
        assertEquals(
                state, assertThrows(SQLException.class, () -> sql(c, sql), sql).getSQLState(), sql);
    }

    void seedV8(Connection c) throws SQLException {
        sql(
                c,
                "INSERT INTO bliss_user(id,oidc_subject,display_name) VALUES ('"
                        + CUSTOMER
                        + "','upgrade-customer','Migration demo')");
        sql(
                c,
                "INSERT INTO bliss_user(id,oidc_subject,display_name) VALUES ('"
                        + ADMIN
                        + "','upgrade-admin','Independent admin fixture')");
        sql(
                c,
                "INSERT INTO provider_profile(id,display_name,provider_type,description,city) VALUES ('"
                        + PROVIDER
                        + "','Migration provider','INDIVIDUAL','Demo','Toronto')");
        sql(
                c,
                "INSERT INTO service_offering(id,provider_id,category_id,title,description,price,currency) VALUES ('"
                        + OFFERING
                        + "','"
                        + PROVIDER
                        + "','"
                        + CATEGORY
                        + "','Upgrade service','Frozen scope',85.50,'CAD')");
        sql(
                c,
                "INSERT INTO service_request(id,customer_id,category_id,title,description,city,budget,currency,status,created_at) VALUES ('"
                        + REQUEST
                        + "','"
                        + CUSTOMER
                        + "','"
                        + CATEGORY
                        + "','Request','Scope','Toronto',100.00,'CAD','OPEN','"
                        + CREATED
                        + "')");
        sql(
                c,
                "INSERT INTO quote(id,request_id,provider_id,description,price,currency,status,expires_at,created_at) VALUES ('"
                        + QUOTE
                        + "','"
                        + REQUEST
                        + "','"
                        + PROVIDER
                        + "','Quote',80.50,'CAD','SUBMITTED','2026-09-30T15:00:00Z','"
                        + CREATED
                        + "')");
        sql(
                c,
                "INSERT INTO booking(id,customer_id,provider_id,offering_id,offering_version,direct_key,title,description,city,price,currency,status,created_at) VALUES ('"
                        + BOOKING
                        + "','"
                        + CUSTOMER
                        + "','"
                        + PROVIDER
                        + "','"
                        + OFFERING
                        + "',0,gen_random_uuid(),'Upgrade service','Frozen scope','Toronto',85.50,'CAD','COMPLETED','"
                        + CREATED
                        + "')");
        sql(
                c,
                "INSERT INTO payment(booking_id,amount,currency,status) VALUES ('"
                        + BOOKING
                        + "',85.50,'CAD','CAPTURED')");
        sql(
                c,
                "INSERT INTO payment_transaction(id,booking_id,operation) VALUES (gen_random_uuid(),'"
                        + BOOKING
                        + "','AUTHORIZE'),(gen_random_uuid(),'"
                        + BOOKING
                        + "','CAPTURE')");
        sql(
                c,
                "INSERT INTO booking_status_history(id,booking_id,status,occurred_at) VALUES (gen_random_uuid(),'"
                        + BOOKING
                        + "','COMPLETED','"
                        + CREATED
                        + "')");
        sql(
                c,
                "INSERT INTO conversation(id,booking_id,last_sequence) VALUES ('"
                        + CONVERSATION
                        + "','"
                        + BOOKING
                        + "',1)");
        sql(
                c,
                "INSERT INTO conversation_participant(conversation_id,user_id,participant_role) VALUES ('"
                        + CONVERSATION
                        + "','"
                        + CUSTOMER
                        + "','CUSTOMER')");
        sql(
                c,
                "INSERT INTO conversation_message(id,conversation_id,sender_id,client_key,sequence,body) VALUES (gen_random_uuid(),'"
                        + CONVERSATION
                        + "','"
                        + CUSTOMER
                        + "',gen_random_uuid(),1,'Keep this message')");
        sql(
                c,
                "INSERT INTO booking_review(id,booking_id,rating,comment) VALUES (gen_random_uuid(),'"
                        + BOOKING
                        + "',5,'Keep this review')");
        sql(
                c,
                "INSERT INTO audit_event(id,actor_user_id,action,resource_id) VALUES (gen_random_uuid(),'"
                        + CUSTOMER
                        + "','MIGRATION_FIXTURE','"
                        + BOOKING
                        + "')");
    }

    void seedV9(Connection c) throws SQLException {
        sql(c, "UPDATE booking SET status='DISPUTED' WHERE id='" + BOOKING + "'");
        sql(
                c,
                "INSERT INTO dispute(id,booking_id,opened_by,reason,status,created_at,evidence_deadline,workflow_instance_id) VALUES ('"
                        + CASE
                        + "','"
                        + BOOKING
                        + "','"
                        + CUSTOMER
                        + "','Keep this reason','UNDER_REVIEW','"
                        + CREATED
                        + "','2026-09-21T15:00:00Z','migration-dispute-instance')");
        sql(
                c,
                "INSERT INTO dispute_evidence(id,dispute_id,actor_user_id,actor_role,client_key,body) VALUES (gen_random_uuid(),'"
                        + CASE
                        + "','"
                        + CUSTOMER
                        + "','CUSTOMER',gen_random_uuid(),'Keep this evidence')");
        sql(
                c,
                "INSERT INTO dispute_decision(dispute_id,actor_user_id,client_key,outcome,rationale) VALUES ('"
                        + CASE
                        + "','"
                        + ADMIN
                        + "',gen_random_uuid(),'CUSTOMER_FAVORED','Keep this independent decision')");
        sql(
                c,
                "INSERT INTO dispute_history(id,dispute_id,status) VALUES (gen_random_uuid(),'"
                        + CASE
                        + "','UNDER_REVIEW')");
        sql(
                c,
                "INSERT INTO dispute_workflow_start(dispute_id,started) VALUES ('"
                        + CASE
                        + "',true)");
    }

    Map<String, String> snapshot(Connection c, List<String> tables) throws SQLException {
        var result = new TreeMap<String, String>();
        for (String table : tables)
            result.put(
                    table,
                    value(
                            c,
                            "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "
                                    + table
                                    + " t"));
        return result;
    }

    @Test
    void populatedV8AndV9UpgradesPreserveRecordsAndEnforceBoundaries() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            flyway(db, "8").migrate();
            try (var c = connect(db)) {
                seedV8(c);
                var oldTables =
                        List.of(
                                "bliss_user",
                                "provider_profile",
                                "service_offering",
                                "service_request",
                                "quote",
                                "booking",
                                "payment",
                                "payment_transaction",
                                "booking_status_history",
                                "conversation",
                                "conversation_participant",
                                "conversation_message",
                                "booking_review",
                                "audit_event");
                var before = snapshot(c, oldTables);
                flyway(db, "9").migrate();
                assertEquals(before, snapshot(c, oldTables));
                seedV9(c);
                var allTables = new ArrayList<>(oldTables);
                allTables.addAll(
                        List.of(
                                "dispute",
                                "dispute_evidence",
                                "dispute_decision",
                                "dispute_history",
                                "dispute_workflow_start"));
                var v9 = snapshot(c, allTables);
                flyway(db, "latest").migrate();
                flyway(db, "latest").validate();
                assertEquals(v9, snapshot(c, allTables));

                for (String boundary :
                        List.of(
                                "service_offering.price",
                                "service_request.budget",
                                "quote.price",
                                "booking.price",
                                "payment.amount")) {
                    String[] part = boundary.split("\\.");
                    rejects(
                            c,
                            "23514",
                            "UPDATE " + part[0] + " SET " + part[1] + "='NaN'::numeric");
                    rejects(c, "23514", "UPDATE " + part[0] + " SET " + part[1] + "=-0.01");
                }
                rejects(c, "23514", "UPDATE payment SET currency='cad'");
                rejects(c, "23514", "UPDATE payment SET simulated=false");
                rejects(c, "23514", "UPDATE booking_status_history SET status='UNKNOWN'");
                rejects(c, "23514", "UPDATE dispute_history SET status='UNKNOWN'");
                rejects(
                        c,
                        "23514",
                        "UPDATE dispute SET evidence_deadline=created_at-interval '1 second'");
                rejects(c, "23514", "UPDATE dispute SET status='RESOLVED_CUSTOMER'");
                rejects(c, "23514", "UPDATE dispute SET resolved_at=now()");
                rejects(c, "23514", "UPDATE booking_review SET rating=6");
                rejects(c, "23514", "UPDATE conversation_message SET sequence=0");
                rejects(c, "23514", "UPDATE conversation_message SET body='   '");
                rejects(c, "23503", "UPDATE conversation_message SET sender_id=gen_random_uuid()");
                rejects(c, "23503", "UPDATE booking SET provider_id=gen_random_uuid()");
                rejects(c, "23514", "UPDATE booking SET offering_id=NULL");
                rejects(
                        c,
                        "23505",
                        "INSERT INTO payment_transaction(id,booking_id,operation) VALUES (gen_random_uuid(),'"
                                + BOOKING
                                + "','CAPTURE')");
                rejects(
                        c,
                        "23505",
                        "INSERT INTO booking_review(id,booking_id,rating) VALUES (gen_random_uuid(),'"
                                + BOOKING
                                + "',4)");
                rejects(c, "23505", "INSERT INTO dispute_decision SELECT * FROM dispute_decision");
                rejects(
                        c,
                        "23505",
                        "INSERT INTO dispute_evidence SELECT gen_random_uuid(),dispute_id,actor_user_id,actor_role,client_key,body,created_at FROM dispute_evidence");
                assertEquals(
                        v9,
                        snapshot(c, allTables),
                        "Rejected writes must not change existing records");

                sql(c, "SET TIME ZONE 'Pacific/Auckland'");
                assertEquals(
                        "2026-09-20T15:00:00.123456Z",
                        value(
                                c,
                                "SELECT to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') FROM booking"));
                assertEquals("85.50", value(c, "SELECT price::text FROM booking"));
                sql(c, "UPDATE service_request SET budget=NULL");
                sql(
                        c,
                        "UPDATE dispute SET status='RESOLVED_CUSTOMER',resolved_at=created_at+interval '1 day'");
            }
        }
    }

    @Test
    void invalidLegacyMoneyStopsMigrationWithoutSilentRepair() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            flyway(db, "9").migrate();
            try (var c = connect(db)) {
                seedV8(c);
                sql(c, "UPDATE payment SET amount='NaN'::numeric");
                assertThrows(
                        org.flywaydb.core.api.FlywayException.class,
                        () -> flyway(db, "latest").migrate());
                assertEquals("NaN", value(c, "SELECT amount::text FROM payment"));
                assertEquals(
                        "9",
                        value(
                                c,
                                "SELECT max(version::int)::text FROM flyway_schema_history WHERE version IS NOT NULL"));
                assertEquals(
                        "0",
                        value(
                                c,
                                "SELECT count(*) FROM pg_constraint WHERE conname='ck_offering_price_finite'"),
                        "Failed PostgreSQL migration must roll back all new constraints");
                // Fixture-only correction using the immutable booking snapshot; no production
                // repair tool.
                sql(c, "UPDATE payment SET amount=85.50");
                flyway(db, "latest").migrate();
                flyway(db, "latest").validate();
                assertEquals("85.50", value(c, "SELECT amount::text FROM payment"));
            }
        }
    }

    @Test
    void emptyInstallUsesUuidUtcAndExactMoneyTypes() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            flyway(db, "latest").migrate();
            try (var c = connect(db)) {
                assertEquals(
                        "0",
                        value(
                                c,
                                "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name<>'flyway_schema_history' AND data_type IN ('real','double precision','timestamp without time zone')"));
                for (String table : List.of("booking", "dispute", "conversation", "booking_review"))
                    assertEquals(
                            "uuid",
                            value(
                                    c,
                                    "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='"
                                            + table
                                            + "' AND column_name='id'"));
                for (String boundary :
                        List.of(
                                "service_offering.price",
                                "service_request.budget",
                                "quote.price",
                                "booking.price",
                                "payment.amount")) {
                    String[] part = boundary.split("\\.");
                    assertEquals(
                            "12,2",
                            value(
                                    c,
                                    "SELECT numeric_precision||','||numeric_scale FROM information_schema.columns WHERE table_schema='public' AND table_name='"
                                            + part[0]
                                            + "' AND column_name='"
                                            + part[1]
                                            + "'"));
                }
                flyway(db, "latest").validate();
            }
        }
    }
}
