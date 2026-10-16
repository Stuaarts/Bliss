package com.bliss.workflow;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class WorkflowBacklogMetricsTest {
    @Test
    void disabledCollectionExposesUnknownValuesWithoutTouchingTheDatabase() {
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new WorkflowBacklogMetrics();
            metrics.registry = registry;
            metrics.enabled = false;
            metrics.register();
            metrics.collect(); // No EntityManager: disabled collection must not attempt SQL.
            assertEquals(
                    0, registry.get("bliss.workflow.backlog.snapshot.available").gauge().value());
            assertEquals(
                    0, registry.get("bliss.workflow.backlog.collection.success").gauge().value());
            assertEquals(
                    0,
                    registry.get("bliss.workflow.backlog.collection.failures").counter().count());
            assertTrue(
                    Double.isNaN(
                            registry.get("bliss.workflow.backlog.snapshot.age.seconds")
                                    .gauge()
                                    .value()));
            for (String queue :
                    new String[] {
                        "booking_start", "booking_action", "dispute_start", "dispute_decision"
                    }) {
                assertTrue(
                        Double.isNaN(
                                registry.get("bliss.workflow.backlog.pending")
                                        .tag("queue", queue)
                                        .gauge()
                                        .value()));
                assertTrue(
                        Double.isNaN(
                                registry.get("bliss.workflow.backlog.oldest.seconds")
                                        .tag("queue", queue)
                                        .gauge()
                                        .value()));
            }
            assertEquals(12, registry.getMeters().size());
        } finally {
            registry.close();
        }
    }
}
