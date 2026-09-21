package com.bliss.workflow;

import io.quarkiverse.flow.persistence.jpa.JpaInstanceOperations;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.persistence.PersistenceWorkflowInfo;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.stream.Stream;

/**
 * Flow 1.2.0.CR1 writes every loop iteration but reads one checkpoint per task pointer. Keep only
 * the latest iteration in the same engine transaction, matching that read contract. Remove this
 * adapter only after WorkflowRestartIT passes against an upstream fix.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class LoopSafeJpaOperations extends JpaInstanceOperations {
    @Inject EntityManager entityManager;

    @Override
    public Stream<PersistenceWorkflowInfo> scanAll(
            String applicationId, WorkflowDefinition definition) {
        // Close the upstream scan transaction before restoration starts asynchronous tasks.
        // Otherwise context propagation shares the live transaction with those worker threads.
        try (var rows = super.scanAll(applicationId, definition)) {
            return rows.toList().stream();
        }
    }

    @Override
    public void writeCompletedTask(WorkflowContextData workflow, TaskContextData task) {
        super.writeCompletedTask(workflow, task);
        entityManager.flush();
        entityManager
                .createNativeQuery(
                        "DELETE FROM task_info_entity WHERE application_id=?1 AND workflow_instance_id=?2 AND json_pointer=?3 AND iteration<?4")
                .setParameter(1, workflow.definition().application().id())
                .setParameter(2, workflow.instanceData().id())
                .setParameter(3, task.position().jsonPointer())
                .setParameter(4, task.iteration())
                .executeUpdate();
    }
}
