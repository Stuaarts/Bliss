package com.bliss.request;

import com.bliss.catalog.api.CatalogModels;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/provider/opportunities")
@RolesAllowed("provider")
@Produces(MediaType.APPLICATION_JSON)
public class OpportunityResource {
    @Inject RequestService service;

    @GET
    public CatalogModels.Page<RequestModels.View> list(
            @QueryParam("categoryId") UUID categoryId,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return service.opportunities(categoryId, page, size);
    }

    @GET
    @Path("/{id}")
    public RequestModels.View get(@PathParam("id") UUID id) {
        return RequestModels.view(service.open(id, false));
    }
}
