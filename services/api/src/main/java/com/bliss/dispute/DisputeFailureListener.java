package com.bliss.dispute;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;

/** Flow deletes failed checkpoints; retain a content-free operational signal for the case. */
@ApplicationScoped
public class DisputeFailureListener implements WorkflowExecutionListener {
    @Inject EntityManager em;

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        if (!"dispute-resolution"
                .equals(event.workflowContext().definition().workflow().getDocument().getName()))
            return;
        String instance = event.workflowContext().instanceData().id();
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                em.createNativeQuery(
                                                "UPDATE dispute SET workflow_failed_at=?1,version=version+1 WHERE workflow_instance_id=?2 AND resolved_at IS NULL AND workflow_failed_at IS NULL")
                                        .setParameter(1, Instant.now())
                                        .setParameter(2, instance)
                                        .executeUpdate());
    }
}
