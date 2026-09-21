package com.bliss.quote;

import com.bliss.booking.Booking;
import com.bliss.booking.BookingResource;
import com.bliss.catalog.api.CatalogModels;
import com.bliss.common.Money;
import com.bliss.identity.AuditLog;
import com.bliss.identity.CurrentUser;
import com.bliss.provider.ProviderAccess;
import com.bliss.provider.persistence.ProviderProfile;
import com.bliss.request.RequestService;
import com.bliss.request.ServiceRequest;
import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Transactional
public class QuoteService {
    @Inject RequestService requests;
    @Inject ProfileService profiles;
    @Inject ProviderAccess access;
    @Inject AuditLog audit;
    @Inject EntityManager em;
    @Inject CurrentUser current;

    @ConfigProperty(name = "bliss.quotes.max-validity-seconds", defaultValue = "2592000")
    long maxValiditySeconds;

    public record Input(
            @NotNull UUID providerId,
            @NotBlank @Size(max = 2000) String description,
            @NotNull String price,
            @NotNull String currency,
            @NotNull @Future Instant expiresAt) {}

    public record View(
            UUID id,
            UUID requestId,
            UUID providerId,
            String providerName,
            String description,
            String price,
            String currency,
            String status,
            Instant expiresAt) {}

    public View submit(UUID requestId, Input input) {
        access.requireOwner(input.providerId());
        var r = requests.open(requestId, true);
        if (!r.currency.equals(input.currency()))
            throw new BadRequestException("Currency mismatch");
        if (!input.expiresAt().isAfter(Instant.now())
                || input.expiresAt().isAfter(Instant.now().plusSeconds(maxValiditySeconds)))
            throw new BadRequestException("Quote expiry outside local policy");
        if (Quote.count("requestId=?1 and providerId=?2", requestId, input.providerId()) > 0)
            throw new ClientErrorException("Provider already quoted this request", 409);
        var q = new Quote();
        q.id = UUID.randomUUID();
        q.requestId = requestId;
        q.providerId = input.providerId();
        q.description = input.description().strip();
        q.price = Money.amount(input.price(), input.currency());
        q.currency = input.currency();
        q.status = Quote.Status.SUBMITTED;
        q.createdAt = Instant.now();
        q.expiresAt = input.expiresAt();
        q.persistAndFlush();
        audit.record(profiles.requireOwnProfile().id, "QUOTE_SUBMITTED", q.id);
        return view(q);
    }

    public CatalogModels.Page<View> forRequest(UUID requestId, int page, int size) {
        requests.own(requestId, false);
        return page(Quote.find("requestId=?1 order by createdAt,id", requestId), page, size);
    }

    public View get(UUID quoteId) {
        Quote q = Quote.findById(quoteId);
        if (q == null) throw new NotFoundException();
        ServiceRequest request = ServiceRequest.findById(q.requestId);
        if (current.capabilities().contains("customer")
                && request.customerId.equals(profiles.requireOwnProfile().id)) return view(q);
        if (!current.capabilities().contains("provider")) throw new NotFoundException();
        access.requireOwner(q.providerId);
        return view(q);
    }

    public CatalogModels.Page<View> forProvider(UUID providerId, int page, int size) {
        access.requireOwner(providerId);
        return page(Quote.find("providerId=?1 order by createdAt desc,id", providerId), page, size);
    }

    private CatalogModels.Page<View> page(
            io.quarkus.hibernate.orm.panache.PanacheQuery<Quote> query, int page, int size) {
        long total = query.count();
        return new CatalogModels.Page<>(
                query.page(page, size).list().stream().map(QuoteService::view).toList(),
                page,
                size,
                total);
    }

    private ServiceRequest lockCustomerRequest(UUID quoteId) {
        // Read only the FK before locking: never cache a stale Quote entity while waiting.
        UUID requestId =
                em.createQuery("select q.requestId from Quote q where q.id=:id", UUID.class)
                        .setParameter("id", quoteId)
                        .getResultStream()
                        .findFirst()
                        .orElseThrow(NotFoundException::new);
        return requests.own(requestId, true);
    }

    public BookingResource.View accept(UUID quoteId) {
        var r = lockCustomerRequest(quoteId);
        Quote q = Quote.findById(quoteId);
        if (q.status == Quote.Status.ACCEPTED) {
            Booking existing = Booking.find("quoteId", quoteId).firstResult();
            if (existing == null) throw new IllegalStateException("Accepted quote lacks booking");
            return BookingResource.view(existing);
        }
        if (r.status != ServiceRequest.Status.OPEN
                || q.status != Quote.Status.SUBMITTED
                || !q.expiresAt.isAfter(Instant.now()))
            throw new ClientErrorException("Quote cannot be accepted", 409);
        q.status = Quote.Status.ACCEPTED;
        r.status = ServiceRequest.Status.ACCEPTED;
        q.flush();
        r.flush();
        Quote.update(
                "status=?1 where requestId=?2 and status=?3",
                Quote.Status.DECLINED,
                r.id,
                Quote.Status.SUBMITTED);
        var b = new Booking();
        b.id = UUID.randomUUID();
        b.requestId = r.id;
        b.quoteId = q.id;
        b.customerId = r.customerId;
        b.providerId = q.providerId;
        b.title = r.title;
        b.city = r.city;
        b.price = q.price;
        b.currency = q.currency;
        b.status = Booking.Status.CREATED;
        b.createdAt = Instant.now();
        b.persistAndFlush();
        em.createNativeQuery(
                        "INSERT INTO booking_status_history(id,booking_id,status,actor_user_id,occurred_at) VALUES (?1,?2,'CREATED',?3,?4)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, b.id)
                .setParameter(3, r.customerId)
                .setParameter(4, b.createdAt)
                .executeUpdate();
        audit.record(r.customerId, "QUOTE_ACCEPTED", q.id);
        audit.record(r.customerId, "BOOKING_CREATED", b.id);
        return BookingResource.view(b);
    }

    public View decline(UUID quoteId) {
        var r = lockCustomerRequest(quoteId);
        Quote q = Quote.findById(quoteId);
        if (q.status == Quote.Status.DECLINED) return view(q);
        if (r.status != ServiceRequest.Status.OPEN || q.status != Quote.Status.SUBMITTED)
            throw new ClientErrorException("Quote cannot be declined", 409);
        q.status = Quote.Status.DECLINED;
        q.flush();
        audit.record(r.customerId, "QUOTE_DECLINED", q.id);
        return view(q);
    }

    public static View view(Quote q) {
        ProviderProfile provider = ProviderProfile.findById(q.providerId);
        return new View(
                q.id,
                q.requestId,
                q.providerId,
                provider.displayName,
                q.description,
                q.price.toPlainString(),
                q.currency,
                q.status.name(),
                q.expiresAt);
    }
}
