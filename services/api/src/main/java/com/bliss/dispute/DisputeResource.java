package com.bliss.dispute;

import com.bliss.catalog.api.CatalogModels;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.UUID;

@Path("/api/v1/disputes")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class DisputeResource {
    @Inject DisputeService disputes;

    @GET
    @Path("/{id}/execution")
    public com.bliss.booking.BookingResource.Execution execution(@PathParam("id") UUID id) {
        return disputes.execution(id);
    }

    public record EvidenceInput(@NotBlank @Size(max = 2000) String body) {}

    public record DecisionInput(
            @NotNull DisputeService.Outcome outcome,
            @NotBlank @Size(max = 2000) String rationale) {}

    @GET
    public CatalogModels.Page<DisputeService.Summary> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return disputes.list(page, size);
    }

    @GET
    @Path("/{id}")
    public DisputeService.View get(@PathParam("id") UUID id) {
        return disputes.get(id);
    }

    @GET
    @Path("/{id}/evidence")
    public CatalogModels.Page<DisputeService.Evidence> evidence(
            @PathParam("id") UUID id,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return disputes.evidence(id, page, size);
    }

    @GET
    @Path("/{id}/history")
    public CatalogModels.Page<DisputeService.History> history(
            @PathParam("id") UUID id,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return disputes.history(id, page, size);
    }

    @POST
    @Path("/{id}/evidence")
    @Consumes(MediaType.APPLICATION_JSON)
    public DisputeService.Evidence evidence(
            @PathParam("id") UUID id,
            @HeaderParam("Idempotency-Key") @NotNull UUID key,
            @NotNull @Valid EvidenceInput input) {
        return disputes.evidence(id, key, input.body());
    }

    @POST
    @Path("/{id}/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response decide(
            @PathParam("id") UUID id,
            @HeaderParam("Idempotency-Key") @NotNull UUID key,
            @NotNull @Valid DecisionInput input) {
        return Response.accepted(disputes.decide(id, key, input.outcome(), input.rationale()))
                .build();
    }
}
