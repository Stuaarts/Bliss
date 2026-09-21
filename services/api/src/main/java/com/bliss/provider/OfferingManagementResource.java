package com.bliss.provider;

import com.bliss.catalog.api.CatalogModels;
import com.bliss.catalog.persistence.ServiceCategory;
import com.bliss.catalog.persistence.ServiceOffering;
import com.bliss.identity.AuditLog;
import com.bliss.provider.persistence.ProviderProfile;
import com.bliss.user.ProfileService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Path("/api/v1/providers/me/{providerId}/offerings")
@RolesAllowed("provider")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class OfferingManagementResource {
    @Inject ProviderAccess access;
    @Inject ProfileService profiles;
    @Inject AuditLog audit;

    public record OfferingInput(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotNull UUID categoryId,
            @NotNull @Pattern(regexp = "^(0|[1-9][0-9]{0,9})(\\.[0-9]{1,2})?$") String price,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {}

    public record UpdateOffering(
            @NotNull @Valid OfferingInput offering, @NotNull @Min(0) Long version) {}

    public record ManagedOffering(
            UUID id,
            UUID providerId,
            UUID categoryId,
            String title,
            String description,
            String price,
            String currency,
            boolean active,
            long version) {}

    @GET
    public CatalogModels.Page<ManagedOffering> list(
            @PathParam("providerId") UUID providerId,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(1000000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        access.requireOwner(providerId);
        var query =
                ServiceOffering.<ServiceOffering>find(
                        "provider.id = ?1 order by createdAt desc, id", providerId);
        long total = query.count();
        return new CatalogModels.Page<>(
                query.page(page, size).list().stream()
                        .map(OfferingManagementResource::view)
                        .toList(),
                page,
                size,
                total);
    }

    @POST
    public Response create(
            @PathParam("providerId") UUID providerId, @NotNull @Valid OfferingInput input) {
        access.requireOwner(providerId);
        var offering = new ServiceOffering();
        offering.id = UUID.randomUUID();
        offering.provider = ProviderProfile.findById(providerId);
        offering.createdAt = Instant.now();
        offering.active = true;
        apply(offering, input);
        offering.persistAndFlush();
        audit.record(profiles.requireOwnProfile().id, "OFFERING_CREATED", offering.id);
        return Response.created(
                        URI.create(
                                "/api/v1/providers/me/" + providerId + "/offerings/" + offering.id))
                .entity(view(offering))
                .build();
    }

    @GET
    @Path("/{offeringId}")
    public ManagedOffering get(
            @PathParam("providerId") UUID providerId, @PathParam("offeringId") UUID offeringId) {
        return view(owned(providerId, offeringId));
    }

    @PUT
    @Path("/{offeringId}")
    public ManagedOffering update(
            @PathParam("providerId") UUID providerId,
            @PathParam("offeringId") UUID offeringId,
            @NotNull @Valid UpdateOffering request) {
        var offering = owned(providerId, offeringId);
        checkVersion(offering, request.version());
        if (!offering.active) throw new ClientErrorException("Archived offering", 409);
        apply(offering, request.offering());
        offering.flush();
        audit.record(profiles.requireOwnProfile().id, "OFFERING_UPDATED", offering.id);
        return view(offering);
    }

    @DELETE
    @Path("/{offeringId}")
    public Response archive(
            @PathParam("providerId") UUID providerId,
            @PathParam("offeringId") UUID offeringId,
            @QueryParam("version") @NotNull @Min(0) Long version) {
        var offering = owned(providerId, offeringId);
        checkVersion(offering, version);
        if (offering.active) {
            offering.active = false;
            offering.flush();
            audit.record(profiles.requireOwnProfile().id, "OFFERING_ARCHIVED", offering.id);
        }
        return Response.noContent().build();
    }

    private ServiceOffering owned(UUID providerId, UUID offeringId) {
        access.requireOwner(providerId);
        ServiceOffering offering =
                ServiceOffering.find("id = ?1 and provider.id = ?2", offeringId, providerId)
                        .firstResult();
        if (offering == null) throw new NotFoundException();
        return offering;
    }

    private static void checkVersion(ServiceOffering offering, long version) {
        if (offering.version != version) throw new ClientErrorException("Stale version", 409);
    }

    private static void apply(ServiceOffering offering, OfferingInput input) {
        ServiceCategory category = ServiceCategory.findById(input.categoryId());
        if (category == null) throw new BadRequestException("Unknown category");
        try {
            // The current schema and clients support currencies with two minor-unit digits.
            if (Currency.getInstance(input.currency()).getDefaultFractionDigits() != 2)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported currency");
        }
        offering.category = category;
        offering.title = input.title().strip();
        offering.description = input.description().strip();
        offering.price = new BigDecimal(input.price()).setScale(2);
        offering.currency = input.currency();
    }

    private static ManagedOffering view(ServiceOffering o) {
        return new ManagedOffering(
                o.id,
                o.provider.id,
                o.category.id,
                o.title,
                o.description,
                o.price.toPlainString(),
                o.currency,
                o.active,
                o.version);
    }
}
