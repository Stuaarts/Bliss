package com.bliss.booking;

import com.bliss.catalog.api.CatalogModels;
import com.bliss.identity.CurrentUser;
import com.bliss.provider.ProviderAccess;
import com.bliss.user.ProfileService;
import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceEntity;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Path("/api/v1/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class BookingResource {
    @Inject CurrentUser current;
    @Inject ProfileService profiles;
    @Inject ProviderAccess access;
    @Inject EntityManager em;

    public record View(
            UUID id,
            UUID requestId,
            UUID quoteId,
            UUID providerId,
            String title,
            String city,
            String price,
            String currency,
            String status,
            Instant createdAt,
            String workflowInstanceId,
            Instant scheduledAt,
            Instant decisionDeadline,
            UUID offeringId,
            Long offeringVersion,
            String description) {}

    public static View view(Booking b) {
        return new View(
                b.id,
                b.requestId,
                b.quoteId,
                b.providerId,
                b.title,
                b.city,
                b.price.toPlainString(),
                b.currency,
                b.status.name(),
                b.createdAt,
                b.workflowInstanceId,
                b.scheduledAt,
                b.decisionDeadline,
                b.offeringId,
                b.offeringVersion,
                b.description);
    }

    @GET
    @Path("/{id}")
    public View get(@PathParam("id") UUID id) {
        Booking b = Booking.findById(id);
        if (b == null) throw new NotFoundException();
        if (current.capabilities().contains("customer")
                && b.customerId.equals(profiles.requireOwnProfile().id)) return view(b);
        if (!current.capabilities().contains("provider")) throw new NotFoundException();
        access.requireOwner(b.providerId);
        return view(b);
    }

    public record History(String status, Instant occurredAt, boolean system) {}

    public record Payment(
            String status,
            String amount,
            String currency,
            boolean simulated,
            List<String> operations) {}

    public record Execution(String instanceId, String status, boolean checkpointPresent) {}

    @GET
    @Path("/{id}/history")
    public CatalogModels.Page<History> history(
            @PathParam("id") UUID id,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        get(id);
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(
                                "SELECT status,occurred_at,actor_user_id FROM booking_status_history WHERE booking_id=?1 ORDER BY occurred_at,id")
                        .setParameter(1, id)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList();
        long total =
                ((Number)
                                em.createNativeQuery(
                                                "SELECT count(*) FROM booking_status_history WHERE booking_id=?1")
                                        .setParameter(1, id)
                                        .getSingleResult())
                        .longValue();
        return new CatalogModels.Page<>(
                rows.stream()
                        .map(r -> new History((String) r[0], instant(r[1]), r[2] == null))
                        .toList(),
                page,
                size,
                total);
    }

    @GET
    @Path("/{id}/payment")
    public Payment payment(@PathParam("id") UUID id) {
        View booking = get(id);
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(
                                "SELECT status,amount,currency FROM payment WHERE booking_id=?1")
                        .setParameter(1, id)
                        .getResultList();
        if (rows.isEmpty())
            return new Payment(
                    "NOT_AUTHORIZED", booking.price(), booking.currency(), true, List.of());
        @SuppressWarnings("unchecked")
        List<String> operations =
                em.createNativeQuery(
                                "SELECT operation FROM payment_transaction WHERE booking_id=?1 ORDER BY occurred_at,id")
                        .setParameter(1, id)
                        .getResultList();
        Object[] row = rows.getFirst();
        return new Payment(
                (String) row[0],
                ((BigDecimal) row[1]).toPlainString(),
                (String) row[2],
                true,
                operations);
    }

    @GET
    @Path("/{id}/execution")
    public Execution execution(@PathParam("id") UUID id) {
        View booking = get(id);
        if (booking.workflowInstanceId() == null) return new Execution(null, "PENDING", false);
        var rows =
                em.createQuery(
                                "from WorkflowInstanceEntity where instanceId=:id",
                                WorkflowInstanceEntity.class)
                        .setParameter("id", booking.workflowInstanceId())
                        .getResultList();
        Booking record = em.find(Booking.class, id); // get(id) above enforces ownership first.
        if (record.workflowFailedAt != null
                && !record.status.terminal()
                && record.status != Booking.Status.DISPUTED)
            return new Execution(booking.workflowInstanceId(), "ERROR", !rows.isEmpty());
        if (rows.isEmpty())
            return new Execution(
                    booking.workflowInstanceId(),
                    Booking.Status.valueOf(booking.status()).terminal() ? "FINISHED" : "PENDING",
                    false);
        var status = rows.getFirst().getStatus();
        return new Execution(
                booking.workflowInstanceId(), status == null ? "RUNNING" : status.name(), true);
    }

    private static Instant instant(Object value) {
        return value instanceof Instant i ? i : ((OffsetDateTime) value).toInstant();
    }

    @GET
    public CatalogModels.Page<View> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        var params = new HashMap<String, Object>();
        var clauses = new ArrayList<String>();
        if (current.capabilities().contains("customer")) {
            clauses.add("customerId=:user");
            params.put("user", profiles.requireOwnProfile().id);
        }
        if (current.capabilities().contains("provider")) {
            var ids =
                    access.memberships().stream()
                            .filter(m -> m.role().equals("OWNER"))
                            .map(ProviderAccess.Membership::providerId)
                            .toList();
            if (!ids.isEmpty()) {
                clauses.add("providerId in :providers");
                params.put("providers", ids);
            }
        }
        if (clauses.isEmpty()) return new CatalogModels.Page<>(List.of(), page, size, 0);
        var q =
                Booking.<Booking>find(
                        "(" + String.join(" or ", clauses) + ") order by createdAt desc,id",
                        params);
        long total = q.count();
        return new CatalogModels.Page<>(
                q.page(page, size).list().stream().map(BookingResource::view).toList(),
                page,
                size,
                total);
    }
}
