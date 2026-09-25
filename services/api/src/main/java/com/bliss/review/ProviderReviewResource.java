package com.bliss.review;

import jakarta.inject.Inject;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/providers/{id}/reviews")
@Produces(MediaType.APPLICATION_JSON)
public class ProviderReviewResource {
    @Inject ReviewService reviews;

    @GET
    public ReviewService.Page list(
            @PathParam("id") UUID id,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return reviews.list(id, page, size);
    }
}
