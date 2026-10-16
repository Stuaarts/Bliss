package com.bliss.workflow;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;

/** Preserve failure after engine checkpoint cleanup; never restart settlement automatically. */
@ApplicationScoped
public class BookingFailureListener implements WorkflowExecutionListener {
    @Inject EntityManager em;

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        if (!"booking-lifecycle"
                .equals(event.workflowContext().definition().workflow().getDocument().getName()))
            return;
        String instance = event.workflowContext().instanceData().id();
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "UPDATE booking SET workflow_failed_at=?1,version=version+1 WHERE workflow_instance_id=?2 AND workflow_failed_at IS NULL AND status NOT IN ('COMPLETED','CANCELLED','EXPIRED','REFUNDED','DISPUTED')")
                                        .setParameter(1, Instant.now())
                                        .setParameter(2, instance)
                                        .executeUpdate());
    }
}
