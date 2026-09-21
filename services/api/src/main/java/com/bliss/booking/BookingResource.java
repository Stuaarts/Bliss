package com.bliss.booking;

import com.bliss.catalog.api.CatalogModels;
import com.bliss.identity.CurrentUser;
import com.bliss.provider.ProviderAccess;
import com.bliss.user.ProfileService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;

@Path("/api/v1/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class BookingResource {
    @Inject CurrentUser current;
    @Inject ProfileService profiles;
    @Inject ProviderAccess access;

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
            Instant createdAt) {}

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
                b.createdAt);
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
