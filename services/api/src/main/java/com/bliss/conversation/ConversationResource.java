package com.bliss.conversation;

import com.bliss.catalog.api.CatalogModels;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/conversations")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ConversationResource {
    @Inject ConversationService conversations;

    public record Input(@NotBlank @Size(max = 2000) String body) {}

    @GET
    public CatalogModels.Page<ConversationService.View> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return conversations.list(page, size);
    }

    @GET
    @Path("/{id}")
    public ConversationService.View get(@PathParam("id") UUID id) {
        return conversations.get(id);
    }

    @GET
    @Path("/{id}/messages")
    public ConversationService.Messages messages(
            @PathParam("id") UUID id,
            @QueryParam("before") @Min(1) Long before,
            @QueryParam("after") @Min(0) Long after,
            @QueryParam("size") @DefaultValue("50") @Min(1) @Max(100) int size) {
        return conversations.messages(id, before, after, size);
    }

    @POST
    @Path("/{id}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    public ConversationService.Message send(
            @PathParam("id") UUID id,
            @HeaderParam("Idempotency-Key") @NotNull UUID key,
            @NotNull @Valid Input input) {
        return conversations.send(id, key, input.body());
    }
}
