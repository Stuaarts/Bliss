package com.bliss.booking;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Instant;
import java.util.UUID;

@Path("/api/v1/bookings/{id}")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class BookingActionResource {
    @Inject BookingEvents events;

    public record Schedule(@NotNull Instant scheduledAt) {}

    private Response submit(UUID id, UUID key, BookingEvents.Type type, Instant schedule) {
        return Response.accepted(events.submit(id, key, type, schedule)).build();
    }

    @POST
    @Path("/provider-accept")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response accept(
            @PathParam("id") UUID id,
            @HeaderParam("Idempotency-Key") @NotNull UUID key,
            @NotNull @Valid Schedule body) {
        return submit(id, key, BookingEvents.Type.PROVIDER_ACCEPT, body.scheduledAt());
    }

    @POST
    @Path("/provider-reject")
    public Response reject(
            @PathParam("id") UUID id, @HeaderParam("Idempotency-Key") @NotNull UUID key) {
        return submit(id, key, BookingEvents.Type.PROVIDER_REJECT, null);
    }

    @POST
    @Path("/start")
    public Response start(
            @PathParam("id") UUID id, @HeaderParam("Idempotency-Key") @NotNull UUID key) {
        return submit(id, key, BookingEvents.Type.START, null);
    }

    @POST
    @Path("/mark-complete")
    public Response complete(
            @PathParam("id") UUID id, @HeaderParam("Idempotency-Key") @NotNull UUID key) {
        return submit(id, key, BookingEvents.Type.MARK_COMPLETE, null);
    }

    @POST
    @Path("/confirm-completion")
    public Response confirm(
            @PathParam("id") UUID id, @HeaderParam("Idempotency-Key") @NotNull UUID key) {
        return submit(id, key, BookingEvents.Type.CUSTOMER_CONFIRM, null);
    }

    @POST
    @Path("/cancel")
    public Response cancel(
            @PathParam("id") UUID id, @HeaderParam("Idempotency-Key") @NotNull UUID key) {
        return submit(id, key, BookingEvents.Type.CANCEL, null);
    }
}
