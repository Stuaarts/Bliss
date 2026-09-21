package com.bliss.workflow;

import com.bliss.booking.Booking;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BookingWorkflowDispatcher {
    private static final Logger LOG = Logger.getLogger(BookingWorkflowDispatcher.class);
    @Inject EntityManager em;
    @Inject BookingLifecycleFlow flow;

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
                                                            "SELECT booking_id FROM booking_workflow_start WHERE started=false ORDER BY requested_at LIMIT 50")
                                                    .getResultList();
                                    return rows;
                                });
        for (UUID id : ids) startBooking(id);
    }

    public void startBooking(UUID id) {
        WorkflowInstance instance = QuarkusTransaction.requiringNew().call(() -> claim(id));
        if (instance != null)
            instance.start()
                    .whenComplete(
                            (result, error) -> {
                                if (error != null)
                                    LOG.errorf(
                                            error,
                                            "Booking workflow failed bookingId=%s instanceId=%s",
                                            id,
                                            instance.id());
                            });
    }

    private WorkflowInstance claim(UUID id) {
        Booking b = em.find(Booking.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (b == null || b.status.terminal()) return null;
        @SuppressWarnings("unchecked")
        List<Object[]> jobs =
                em.createNativeQuery(
                                "SELECT started,claimed_at FROM booking_workflow_start WHERE booking_id=?1")
                        .setParameter(1, id)
                        .getResultList();
        if (jobs.isEmpty() || Boolean.TRUE.equals(jobs.getFirst()[0])) return null;
        if (b.workflowInstanceId != null) {
            Number checkpoints =
                    (Number)
                            em.createNativeQuery(
                                            "SELECT count(*) FROM workflow_instance_entity WHERE application_id=?1 AND instance_id=?2")
                                    .setParameter(1, flow.definition().application().id())
                                    .setParameter(2, b.workflowInstanceId)
                                    .getSingleResult();
            if (checkpoints.longValue() > 0) {
                em.createNativeQuery(
                                "UPDATE booking_workflow_start SET started=true WHERE booking_id=?1")
                        .setParameter(1, id)
                        .executeUpdate();
                return null; // The engine restores its existing instance; never create another.
            }
            Instant claimed = BookingWorkflowSteps.instant(jobs.getFirst()[1]);
            if (claimed != null && claimed.isAfter(Instant.now().minusSeconds(30))) return null;
        }
        var instance = flow.instance(new BookingWorkflowSteps.State(id, false));
        b.workflowInstanceId = instance.id();
        em.createNativeQuery("UPDATE booking_workflow_start SET claimed_at=?2 WHERE booking_id=?1")
                .setParameter(1, id)
                .setParameter(2, Instant.now())
                .executeUpdate();
        b.flush();
        return instance; // Start only after the booking/queue transaction commits.
    }
}
