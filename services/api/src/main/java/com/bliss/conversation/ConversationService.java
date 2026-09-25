package com.bliss.conversation;

import com.bliss.booking.Booking;
import com.bliss.booking.BookingResource;
import com.bliss.catalog.api.CatalogModels;
import com.bliss.identity.AuditLog;
import com.bliss.identity.CurrentUser;
import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@ApplicationScoped
@Transactional
public class ConversationService {
    @Inject EntityManager em;
    @Inject CurrentUser current;
    @Inject ProfileService profiles;
    @Inject BookingResource bookings;
    @Inject AuditLog audit;

    public record View(
            UUID id, UUID bookingId, String title, Instant createdAt, long lastSequence) {}

    public record Message(
            UUID id,
            long sequence,
            String body,
            String senderName,
            String senderRole,
            boolean mine,
            Instant createdAt) {}

    public record Messages(List<Message> items, boolean hasMore) {}

    // Snapshot membership is required as well as present-day booking access. New
    // business owners do not silently inherit historical private conversations.
    private String eligibility() {
        boolean customer = current.capabilities().contains("customer");
        boolean provider = current.capabilities().contains("provider");
        return "cp.user_id=:user AND (("
                + customer
                + " AND cp.participant_role='CUSTOMER' AND b.customer_id=:user) OR ("
                + provider
                + " AND cp.participant_role='PROVIDER' AND EXISTS (SELECT 1 FROM provider_member pm WHERE pm.provider_id=b.provider_id AND pm.user_id=:user AND pm.member_role='OWNER')))";
    }

    private static final String FROM =
            " FROM conversation c JOIN booking b ON b.id=c.booking_id JOIN conversation_participant cp ON cp.conversation_id=c.id ";
    private static final String SELECT =
            "SELECT c.id,c.booking_id,b.title,c.created_at,c.last_sequence";

    public View get(UUID id) {
        UUID user = profiles.requireOwnProfile().id;
        var rows =
                em.createNativeQuery(SELECT + FROM + "WHERE c.id=:id AND " + eligibility())
                        .setParameter("id", id)
                        .setParameter("user", user)
                        .getResultList();
        if (rows.isEmpty()) throw new NotFoundException();
        return view((Object[]) rows.getFirst());
    }

    public CatalogModels.Page<View> list(int page, int size) {
        UUID user = profiles.requireOwnProfile().id;
        String predicate = FROM + "WHERE " + eligibility();
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(SELECT + predicate + " ORDER BY c.created_at DESC,c.id")
                        .setParameter("user", user)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList();
        long total =
                ((Number)
                                em.createNativeQuery("SELECT count(*)" + predicate)
                                        .setParameter("user", user)
                                        .getSingleResult())
                        .longValue();
        return new CatalogModels.Page<>(
                rows.stream().map(ConversationService::view).toList(), page, size, total);
    }

    public View open(UUID bookingId) {
        bookings.get(bookingId);
        Booking booking = em.find(Booking.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        var existing =
                em.createNativeQuery("SELECT id FROM conversation WHERE booking_id=?1")
                        .setParameter(1, bookingId)
                        .getResultList();
        if (!existing.isEmpty()) return get((UUID) existing.getFirst());
        UUID id = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO conversation(id,booking_id) VALUES (?1,?2)")
                .setParameter(1, id)
                .setParameter(2, bookingId)
                .executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO conversation_participant(conversation_id,user_id,participant_role) VALUES (?1,?2,'CUSTOMER')")
                .setParameter(1, id)
                .setParameter(2, booking.customerId)
                .executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO conversation_participant(conversation_id,user_id,participant_role) SELECT ?1,user_id,'PROVIDER' FROM provider_member WHERE provider_id=?2 AND member_role='OWNER' AND user_id<>?3")
                .setParameter(1, id)
                .setParameter(2, booking.providerId)
                .setParameter(3, booking.customerId)
                .executeUpdate();
        audit.record(profiles.requireOwnProfile().id, "CONVERSATION_OPENED", id);
        return get(id);
    }

    private static final String MESSAGE_SELECT =
            "SELECT m.id,m.sequence,m.body,u.display_name,cp.participant_role,m.sender_id,m.created_at FROM conversation_message m JOIN bliss_user u ON u.id=m.sender_id JOIN conversation_participant cp ON cp.conversation_id=m.conversation_id AND cp.user_id=m.sender_id ";

    public Messages messages(UUID id, Long before, Long after, int size) {
        get(id);
        if (before != null && after != null) throw new BadRequestException();
        String cursor =
                before != null
                        ? " AND m.sequence<:cursor"
                        : after != null ? " AND m.sequence>:cursor" : "";
        var query =
                em.createNativeQuery(
                                MESSAGE_SELECT
                                        + "WHERE m.conversation_id=:id"
                                        + cursor
                                        + " ORDER BY m.sequence "
                                        + (after == null ? "DESC" : "ASC"))
                        .setParameter("id", id)
                        .setMaxResults(size + 1);
        if (before != null || after != null)
            query.setParameter("cursor", before != null ? before : after);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        boolean more = rows.size() > size;
        UUID user = profiles.requireOwnProfile().id;
        var items = new ArrayList<>(rows.stream().limit(size).map(r -> message(r, user)).toList());
        if (after == null) Collections.reverse(items);
        return new Messages(items, more);
    }

    public Message send(UUID id, UUID key, String text) {
        get(id);
        UUID user = profiles.requireOwnProfile().id;
        String body = text.strip();
        if (body.isBlank() || body.length() > 2000 || body.indexOf('\0') >= 0)
            throw new BadRequestException();
        // Serializes both duplicate keys and message sequence allocation. A rollback
        // cannot leave a cursor gap that would hide a late-committing message.
        long sequence =
                ((Number)
                                em.createNativeQuery(
                                                "SELECT last_sequence FROM conversation WHERE id=?1 FOR UPDATE")
                                        .setParameter(1, id)
                                        .getSingleResult())
                        .longValue();
        @SuppressWarnings("unchecked")
        List<Object[]> existing =
                em.createNativeQuery(
                                MESSAGE_SELECT
                                        + "WHERE m.conversation_id=?1 AND m.sender_id=?2 AND m.client_key=?3")
                        .setParameter(1, id)
                        .setParameter(2, user)
                        .setParameter(3, key)
                        .getResultList();
        if (!existing.isEmpty()) {
            var prior = message(existing.getFirst(), user);
            if (!prior.body.equals(body)) throw new ClientErrorException(409);
            return prior;
        }
        UUID messageId = UUID.randomUUID();
        em.createNativeQuery(
                        "INSERT INTO conversation_message(id,conversation_id,sender_id,client_key,sequence,body) VALUES (?1,?2,?3,?4,?5,?6)")
                .setParameter(1, messageId)
                .setParameter(2, id)
                .setParameter(3, user)
                .setParameter(4, key)
                .setParameter(5, sequence + 1)
                .setParameter(6, body)
                .executeUpdate();
        em.createNativeQuery("UPDATE conversation SET last_sequence=?2 WHERE id=?1")
                .setParameter(1, id)
                .setParameter(2, sequence + 1)
                .executeUpdate();
        audit.record(user, "MESSAGE_SENT", messageId);
        Object[] row =
                (Object[])
                        em.createNativeQuery(MESSAGE_SELECT + "WHERE m.id=?1")
                                .setParameter(1, messageId)
                                .getSingleResult();
        return message(row, user);
    }

    private static View view(Object[] r) {
        return new View(
                (UUID) r[0],
                (UUID) r[1],
                (String) r[2],
                instant(r[3]),
                ((Number) r[4]).longValue());
    }

    private static Message message(Object[] r, UUID user) {
        return new Message(
                (UUID) r[0],
                ((Number) r[1]).longValue(),
                (String) r[2],
                (String) r[3],
                (String) r[4],
                user.equals(r[5]),
                instant(r[6]));
    }

    private static Instant instant(Object value) {
        return value instanceof Instant i ? i : ((OffsetDateTime) value).toInstant();
    }
}
