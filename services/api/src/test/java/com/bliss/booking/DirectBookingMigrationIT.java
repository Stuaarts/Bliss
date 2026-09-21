package com.bliss.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class DirectBookingMigrationIT {
    @Test
    void migratesExistingQuoteSnapshotAndEnforcesExactlyOneBookingSource() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17.6-alpine")) {
            db.start();
            Flyway.configure()
                    .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .target("5")
                    .load()
                    .migrate();
            try (var c =
                    DriverManager.getConnection(
                            db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                UUID user = UUID.randomUUID(),
                        provider = UUID.randomUUID(),
                        request = UUID.randomUUID(),
                        quote = UUID.randomUUID(),
                        booking = UUID.randomUUID(),
                        offering = UUID.randomUUID();
                UUID category = UUID.fromString("10000000-0000-0000-0000-000000000004");
                execute(
                        c,
                        "INSERT INTO bliss_user(id,oidc_subject,display_name) VALUES (?,'migration-customer','Demo')",
                        user);
                execute(
                        c,
                        "INSERT INTO provider_profile(id,display_name,provider_type,description,city) VALUES (?,'Demo','INDIVIDUAL','Demo','Toronto')",
                        provider);
                execute(
                        c,
                        "INSERT INTO service_request(id,customer_id,category_id,title,description,city,currency,status,created_at) VALUES (?,?,?,'Title','Request scope','Toronto','CAD','ACCEPTED',now())",
                        request,
                        user,
                        category);
                execute(
                        c,
                        "INSERT INTO quote(id,request_id,provider_id,description,price,currency,status,expires_at,created_at) VALUES (?,?,?,'Accepted quote scope',80.50,'CAD','ACCEPTED',now()+interval '1 day',now())",
                        quote,
                        request,
                        provider);
                execute(
                        c,
                        "INSERT INTO booking(id,request_id,quote_id,customer_id,provider_id,title,city,price,currency,status,created_at) VALUES (?,?,?,?,?,'Title','Toronto',80.50,'CAD','CREATED',now())",
                        booking,
                        request,
                        quote,
                        user,
                        provider);
                execute(
                        c,
                        "INSERT INTO service_offering(id,provider_id,category_id,title,description,price,currency) VALUES (?,?,?,'Direct','Fixed scope',45.50,'CAD')",
                        offering,
                        provider,
                        category);
                Flyway.configure()
                        .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                        .load()
                        .migrate();
                try (var query =
                        c.prepareStatement(
                                "SELECT description,price,quote_id,offering_id FROM booking WHERE id=?")) {
                    query.setObject(1, booking);
                    try (var row = query.executeQuery()) {
                        assertTrue(row.next());
                        assertEquals("Accepted quote scope", row.getString(1));
                        assertEquals("80.50", row.getBigDecimal(2).toPlainString());
                        assertEquals(quote, row.getObject(3));
                        assertNull(row.getObject(4));
                    }
                }
                assertEquals(
                        "23514",
                        assertThrows(
                                        SQLException.class,
                                        () ->
                                                execute(
                                                        c,
                                                        "UPDATE booking SET offering_id=?,offering_version=0,direct_key=? WHERE id=?",
                                                        offering,
                                                        UUID.randomUUID(),
                                                        booking))
                                .getSQLState());
                assertEquals(
                        "23514",
                        assertThrows(
                                        SQLException.class,
                                        () ->
                                                execute(
                                                        c,
                                                        "UPDATE booking SET request_id=NULL,quote_id=NULL WHERE id=?",
                                                        booking))
                                .getSQLState());
                assertEquals(
                        "23502",
                        assertThrows(
                                        SQLException.class,
                                        () ->
                                                execute(
                                                        c,
                                                        "UPDATE booking SET description=NULL WHERE id=?",
                                                        booking))
                                .getSQLState());
                execute(
                        c,
                        "UPDATE booking SET request_id=NULL,quote_id=NULL,offering_id=?,offering_version=0,direct_key=? WHERE id=?",
                        offering,
                        UUID.randomUUID(),
                        booking);
                assertEquals(
                        "23514",
                        assertThrows(
                                        SQLException.class,
                                        () ->
                                                execute(
                                                        c,
                                                        "UPDATE booking SET offering_version=NULL WHERE id=?",
                                                        booking))
                                .getSQLState());
            }
        }
    }

    void execute(Connection c, String sql, Object... values) throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }
}
