package com.bliss.workflow;

import com.bliss.booking.*;
import com.bliss.identity.AuditLog;
import com.bliss.payment.PaymentGateway;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;

@ApplicationScoped
public class BookingWorkflowSteps {
    @RegisterForReflection
    public record State(UUID bookingId, boolean done) {}

    @Inject EntityManager em;
    @Inject PaymentGateway payments;
    @Inject AuditLog audit;

    @ConfigProperty(name = "bliss.booking.provider-decision-seconds", defaultValue = "86400")
    long decisionSeconds;

    @Retry(
            maxRetries = 3,
            delay = 250,
            retryOn = {PaymentGateway.TransientFailure.class, PersistenceException.class})
    public State advance(String instanceId, State input) {
        return QuarkusTransaction.requiringNew().call(() -> tick(instanceId, input.bookingId()));
    }

    private State tick(String instanceId, UUID bookingId) {
        Booking b = em.find(Booking.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (b == null || !instanceId.equals(b.workflowInstanceId))
            return new State(bookingId, true);
        em.createNativeQuery("UPDATE booking_workflow_start SET started=true WHERE booking_id=?1")
                .setParameter(1, b.id)
                .executeUpdate();
        // DisputeFlow owns settlement once a dispute is opened. Never resume
        // the ordinary confirmation path while that case is outstanding.
        if (b.status.terminal() || b.status == Booking.Status.DISPUTED)
            return new State(b.id, true);
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery(
                                "SELECT id,actor_user_id,event_type,scheduled_at,occurred_at FROM booking_event WHERE booking_id=?1 AND processed_at IS NULL")
                        .setParameter(1, b.id)
                        .getResultList();
        if (!rows.isEmpty()) {
            Object[] row = rows.getFirst();
            var type = BookingEvents.Type.valueOf((String) row[2]);
            if (!BookingEvents.allowed(b, type, instant(row[4])))
                throw new IllegalStateException("Stored event conflicts with booking state");
            Booking.Status next =
                    switch (type) {
                        case PROVIDER_ACCEPT -> {
                            b.scheduledAt = instant(row[3]);
                            yield Booking.Status.SCHEDULED;
                        }
                        case PROVIDER_REJECT, CANCEL -> {
                            payments.execute(b, PaymentGateway.Operation.VOID);
                            yield Booking.Status.CANCELLED;
                        }
                        case START -> Booking.Status.IN_PROGRESS;
                        case MARK_COMPLETE -> Booking.Status.WAIT_CUSTOMER;
                        case CUSTOMER_CONFIRM -> {
                            payments.execute(b, PaymentGateway.Operation.CAPTURE);
                            yield Booking.Status.COMPLETED;
                        }
                    };
            transition(b, next, (UUID) row[1]);
            em.createNativeQuery("UPDATE booking_event SET processed_at=?2 WHERE id=?1")
                    .setParameter(1, row[0])
                    .setParameter(2, Instant.now())
                    .executeUpdate();
        } else if (b.status == Booking.Status.CREATED) {
            payments.execute(b, PaymentGateway.Operation.AUTHORIZE);
            b.decisionDeadline = Instant.now().plusSeconds(decisionSeconds);
            transition(b, Booking.Status.WAIT_PROVIDER, null);
        } else if (b.status == Booking.Status.WAIT_PROVIDER
                && !Instant.now().isBefore(b.decisionDeadline)) {
            payments.execute(b, PaymentGateway.Operation.VOID);
            transition(b, Booking.Status.EXPIRED, null);
        }
        b.flush();
        return new State(b.id, b.status.terminal());
    }

    static Instant instant(Object value) {
        if (value == null) return null;
        return value instanceof Instant i ? i : ((OffsetDateTime) value).toInstant();
    }

    private void transition(Booking b, Booking.Status next, UUID actor) {
        b.status = next;
        em.createNativeQuery(
                        "INSERT INTO booking_status_history(id,booking_id,status,actor_user_id,occurred_at) VALUES (?1,?2,?3,?4,?5)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, b.id)
                .setParameter(3, next.name())
                .setParameter(4, actor)
                .setParameter(5, Instant.now())
                .executeUpdate();
        audit.record(actor, "BOOKING_" + next.name(), b.id);
    }
}
