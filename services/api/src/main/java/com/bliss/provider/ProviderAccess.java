package com.bliss.provider;

import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class ProviderAccess {
    @Inject ProfileService profiles;
    @Inject EntityManager em;

    public record Membership(UUID providerId, String name, String role) {}

    public List<Membership> memberships() {
        UUID userId = profiles.requireOwnProfile().id;
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(
                                "SELECT p.id, p.display_name, m.member_role FROM provider_profile p JOIN provider_member m ON m.provider_id=p.id WHERE m.user_id=?1 ORDER BY p.display_name, p.id")
                        .setParameter(1, userId)
                        .getResultList();
        return rows.stream()
                .map(row -> new Membership((UUID) row[0], (String) row[1], (String) row[2]))
                .toList();
    }

    public void requireOwner(UUID providerId) {
        UUID userId = profiles.requireOwnProfile().id;
        Number count =
                (Number)
                        em.createNativeQuery(
                                        "SELECT count(*) FROM provider_member WHERE provider_id=?1 AND user_id=?2 AND member_role='OWNER'")
                                .setParameter(1, providerId)
                                .setParameter(2, userId)
                                .getSingleResult();
        if (count.longValue() != 1) throw new NotFoundException();
    }
}
