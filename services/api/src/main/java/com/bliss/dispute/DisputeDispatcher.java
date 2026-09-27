package com.bliss.dispute;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DisputeDispatcher {
    private static final Logger LOG = Logger.getLogger(DisputeDispatcher.class);
    @Inject EntityManager em;
    @Inject DisputeFlow flow;

    @ConfigProperty(name = "bliss.workflow.dispatch-enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(
            every = "2s",
            delayed = "3s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void dispatch() {
        if (!enabled) return;
        List<UUID> ids =
                QuarkusTransaction.requiringNew()
                        .call(
                                () -> {
                                    @SuppressWarnings("unchecked")
                                    List<UUID> rows =
                                            em.createNativeQuery(
                                                            "SELECT dispute_id FROM dispute_workflow_start WHERE started=false ORDER BY requested_at LIMIT 50")
                                                    .getResultList();
                                    return rows;
                                });
        for (UUID id : ids) startDispute(id);
    }

    public void startDispute(UUID id) {
        WorkflowInstance instance = QuarkusTransaction.requiringNew().call(() -> claim(id));
        if (instance != null)
            instance.start()
                    .whenComplete(
                            (result, error) -> {
                                if (error != null)
                                    LOG.errorf(
                                            error,
                                            "Dispute workflow failed disputeId=%s instanceId=%s",
                                            id,
                                            instance.id());
                            });
    }

    private WorkflowInstance claim(UUID id) {
        Dispute d = em.find(Dispute.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (d == null || d.status.resolved() || d.workflowFailedAt != null) return null;
        var jobs =
                em.createNativeQuery(
                                "SELECT started,claimed_at FROM dispute_workflow_start WHERE dispute_id=?1")
                        .setParameter(1, id)
                        .getResultList();
        if (jobs.isEmpty()) return null;
        Object[] job = (Object[]) jobs.getFirst();
        if (Boolean.TRUE.equals(job[0])) return null;
        if (d.workflowInstanceId != null) {
            long checkpoints =
                    ((Number)
                                    em.createNativeQuery(
                                                    "SELECT count(*) FROM workflow_instance_entity WHERE application_id=?1 AND instance_id=?2")
                                            .setParameter(1, flow.definition().application().id())
                                            .setParameter(2, d.workflowInstanceId)
                                            .getSingleResult())
                            .longValue();
            if (checkpoints > 0) {
                em.createNativeQuery(
                                "UPDATE dispute_workflow_start SET started=true WHERE dispute_id=?1")
                        .setParameter(1, id)
                        .executeUpdate();
                return null;
            }
            Instant claimed = DisputeService.instant(job[1]);
            if (claimed != null && claimed.isAfter(Instant.now().minusSeconds(30))) return null;
        }
        var instance = flow.instance(new DisputeSteps.State(id, false));
        d.workflowInstanceId = instance.id();
        em.createNativeQuery("UPDATE dispute_workflow_start SET claimed_at=?2 WHERE dispute_id=?1")
                .setParameter(1, id)
                .setParameter(2, Instant.now())
                .executeUpdate();
        em.flush();
        return instance;
    }
}
