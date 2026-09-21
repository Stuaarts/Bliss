package com.bliss.identity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Transactional audit metadata only: never tokens, credentials, or submitted profile text. */
@ApplicationScoped
public class AuditLog {
    @Inject EntityManager em;

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(UUID actor, String action, UUID resource) {
        em.createNativeQuery(
                        "INSERT INTO audit_event (id, actor_user_id, action, resource_id) VALUES (?1, ?2, ?3, ?4)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, actor)
                .setParameter(3, action)
                .setParameter(4, resource)
                .executeUpdate();
    }
}
