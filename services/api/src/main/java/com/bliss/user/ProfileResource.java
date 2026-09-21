package com.bliss.user;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileResource {
    @Inject ProfileService profiles;

    public record UpdateProfile(@NotBlank @Size(max = 120) String displayName) {}

    @POST
    @Path("/bootstrap")
    public ProfileService.Profile bootstrap() {
        return profiles.bootstrap();
    }

    @GET
    public ProfileService.Profile me() {
        return profiles.me();
    }

    @PUT
    public ProfileService.Profile update(@Valid UpdateProfile request) {
        return profiles.rename(request.displayName());
    }
}
