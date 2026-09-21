package com.bliss.booking;

import com.bliss.identity.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Both booking sources commit the snapshot and durable workflow start intent together. */
@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class BookingCreation {
    @Inject EntityManager em;
    @Inject AuditLog audit;

    public void persist(Booking booking) {
        booking.id = UUID.randomUUID();
        booking.status = Booking.Status.CREATED;
        booking.createdAt = Instant.now();
        booking.persistAndFlush();
        em.createNativeQuery("INSERT INTO booking_workflow_start(booking_id) VALUES (?1)")
                .setParameter(1, booking.id)
                .executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO booking_status_history(id,booking_id,status,actor_user_id,occurred_at) VALUES (?1,?2,'CREATED',?3,?4)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, booking.id)
                .setParameter(3, booking.customerId)
                .setParameter(4, booking.createdAt)
                .executeUpdate();
        audit.record(booking.customerId, "BOOKING_CREATED", booking.id);
    }
}
