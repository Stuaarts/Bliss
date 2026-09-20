package com.bliss.user;

import com.bliss.identity.AuditLog;
import com.bliss.identity.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class ProfileService {
    @Inject CurrentUser current;
    @Inject EntityManager em;
    @Inject AuditLog audit;

    public record Profile(UUID id, String displayName, List<String> capabilities) {}

    public Profile bootstrap() {
        // PostgreSQL unique subject + ON CONFLICT makes concurrent first logins safe.
        em.createNativeQuery(
                        "INSERT INTO bliss_user (id, oidc_subject, display_name) VALUES (?1, ?2, ?3) ON CONFLICT (oidc_subject) DO NOTHING")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, current.subject())
                .setParameter(3, current.initialName())
                .executeUpdate();
        UserProfile profile = requireOwnProfile();
        for (String role : current.capabilities()) {
            em.createNativeQuery(
                            "INSERT INTO user_role (user_id, role) VALUES (?1, ?2) ON CONFLICT DO NOTHING")
                    .setParameter(1, profile.id)
                    .setParameter(2, role.toUpperCase(java.util.Locale.ROOT))
                    .executeUpdate();
        }
        return view(profile);
    }

    public UserProfile requireOwnProfile() {
        return UserProfile.<UserProfile>find("subject", current.subject())
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Profile not initialized"));
    }

    public Profile me() {
        return view(requireOwnProfile());
    }

    public Profile rename(String name) {
        UserProfile profile = requireOwnProfile();
        profile.displayName = name.strip();
        audit.record(profile.id, "PROFILE_UPDATED", profile.id);
        return view(profile);
    }

    private Profile view(UserProfile profile) {
        // Permissions always come from the validated token, never historical user_role rows.
        return new Profile(profile.id, profile.displayName, current.capabilities());
    }
}
