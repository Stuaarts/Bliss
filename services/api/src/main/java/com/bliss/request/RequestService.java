package com.bliss.request;

import com.bliss.catalog.api.CatalogModels;
import com.bliss.catalog.persistence.ServiceCategory;
import com.bliss.common.Money;
import com.bliss.identity.AuditLog;
import com.bliss.quote.Quote;
import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class RequestService {
    @Inject EntityManager em;
    @Inject ProfileService profiles;
    @Inject AuditLog audit;

    public ServiceRequest own(UUID id, boolean lock) {
        ServiceRequest request =
                lock
                        ? em.find(ServiceRequest.class, id, LockModeType.PESSIMISTIC_WRITE)
                        : em.find(ServiceRequest.class, id);
        if (request == null || !request.customerId.equals(profiles.requireOwnProfile().id))
            throw new NotFoundException();
        return request;
    }

    public ServiceRequest open(UUID id, boolean lock) {
        ServiceRequest request =
                lock
                        ? em.find(ServiceRequest.class, id, LockModeType.PESSIMISTIC_WRITE)
                        : em.find(ServiceRequest.class, id);
        if (request == null
                || request.status != ServiceRequest.Status.OPEN
                || request.customerId.equals(profiles.requireOwnProfile().id))
            throw new NotFoundException();
        return request;
    }

    public RequestModels.View create(RequestModels.Input input) {
        var r = new ServiceRequest();
        r.id = UUID.randomUUID();
        r.customerId = profiles.requireOwnProfile().id;
        r.status = ServiceRequest.Status.OPEN;
        r.createdAt = Instant.now();
        apply(r, input);
        r.persistAndFlush();
        audit.record(r.customerId, "REQUEST_CREATED", r.id);
        return RequestModels.view(r);
    }

    public CatalogModels.Page<RequestModels.View> mine(int page, int size) {
        var q =
                ServiceRequest.<ServiceRequest>find(
                        "customerId=?1 order by createdAt desc,id",
                        profiles.requireOwnProfile().id);
        long total = q.count();
        return new CatalogModels.Page<>(
                q.page(page, size).list().stream().map(RequestModels::view).toList(),
                page,
                size,
                total);
    }

    public CatalogModels.Page<RequestModels.View> opportunities(
            UUID categoryId, int page, int size) {
        String filter = "status=?1 and customerId<>?2";
        var args = new java.util.ArrayList<Object>();
        args.add(ServiceRequest.Status.OPEN);
        args.add(profiles.requireOwnProfile().id);
        if (categoryId != null) {
            filter += " and categoryId=?3";
            args.add(categoryId);
        }
        var q =
                ServiceRequest.<ServiceRequest>find(
                        filter + " order by createdAt desc,id", args.toArray());
        long total = q.count();
        return new CatalogModels.Page<>(
                q.page(page, size).list().stream().map(RequestModels::view).toList(),
                page,
                size,
                total);
    }

    public RequestModels.View update(UUID id, RequestModels.Update input) {
        var r = own(id, true);
        requireEditable(r, input.version());
        if (Quote.count("requestId", id) > 0)
            throw new ClientErrorException("Request has quotes", 409);
        apply(r, input.request());
        r.flush();
        audit.record(r.customerId, "REQUEST_UPDATED", r.id);
        return RequestModels.view(r);
    }

    public void cancel(UUID id, long version) {
        var r = own(id, true);
        if (r.version != version) throw new ClientErrorException("Stale version", 409);
        if (r.status == ServiceRequest.Status.CANCELLED) return;
        requireEditable(r, version);
        r.status = ServiceRequest.Status.CANCELLED;
        Quote.update(
                "status=?1 where requestId=?2 and status=?3",
                Quote.Status.DECLINED,
                id,
                Quote.Status.SUBMITTED);
        r.flush();
        audit.record(r.customerId, "REQUEST_CANCELLED", r.id);
    }

    private static void requireEditable(ServiceRequest r, long version) {
        if (r.status != ServiceRequest.Status.OPEN || r.version != version)
            throw new ClientErrorException("Request changed", 409);
    }

    private static void apply(ServiceRequest r, RequestModels.Input input) {
        if (ServiceCategory.findById(input.categoryId()) == null)
            throw new BadRequestException("Unknown category");
        Money.requireCurrency(input.currency());
        r.title = input.title().strip();
        r.description = input.description().strip();
        r.city = input.city().strip();
        r.categoryId = input.categoryId();
        r.currency = input.currency();
        r.budget = input.budget() == null ? null : Money.amount(input.budget(), input.currency());
    }
}
