package com.bliss.provider;

import com.bliss.identity.AuditLog;
import com.bliss.provider.persistence.ProviderProfile;
import com.bliss.user.ProfileService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/providers/me")
@RolesAllowed("provider")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProviderManagementResource {
    @Inject ProviderAccess access;
    @Inject ProfileService profiles;
    @Inject AuditLog audit;

    public record UpdateProvider(
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 120) String city,
            @NotNull @Min(0) Long version) {}

    public record ManagedProvider(
            UUID id, String displayName, String description, String city, long version) {}

    @GET
    public List<ProviderAccess.Membership> mine() {
        return access.memberships();
    }

    @PUT
    @Path("/{providerId}")
    @Transactional
    public ManagedProvider update(@PathParam("providerId") UUID id, @Valid UpdateProvider request) {
        access.requireOwner(id);
        ProviderProfile provider = ProviderProfile.findById(id);
        if (provider.version != request.version())
            throw new ClientErrorException("Stale version", 409);
        provider.displayName = request.displayName().strip();
        provider.description = request.description().strip();
        provider.city = request.city().strip();
        provider.flush();
        audit.record(profiles.requireOwnProfile().id, "PROVIDER_UPDATED", id);
        return new ManagedProvider(
                provider.id,
                provider.displayName,
                provider.description,
                provider.city,
                provider.version);
    }
}
