package com.bliss.dispute;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.UUID;

@Path("/api/v1/bookings/{id}/disputes")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class BookingDisputeResource {
    @Inject DisputeService disputes;

    public record Input(@NotBlank @Size(max = 2000) String reason) {}

    @GET
    public DisputeService.Eligibility eligibility(@PathParam("id") UUID id) {
        return disputes.eligibility(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response open(@PathParam("id") UUID id, @NotNull @Valid Input input) {
        var result = disputes.open(id, input.reason());
        return Response.status(result.created() ? 201 : 200).entity(result.dispute()).build();
    }
}
