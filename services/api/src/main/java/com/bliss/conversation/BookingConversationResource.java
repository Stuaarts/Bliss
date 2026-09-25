package com.bliss.conversation;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/bookings/{id}/conversation")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class BookingConversationResource {
    @Inject ConversationService conversations;

    @POST
    public ConversationService.View open(@PathParam("id") UUID id) {
        return conversations.open(id);
    }
}
