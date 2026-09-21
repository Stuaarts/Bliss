package com.bliss.quote;

import com.bliss.catalog.api.CatalogModels;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.UUID;

@Path("/api/v1/service-requests/{requestId}/quotes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RequestQuoteResource {
    @Inject QuoteService service;

    @POST
    @RolesAllowed("provider")
    public Response submit(
            @PathParam("requestId") UUID requestId, @NotNull @Valid QuoteService.Input input) {
        var q = service.submit(requestId, input);
        return Response.created(URI.create("/api/v1/quotes/" + q.id())).entity(q).build();
    }

    @GET
    @RolesAllowed("customer")
    public CatalogModels.Page<QuoteService.View> list(
            @PathParam("requestId") UUID requestId,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return service.forRequest(requestId, page, size);
    }
}
