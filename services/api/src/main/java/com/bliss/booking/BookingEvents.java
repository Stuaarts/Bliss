package com.bliss.booking;

import com.bliss.identity.CurrentUser;
import com.bliss.provider.ProviderAccess;
import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@Transactional
public class BookingEvents {
    public enum Type {
        PROVIDER_ACCEPT,
        PROVIDER_REJECT,
        START,
        MARK_COMPLETE,
        CUSTOMER_CONFIRM,
        CANCEL
    }

    public record Receipt(UUID eventId, UUID bookingId, String type, boolean processed) {}

    @Inject EntityManager em;
    @Inject CurrentUser current;
    @Inject ProfileService profiles;
    @Inject ProviderAccess access;

    public Receipt submit(UUID bookingId, UUID eventId, Type type, Instant scheduledAt) {
        Booking b = em.find(Booking.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (b == null) throw new NotFoundException();
        UUID actor = profiles.requireOwnProfile().id;
        boolean customerAction = type == Type.CUSTOMER_CONFIRM || type == Type.CANCEL;
        String role = customerAction ? "customer" : "provider";
        if (!current.capabilities().contains(role)) throw new ForbiddenException();
        if (customerAction) {
            if (!b.customerId.equals(actor)) throw new NotFoundException();
        } else access.requireOwner(b.providerId);
        @SuppressWarnings("unchecked")
        List<Object[]> prior =
                em.createNativeQuery(
                                "SELECT booking_id,actor_user_id,event_type,scheduled_at,processed_at FROM booking_event WHERE id=?1")
                        .setParameter(1, eventId)
                        .getResultList();
        if (!prior.isEmpty()) {
            Object[] row = prior.getFirst();
            Instant previous =
                    row[3] == null
                            ? null
                            : row[3] instanceof Instant i
                                    ? i
                                    : ((java.time.OffsetDateTime) row[3]).toInstant();
            if (!bookingId.equals(row[0])
                    || !actor.equals(row[1])
                    || !type.name().equals(row[2])
                    || !Objects.equals(previous, scheduledAt))
                throw new ClientErrorException("Event key already used", 409);
            return new Receipt(eventId, bookingId, type.name(), row[4] != null);
        }
        if (type == Type.PROVIDER_ACCEPT) {
            if (scheduledAt == null || !scheduledAt.isAfter(Instant.now()))
                throw new BadRequestException("Future schedule required");
            // PostgreSQL stores microseconds; reject finer precision so retries compare exactly.
            if (scheduledAt.getNano() % 1000 != 0)
                throw new BadRequestException("Use microsecond timestamp precision");
        } else if (scheduledAt != null) throw new BadRequestException("Unexpected schedule");
        Instant occurredAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (!allowed(b, type, occurredAt))
            throw new ClientErrorException("Invalid booking transition", 409);
        Number pending =
                (Number)
                        em.createNativeQuery(
                                        "SELECT count(*) FROM booking_event WHERE booking_id=?1 AND processed_at IS NULL")
                                .setParameter(1, b.id)
                                .getSingleResult();
        if (pending.longValue() > 0)
            throw new ClientErrorException("Another action is pending", 409);
        int inserted =
                em.createNativeQuery(
                                "INSERT INTO booking_event(id,booking_id,actor_user_id,event_type,scheduled_at,occurred_at) VALUES (?1,?2,?3,?4,?5,?6) ON CONFLICT (id) DO NOTHING")
                        .setParameter(1, eventId)
                        .setParameter(2, b.id)
                        .setParameter(3, actor)
                        .setParameter(4, type.name())
                        .setParameter(5, scheduledAt)
                        .setParameter(6, occurredAt)
                        .executeUpdate();
        if (inserted != 1) throw new ClientErrorException("Event key already used", 409);
        return new Receipt(eventId, bookingId, type.name(), false);
    }

    public static boolean allowed(Booking b, Type type, Instant now) {
        return switch (type) {
            case PROVIDER_ACCEPT, PROVIDER_REJECT ->
                    b.status == Booking.Status.WAIT_PROVIDER && now.isBefore(b.decisionDeadline);
            case START -> b.status == Booking.Status.SCHEDULED && !now.isBefore(b.scheduledAt);
            case MARK_COMPLETE -> b.status == Booking.Status.IN_PROGRESS;
            case CUSTOMER_CONFIRM -> b.status == Booking.Status.WAIT_CUSTOMER;
            case CANCEL ->
                    b.status == Booking.Status.CREATED
                            || b.status == Booking.Status.WAIT_PROVIDER
                            || b.status == Booking.Status.SCHEDULED;
        };
    }
}
