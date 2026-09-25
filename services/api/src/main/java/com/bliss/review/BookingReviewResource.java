package com.bliss.review;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.math.BigDecimal;
import java.util.UUID;

@Path("/api/v1/bookings/{id}/reviews")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class BookingReviewResource {
    @Inject ReviewService reviews;

    public record Input(
            @NotNull @DecimalMin("1") @DecimalMax("5") @Digits(integer = 1, fraction = 0)
                    BigDecimal rating,
            @Size(max = 2000) String comment) {}

    @GET
    public ReviewService.State state(@PathParam("id") UUID id) {
        return reviews.state(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("id") UUID id, @NotNull @Valid Input input) {
        var result = reviews.create(id, input.rating().intValueExact(), input.comment());
        return Response.status(result.created() ? 201 : 200).entity(result.review()).build();
    }
}
