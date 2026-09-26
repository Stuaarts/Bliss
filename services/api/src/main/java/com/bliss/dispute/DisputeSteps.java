package com.bliss.dispute;

import com.bliss.booking.Booking;
import com.bliss.identity.AuditLog;
import com.bliss.payment.PaymentGateway;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.eclipse.microprofile.faulttolerance.Retry;

@ApplicationScoped
public class DisputeSteps {
    @RegisterForReflection
    public record State(UUID disputeId, boolean done) {}

    @Inject EntityManager em;
    @Inject PaymentGateway payments;
    @Inject DisputeService disputes;
    @Inject AuditLog audit;

    @Retry(
            maxRetries = 3,
            delay = 250,
            retryOn = {PaymentGateway.TransientFailure.class, PersistenceException.class})
    public State advance(String instanceId, State input) {
        return QuarkusTransaction.requiringNew().call(() -> tick(instanceId, input.disputeId()));
    }

    private State tick(String instanceId, UUID id) {
        Dispute d = em.find(Dispute.class, id);
        if (d == null) return new State(id, true);
        Booking b = em.find(Booking.class, d.bookingId, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(d, LockModeType.PESSIMISTIC_WRITE);
        if (!instanceId.equals(d.workflowInstanceId)) return new State(id, true);
        em.createNativeQuery("UPDATE dispute_workflow_start SET started=true WHERE dispute_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        if (d.status.resolved() || d.workflowFailedAt != null) return new State(id, true);
        if (b.status != Booking.Status.DISPUTED)
            throw new IllegalStateException("Dispute lost ownership of booking settlement");
        if (d.status == Dispute.Status.COLLECTING_EVIDENCE) {
            long parties =
                    ((Number)
                                    em.createNativeQuery(
                                                    "SELECT count(DISTINCT actor_role) FROM dispute_evidence WHERE dispute_id=?1")
                                            .setParameter(1, id)
                                            .getSingleResult())
                            .longValue();
            if (parties == 2 || !Instant.now().isBefore(d.evidenceDeadline)) {
                d.status = Dispute.Status.UNDER_REVIEW;
                disputes.history(d, null);
                audit.record(null, "DISPUTE_UNDER_REVIEW", id);
            }
        }
        var rows =
                em.createNativeQuery(
                                "SELECT actor_user_id,outcome FROM dispute_decision WHERE dispute_id=?1 AND processed_at IS NULL")
                        .setParameter(1, id)
                        .getResultList();
        if (d.status == Dispute.Status.UNDER_REVIEW && !rows.isEmpty()) {
            Object[] row = (Object[]) rows.getFirst();
            UUID actor = (UUID) row[0];
            String payment =
                    (String)
                            em.createNativeQuery("SELECT status FROM payment WHERE booking_id=?1")
                                    .setParameter(1, b.id)
                                    .getSingleResult();
            Booking.Status next;
            if ("CUSTOMER_FAVORED".equals(row[1])) {
                if ("CAPTURED".equals(payment)) {
                    payments.execute(b, PaymentGateway.Operation.REFUND);
                    next = Booking.Status.REFUNDED;
                } else if ("AUTHORIZED".equals(payment)) {
                    payments.execute(b, PaymentGateway.Operation.VOID);
                    next = Booking.Status.CANCELLED;
                } else throw new IllegalStateException("Invalid disputed payment state");
                d.status = Dispute.Status.RESOLVED_CUSTOMER;
            } else {
                if ("AUTHORIZED".equals(payment))
                    payments.execute(b, PaymentGateway.Operation.CAPTURE);
                else if (!"CAPTURED".equals(payment))
                    throw new IllegalStateException("Invalid disputed payment state");
                next = Booking.Status.COMPLETED;
                d.status = Dispute.Status.RESOLVED_PROVIDER;
            }
            d.resolvedAt = Instant.now();
            disputes.bookingStatus(b, next, actor);
            disputes.history(d, actor);
            em.createNativeQuery("UPDATE dispute_decision SET processed_at=?2 WHERE dispute_id=?1")
                    .setParameter(1, id)
                    .setParameter(2, d.resolvedAt)
                    .executeUpdate();
            audit.record(actor, "DISPUTE_" + d.status.name(), id);
        }
        em.flush();
        return new State(id, d.status.resolved());
    }
}
