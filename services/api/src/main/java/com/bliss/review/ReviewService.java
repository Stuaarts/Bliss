package com.bliss.review;

import com.bliss.booking.Booking;
import com.bliss.booking.BookingResource;
import com.bliss.identity.AuditLog;
import com.bliss.identity.CurrentUser;
import com.bliss.provider.persistence.ProviderProfile;
import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@ApplicationScoped
@Transactional
public class ReviewService {
    @Inject EntityManager em;
    @Inject CurrentUser current;
    @Inject ProfileService profiles;
    @Inject BookingResource bookings;
    @Inject AuditLog audit;

    // Public DTO deliberately excludes booking, customer and identity information.
    public record View(UUID id, int rating, String comment, Instant createdAt) {}

    public record State(boolean eligible, View review) {}

    public record Page(List<View> items, int page, int size, long total, String averageRating) {}

    public record Created(View review, boolean created) {}

    private static final String SELECT = "SELECT id,rating,comment,created_at FROM booking_review ";

    public State state(UUID id) {
        bookings.get(id);
        Booking b = em.find(Booking.class, id);
        View existing = find(id);
        return new State(
                existing == null
                        && b.status == Booking.Status.COMPLETED
                        && current.capabilities().contains("customer")
                        && b.customerId.equals(profiles.requireOwnProfile().id),
                existing);
    }

    public Created create(UUID id, int rating, String text) {
        UUID user = profiles.requireOwnProfile().id;
        Booking b = em.find(Booking.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (b == null || !current.capabilities().contains("customer") || !b.customerId.equals(user))
            throw new NotFoundException();
        if (b.status != Booking.Status.COMPLETED) throw new ClientErrorException(409);
        String comment = text == null ? "" : text.strip();
        if (rating < 1 || rating > 5 || comment.length() > 2000 || comment.indexOf('\0') >= 0)
            throw new BadRequestException();
        View prior = find(id);
        if (prior != null) {
            if (prior.rating != rating || !prior.comment.equals(comment))
                throw new ClientErrorException(409);
            return new Created(prior, false);
        }
        UUID reviewId = UUID.randomUUID();
        em.createNativeQuery(
                        "INSERT INTO booking_review(id,booking_id,rating,comment) VALUES (?1,?2,?3,?4)")
                .setParameter(1, reviewId)
                .setParameter(2, id)
                .setParameter(3, rating)
                .setParameter(4, comment)
                .executeUpdate();
        audit.record(user, "REVIEW_CREATED", reviewId);
        return new Created(find(id), true);
    }

    private View find(UUID bookingId) {
        var rows =
                em.createNativeQuery(SELECT + "WHERE booking_id=?1")
                        .setParameter(1, bookingId)
                        .getResultList();
        return rows.isEmpty() ? null : view((Object[]) rows.getFirst());
    }

    public Page list(UUID providerId, int page, int size) {
        if (ProviderProfile.findById(providerId) == null) throw new NotFoundException();
        String predicate =
                " FROM booking_review r JOIN booking b ON b.id=r.booking_id WHERE b.provider_id=:provider";
        Object[] aggregate =
                (Object[])
                        em.createNativeQuery("SELECT count(*),avg(r.rating)" + predicate)
                                .setParameter("provider", providerId)
                                .getSingleResult();
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(
                                "SELECT r.id,r.rating,r.comment,r.created_at"
                                        + predicate
                                        + " ORDER BY r.created_at DESC,r.id")
                        .setParameter("provider", providerId)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList();
        String average =
                aggregate[1] == null
                        ? null
                        : ((BigDecimal) aggregate[1])
                                .setScale(2, RoundingMode.HALF_UP)
                                .toPlainString();
        return new Page(
                rows.stream().map(ReviewService::view).toList(),
                page,
                size,
                ((Number) aggregate[0]).longValue(),
                average);
    }

    private static View view(Object[] r) {
        return new View(
                (UUID) r[0],
                ((Number) r[1]).intValue(),
                (String) r[2],
                r[3] instanceof Instant i ? i : ((OffsetDateTime) r[3]).toInstant());
    }
}
