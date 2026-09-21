package com.bliss.quote;

import com.bliss.booking.BookingResource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/quotes/{quoteId}")
@RolesAllowed("customer")
@Produces(MediaType.APPLICATION_JSON)
public class QuoteResource {
    @Inject QuoteService service;

    @GET
    @RolesAllowed({"customer", "provider"})
    public QuoteService.View get(@PathParam("quoteId") UUID id) {
        return service.get(id);
    }

    @POST
    @Path("/accept")
    public BookingResource.View accept(@PathParam("quoteId") UUID id) {
        return service.accept(id);
    }

    @POST
    @Path("/decline")
    public QuoteService.View decline(@PathParam("quoteId") UUID id) {
        return service.decline(id);
    }
}
