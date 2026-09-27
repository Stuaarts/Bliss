package com.bliss.dispute;

import com.bliss.booking.*;
import com.bliss.catalog.api.CatalogModels;
import com.bliss.identity.*;
import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Transactional
public class DisputeService {
    @Inject EntityManager em;
    @Inject CurrentUser current;
    @Inject ProfileService profiles;
    @Inject BookingResource bookings;
    @Inject AuditLog audit;

    @ConfigProperty(name = "bliss.dispute.filing-seconds", defaultValue = "604800")
    long filingSeconds;

    @ConfigProperty(name = "bliss.dispute.evidence-seconds", defaultValue = "86400")
    long evidenceSeconds;

    public enum Outcome {
        CUSTOMER_FAVORED,
        PROVIDER_FAVORED
    }

    public record Decision(
            String outcome, String rationale, Instant admittedAt, boolean processed) {}

    public record View(
            UUID id,
            UUID bookingId,
            String title,
            String amount,
            String currency,
            String status,
            String reason,
            Instant createdAt,
            Instant evidenceDeadline,
            Instant resolvedAt,
            String workflowInstanceId,
            boolean canAddEvidence,
            boolean canDecide,
            Decision decision) {}

    public record Summary(UUID id, String title, String status, Instant createdAt) {}

    public record Eligibility(boolean eligible, Instant filingDeadline, View dispute) {}

    public record Evidence(UUID id, String role, String body, Instant createdAt, boolean mine) {}

    public record History(String status, Instant occurredAt, boolean system) {}

    public record Created(View dispute, boolean created) {}

    private boolean owner(Booking b, UUID user) {
        return ((Number)
                                em.createNativeQuery(
                                                "SELECT count(*) FROM provider_member WHERE provider_id=?1 AND user_id=?2 AND member_role='OWNER'")
                                        .setParameter(1, b.providerId)
                                        .setParameter(2, user)
                                        .getSingleResult())
                        .longValue()
                > 0;
    }

    private String party(Booking b, UUID user) {
        if (current.capabilities().contains("customer") && b.customerId.equals(user))
            return "CUSTOMER";
        if (current.capabilities().contains("provider") && owner(b, user)) return "PROVIDER";
        return null;
    }

    private boolean independentAdmin(Booking b, UUID user) {
        if (!current.capabilities().contains("admin") || b.customerId.equals(user)) return false;
        return ((Number)
                                em.createNativeQuery(
                                                "SELECT count(*) FROM provider_member WHERE provider_id=?1 AND user_id=?2")
                                        .setParameter(1, b.providerId)
                                        .setParameter(2, user)
                                        .getSingleResult())
                        .longValue()
                == 0;
    }

    private Dispute accessible(UUID id) {
        Dispute d = em.find(Dispute.class, id);
        if (d == null) throw new NotFoundException();
        Booking b = em.find(Booking.class, d.bookingId);
        if (!current.capabilities().contains("admin")
                && party(b, profiles.requireOwnProfile().id) == null) throw new NotFoundException();
        return d;
    }

    private Dispute lock(UUID id) {
        Dispute d = accessible(id);
        em.find(Booking.class, d.bookingId, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(d, LockModeType.PESSIMISTIC_WRITE);
        return d;
    }

    public View get(UUID id) {
        return view(accessible(id));
    }

    public BookingResource.Execution execution(UUID id) {
        Dispute d = accessible(id);
        if (d.workflowFailedAt != null)
            return new BookingResource.Execution(d.workflowInstanceId, "FAULTED", false);
        if (d.workflowInstanceId == null)
            return new BookingResource.Execution(null, "PENDING", false);
        var rows =
                em.createQuery(
                                "from WorkflowInstanceEntity where instanceId=:id",
                                io.quarkiverse.flow.persistence.jpa.WorkflowInstanceEntity.class)
                        .setParameter("id", d.workflowInstanceId)
                        .getResultList();
        if (rows.isEmpty())
            return new BookingResource.Execution(
                    d.workflowInstanceId, d.status.resolved() ? "FINISHED" : "PENDING", false);
        var status = rows.getFirst().getStatus();
        return new BookingResource.Execution(
                d.workflowInstanceId, status == null ? "RUNNING" : status.name(), true);
    }

    private View view(Dispute d) {
        Booking b = em.find(Booking.class, d.bookingId);
        UUID user = profiles.requireOwnProfile().id;
        var rows =
                em.createNativeQuery(
                                "SELECT outcome,rationale,admitted_at,processed_at FROM dispute_decision WHERE dispute_id=?1")
                        .setParameter(1, d.id)
                        .getResultList();
        Object[] decision = rows.isEmpty() ? null : (Object[]) rows.getFirst();
        return new View(
                d.id,
                b.id,
                b.title,
                b.price.toPlainString(),
                b.currency,
                d.status.name(),
                d.reason,
                d.createdAt,
                d.evidenceDeadline,
                d.resolvedAt,
                d.workflowInstanceId,
                d.workflowFailedAt == null
                        && d.status == Dispute.Status.COLLECTING_EVIDENCE
                        && Instant.now().isBefore(d.evidenceDeadline)
                        && party(b, user) != null,
                d.workflowFailedAt == null
                        && d.status == Dispute.Status.UNDER_REVIEW
                        && decision == null
                        && independentAdmin(b, user),
                decision == null
                        ? null
                        : new Decision(
                                (String) decision[0],
                                (String) decision[1],
                                instant(decision[2]),
                                decision[3] != null));
    }

    private Instant filingDeadline(Booking b) {
        Object value =
                em.createNativeQuery(
                                "SELECT max(occurred_at) FROM booking_status_history WHERE booking_id=?1 AND status='WAIT_CUSTOMER'")
                        .setParameter(1, b.id)
                        .getSingleResult();
        return value == null ? null : instant(value).plusSeconds(filingSeconds);
    }

    public Eligibility eligibility(UUID bookingId) {
        bookings.get(bookingId);
        Booking b = em.find(Booking.class, bookingId);
        var existing =
                em.createQuery("from Dispute where bookingId=?1", Dispute.class)
                        .setParameter(1, bookingId)
                        .getResultList();
        Instant deadline = filingDeadline(b);
        return new Eligibility(
                existing.isEmpty()
                        && "CUSTOMER".equals(party(b, profiles.requireOwnProfile().id))
                        && (b.status == Booking.Status.WAIT_CUSTOMER
                                || b.status == Booking.Status.COMPLETED)
                        && deadline != null
                        && Instant.now().isBefore(deadline),
                deadline,
                existing.isEmpty() ? null : view(existing.getFirst()));
    }

    public Created open(UUID bookingId, String text) {
        UUID user = profiles.requireOwnProfile().id;
        Booking b = em.find(Booking.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (b == null || !"CUSTOMER".equals(party(b, user))) throw new NotFoundException();
        String reason = text(text);
        var existing =
                em.createQuery("from Dispute where bookingId=?1", Dispute.class)
                        .setParameter(1, bookingId)
                        .getResultList();
        if (!existing.isEmpty()) {
            var prior = existing.getFirst();
            if (!prior.reason.equals(reason)) throw new ClientErrorException(409);
            return new Created(view(prior), false);
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS), deadline = filingDeadline(b);
        if ((b.status != Booking.Status.WAIT_CUSTOMER && b.status != Booking.Status.COMPLETED)
                || deadline == null
                || !now.isBefore(deadline)) throw new ClientErrorException(409);
        if (((Number)
                                em.createNativeQuery(
                                                "SELECT count(*) FROM booking_event WHERE booking_id=?1 AND processed_at IS NULL")
                                        .setParameter(1, b.id)
                                        .getSingleResult())
                        .longValue()
                > 0) throw new ClientErrorException("Another booking action is pending", 409);
        Dispute d = new Dispute();
        d.id = UUID.randomUUID();
        d.bookingId = b.id;
        d.openedBy = user;
        d.reason = reason;
        d.status = Dispute.Status.COLLECTING_EVIDENCE;
        d.createdAt = now;
        d.evidenceDeadline = now.plusSeconds(evidenceSeconds);
        em.persist(d);
        em.flush();
        em.createNativeQuery("INSERT INTO dispute_workflow_start(dispute_id) VALUES (?1)")
                .setParameter(1, d.id)
                .executeUpdate();
        addEvidence(d, user, "CUSTOMER", UUID.randomUUID(), reason);
        history(d, user);
        bookingStatus(b, Booking.Status.DISPUTED, user);
        audit.record(user, "DISPUTE_OPENED", d.id);
        return new Created(view(d), true);
    }

    public Evidence evidence(UUID id, UUID key, String body) {
        Dispute d = lock(id);
        UUID user = profiles.requireOwnProfile().id;
        String role = party(em.find(Booking.class, d.bookingId), user);
        if (role == null) throw new ForbiddenException();
        String content = text(body);
        var rows =
                em.createNativeQuery(
                                "SELECT id,actor_role,body,created_at,actor_user_id FROM dispute_evidence WHERE dispute_id=?1 AND actor_user_id=?2 AND client_key=?3")
                        .setParameter(1, id)
                        .setParameter(2, user)
                        .setParameter(3, key)
                        .getResultList();
        if (!rows.isEmpty()) {
            Evidence prior = evidenceView((Object[]) rows.getFirst(), user);
            if (!prior.body.equals(content)) throw new ClientErrorException(409);
            return prior;
        }
        if (d.workflowFailedAt != null
                || d.status != Dispute.Status.COLLECTING_EVIDENCE
                || !Instant.now().isBefore(d.evidenceDeadline)) throw new ClientErrorException(409);
        return addEvidence(d, user, role, key, content);
    }

    private Evidence addEvidence(Dispute d, UUID user, String role, UUID key, String body) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        em.createNativeQuery(
                        "INSERT INTO dispute_evidence(id,dispute_id,actor_user_id,actor_role,client_key,body,created_at) VALUES (?1,?2,?3,?4,?5,?6,?7)")
                .setParameter(1, id)
                .setParameter(2, d.id)
                .setParameter(3, user)
                .setParameter(4, role)
                .setParameter(5, key)
                .setParameter(6, body)
                .setParameter(7, now)
                .executeUpdate();
        audit.record(user, "DISPUTE_EVIDENCE_ADDED", id);
        return new Evidence(id, role, body, now, true);
    }

    public Decision decide(UUID id, UUID key, Outcome outcome, String rationale) {
        Dispute d = lock(id);
        UUID user = profiles.requireOwnProfile().id;
        if (!independentAdmin(em.find(Booking.class, d.bookingId), user))
            throw new ForbiddenException();
        String reason = text(rationale);
        var rows =
                em.createNativeQuery(
                                "SELECT actor_user_id,client_key,outcome,rationale,admitted_at,processed_at FROM dispute_decision WHERE dispute_id=?1")
                        .setParameter(1, id)
                        .getResultList();
        if (!rows.isEmpty()) {
            Object[] r = (Object[]) rows.getFirst();
            if (!user.equals(r[0])
                    || !key.equals(r[1])
                    || !outcome.name().equals(r[2])
                    || !reason.equals(r[3])) throw new ClientErrorException(409);
            return new Decision(outcome.name(), reason, instant(r[4]), r[5] != null);
        }
        if (d.workflowFailedAt != null || d.status != Dispute.Status.UNDER_REVIEW)
            throw new ClientErrorException(409);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        em.createNativeQuery(
                        "INSERT INTO dispute_decision(dispute_id,actor_user_id,client_key,outcome,rationale,admitted_at) VALUES (?1,?2,?3,?4,?5,?6)")
                .setParameter(1, id)
                .setParameter(2, user)
                .setParameter(3, key)
                .setParameter(4, outcome.name())
                .setParameter(5, reason)
                .setParameter(6, now)
                .executeUpdate();
        audit.record(user, "DISPUTE_DECISION_ADMITTED", id);
        return new Decision(outcome.name(), reason, now, false);
    }

    public CatalogModels.Page<Summary> list(int page, int size) {
        UUID user = profiles.requireOwnProfile().id;
        String predicate =
                current.capabilities().contains("admin")
                        ? "true"
                        : "(("
                                + current.capabilities().contains("customer")
                                + " AND b.customer_id=:user) OR ("
                                + current.capabilities().contains("provider")
                                + " AND EXISTS (SELECT 1 FROM provider_member pm WHERE pm.provider_id=b.provider_id AND pm.user_id=:user AND pm.member_role='OWNER')))";
        String from = " FROM dispute d JOIN booking b ON b.id=d.booking_id WHERE " + predicate;
        var query =
                em.createNativeQuery(
                        "SELECT d.id,b.title,d.status,d.created_at"
                                + from
                                + " ORDER BY d.created_at DESC,d.id");
        var count = em.createNativeQuery("SELECT count(*)" + from);
        if (!current.capabilities().contains("admin")) {
            query.setParameter("user", user);
            count.setParameter("user", user);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.setFirstResult(page * size).setMaxResults(size).getResultList();
        return new CatalogModels.Page<>(
                rows.stream()
                        .map(
                                r ->
                                        new Summary(
                                                (UUID) r[0],
                                                (String) r[1],
                                                (String) r[2],
                                                instant(r[3])))
                        .toList(),
                page,
                size,
                ((Number) count.getSingleResult()).longValue());
    }

    public CatalogModels.Page<Evidence> evidence(UUID id, int page, int size) {
        accessible(id);
        UUID user = profiles.requireOwnProfile().id;
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(
                                "SELECT id,actor_role,body,created_at,actor_user_id FROM dispute_evidence WHERE dispute_id=?1 ORDER BY created_at,id")
                        .setParameter(1, id)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList();
        long count =
                ((Number)
                                em.createNativeQuery(
                                                "SELECT count(*) FROM dispute_evidence WHERE dispute_id=?1")
                                        .setParameter(1, id)
                                        .getSingleResult())
                        .longValue();
        return new CatalogModels.Page<>(
                rows.stream().map(r -> evidenceView(r, user)).toList(), page, size, count);
    }

    public CatalogModels.Page<History> history(UUID id, int page, int size) {
        accessible(id);
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(
                                "SELECT status,occurred_at,actor_user_id FROM dispute_history WHERE dispute_id=?1 ORDER BY occurred_at,id")
                        .setParameter(1, id)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList();
        long count =
                ((Number)
                                em.createNativeQuery(
                                                "SELECT count(*) FROM dispute_history WHERE dispute_id=?1")
                                        .setParameter(1, id)
                                        .getSingleResult())
                        .longValue();
        return new CatalogModels.Page<>(
                rows.stream()
                        .map(r -> new History((String) r[0], instant(r[1]), r[2] == null))
                        .toList(),
                page,
                size,
                count);
    }

    public void history(Dispute d, UUID actor) {
        em.createNativeQuery(
                        "INSERT INTO dispute_history(id,dispute_id,status,actor_user_id) VALUES (?1,?2,?3,?4)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, d.id)
                .setParameter(3, d.status.name())
                .setParameter(4, actor)
                .executeUpdate();
    }

    public void bookingStatus(Booking b, Booking.Status status, UUID actor) {
        b.status = status;
        em.createNativeQuery(
                        "INSERT INTO booking_status_history(id,booking_id,status,actor_user_id,occurred_at) VALUES (?1,?2,?3,?4,?5)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, b.id)
                .setParameter(3, status.name())
                .setParameter(4, actor)
                .setParameter(5, Instant.now())
                .executeUpdate();
        audit.record(actor, "BOOKING_" + status.name(), b.id);
    }

    private static Evidence evidenceView(Object[] r, UUID user) {
        return new Evidence(
                (UUID) r[0], (String) r[1], (String) r[2], instant(r[3]), user.equals(r[4]));
    }

    public static Instant instant(Object value) {
        return value == null
                ? null
                : value instanceof Instant i ? i : ((OffsetDateTime) value).toInstant();
    }

    static String text(String value) {
        if (value == null || value.isBlank() || value.length() > 2000 || value.indexOf('\0') >= 0)
            throw new BadRequestException();
        return value.strip();
    }
}
