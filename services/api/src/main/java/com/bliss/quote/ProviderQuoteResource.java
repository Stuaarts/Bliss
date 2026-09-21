package com.bliss.quote;

import com.bliss.catalog.api.CatalogModels;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/providers/me/{providerId}/quotes")
@RolesAllowed("provider")
@Produces(MediaType.APPLICATION_JSON)
public class ProviderQuoteResource {
    @Inject QuoteService service;

    @GET
    public CatalogModels.Page<QuoteService.View> list(
            @PathParam("providerId") UUID providerId,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return service.forProvider(providerId, page, size);
    }
}
