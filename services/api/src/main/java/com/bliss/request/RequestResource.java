package com.bliss.request;

import com.bliss.catalog.api.CatalogModels;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.UUID;

@Path("/api/v1/service-requests")
@RolesAllowed("customer")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RequestResource {
    @Inject RequestService service;

    @GET
    public CatalogModels.Page<RequestModels.View> mine(
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return service.mine(page, size);
    }

    @POST
    public Response create(@NotNull @Valid RequestModels.Input input) {
        var result = service.create(input);
        return Response.created(URI.create("/api/v1/service-requests/" + result.id()))
                .entity(result)
                .build();
    }

    @GET
    @Path("/{id}")
    public RequestModels.View get(@PathParam("id") UUID id) {
        return RequestModels.view(service.own(id, false));
    }

    @PUT
    @Path("/{id}")
    public RequestModels.View update(
            @PathParam("id") UUID id, @NotNull @Valid RequestModels.Update input) {
        return service.update(id, input);
    }

    @DELETE
    @Path("/{id}")
    public Response cancel(
            @PathParam("id") UUID id, @QueryParam("version") @NotNull @Min(0) Long version) {
        service.cancel(id, version);
        return Response.noContent().build();
    }
}
