package com.bliss.provider;

import com.bliss.identity.AuditLog;
import com.bliss.provider.persistence.ProviderProfile;
import com.bliss.user.ProfileService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
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
    @Inject EntityManager em;

    public record CreateProvider(
            @NotBlank @Size(max = 120) String displayName,
            @NotNull ProviderProfile.Type type,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 120) String city) {}

    public record UpdateProvider(
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 120) String city,
            @NotNull @Min(0) Long version) {}

    public record ManagedProvider(
            UUID id,
            String displayName,
            String description,
            String city,
            long version,
            ProviderProfile.Type type) {}

    @POST
    @Transactional
    public Response create(@NotNull @Valid CreateProvider request) {
        UUID actor = profiles.requireOwnProfile().id;
        var provider = new ProviderProfile();
        provider.id = UUID.randomUUID();
        provider.providerType = request.type();
        provider.displayName = request.displayName().strip();
        provider.description = request.description().strip();
        provider.city = request.city().strip();
        provider.createdAt = Instant.now();
        provider.persistAndFlush();
        em.createNativeQuery(
                        "INSERT INTO provider_member(provider_id,user_id,member_role) VALUES (?1,?2,'OWNER')")
                .setParameter(1, provider.id)
                .setParameter(2, actor)
                .executeUpdate();
        audit.record(actor, "PROVIDER_CREATED", provider.id);
        return Response.created(URI.create("/api/v1/providers/me/" + provider.id))
                .entity(view(provider))
                .build();
    }

    @GET
    @Path("/{providerId}")
    @Transactional
    public ManagedProvider get(@PathParam("providerId") UUID id) {
        access.requireOwner(id);
        return view(ProviderProfile.findById(id));
    }

    @GET
    public List<ProviderAccess.Membership> mine() {
        return access.memberships();
    }

    @PUT
    @Path("/{providerId}")
    @Transactional
    public ManagedProvider update(
            @PathParam("providerId") UUID id, @NotNull @Valid UpdateProvider request) {
        access.requireOwner(id);
        ProviderProfile provider = ProviderProfile.findById(id);
        if (provider.version != request.version())
            throw new ClientErrorException("Stale version", 409);
        provider.displayName = request.displayName().strip();
        provider.description = request.description().strip();
        provider.city = request.city().strip();
        provider.flush();
        audit.record(profiles.requireOwnProfile().id, "PROVIDER_UPDATED", id);
        return view(provider);
    }

    private static ManagedProvider view(ProviderProfile provider) {
        return new ManagedProvider(
                provider.id,
                provider.displayName,
                provider.description,
                provider.city,
                provider.version,
                provider.providerType);
    }
}
