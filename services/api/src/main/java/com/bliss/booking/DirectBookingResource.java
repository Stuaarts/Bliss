package com.bliss.booking;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.UUID;

@Path("/api/v1/offerings/{offeringId}/bookings")
@RolesAllowed("customer")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DirectBookingResource {
    @Inject DirectBookingService service;

    @POST
    public Response book(
            @PathParam("offeringId") UUID offeringId,
            @HeaderParam("Idempotency-Key") @NotNull UUID key,
            @NotNull @Valid DirectBookingService.Input input) {
        var result = service.book(offeringId, key, input);
        return Response.status(result.created() ? 201 : 200)
                .location(URI.create("/api/v1/bookings/" + result.booking().id()))
                .entity(result.booking())
                .build();
    }
}
