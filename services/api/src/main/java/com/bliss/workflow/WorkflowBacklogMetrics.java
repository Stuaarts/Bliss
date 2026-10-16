package com.bliss.workflow;

import io.micrometer.core.instrument.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Periodic aggregate inbox snapshots; Prometheus scrapes never query the database. */
@Startup
@ApplicationScoped
public class WorkflowBacklogMetrics {
    private enum Queue {
        booking_start,
        booking_action,
        dispute_start,
        dispute_decision
    }

    private record Backlog(long pending, double oldestSeconds) {}

    private record Snapshot(Map<Queue, Backlog> queues, long capturedNanos) {}

    private static final String SQL =
            """
            SELECT 'booking_start', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(requested_at))),0),0)
              FROM booking_workflow_start WHERE started=false
            UNION ALL
            SELECT 'booking_action', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(occurred_at))),0),0)
              FROM booking_event WHERE processed_at IS NULL
            UNION ALL
            SELECT 'dispute_start', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(requested_at))),0),0)
              FROM dispute_workflow_start WHERE started=false
            UNION ALL
            SELECT 'dispute_decision', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(admitted_at))),0),0)
              FROM dispute_decision WHERE processed_at IS NULL
            """;

    @Inject EntityManager em;
    @Inject MeterRegistry registry;

    @ConfigProperty(name = "bliss.operations.backlog.enabled", defaultValue = "true")
    boolean enabled;

    private volatile Snapshot snapshot;
    private volatile boolean lastCollectionSuccessful;
    private Counter failures;

    @PostConstruct
    void register() {
        for (Queue queue : Queue.values()) {
            Gauge.builder("bliss.workflow.backlog.pending", this, self -> self.pending(queue))
                    .tag("queue", queue.name())
                    .description(
                            "Pending workflow start intents or unprocessed admitted actions at last successful snapshot")
                    .register(registry);
            Gauge.builder("bliss.workflow.backlog.oldest.seconds", this, self -> self.oldest(queue))
                    .tag("queue", queue.name())
                    .description(
                            "Oldest pending item age at last successful snapshot; zero when empty")
                    .register(registry);
        }
        Gauge.builder(
                        "bliss.workflow.backlog.snapshot.available",
                        this,
                        self -> self.snapshot == null ? 0 : 1)
                .description("One after a successful snapshot exists; does not imply freshness")
                .register(registry);
        Gauge.builder("bliss.workflow.backlog.snapshot.age.seconds", this, self -> self.age())
                .description(
                        "Monotonic seconds since last successful snapshot; NaN before first success")
                .register(registry);
        Gauge.builder(
                        "bliss.workflow.backlog.collection.success",
                        this,
                        self -> self.lastCollectionSuccessful ? 1 : 0)
                .description(
                        "Whether the latest collection attempt succeeded; zero before first success")
                .register(registry);
        failures =
                Counter.builder("bliss.workflow.backlog.collection.failures")
                        .description(
                                "Failed aggregate snapshot attempts since this API process started")
                        .register(registry);
    }

    @Scheduled(
            every = "${bliss.operations.backlog.every:10s}",
            delayed = "3s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void collect() {
        if (!enabled) return;
        try {
            Map<Queue, Backlog> queues =
                    QuarkusTransaction.requiringNew()
                            .call(
                                    () -> {
                                        @SuppressWarnings("unchecked")
                                        List<Object[]> rows =
                                                em.createNativeQuery(SQL)
                                                        .setHint(
                                                                "jakarta.persistence.query.timeout",
                                                                2000)
                                                        .getResultList();
                                        var values = new EnumMap<Queue, Backlog>(Queue.class);
                                        for (Object[] row : rows)
                                            values.put(
                                                    Queue.valueOf((String) row[0]),
                                                    new Backlog(
                                                            ((Number) row[1]).longValue(),
                                                            ((Number) row[2]).doubleValue()));
                                        if (values.size() != Queue.values().length)
                                            throw new IllegalStateException(
                                                    "Incomplete aggregate snapshot");
                                        return Map.copyOf(values);
                                    });
            snapshot = new Snapshot(queues, System.nanoTime());
            lastCollectionSuccessful = true;
        } catch (RuntimeException failure) {
            // Preserve last good values and expose failure/freshness, never report a false zero.
            lastCollectionSuccessful = false;
            failures.increment();
        }
    }

    private double pending(Queue queue) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.queues().get(queue).pending();
    }

    private double oldest(Queue queue) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.queues().get(queue).oldestSeconds();
    }

    private double age() {
        Snapshot current = snapshot;
        return current == null
                ? Double.NaN
                : (System.nanoTime() - current.capturedNanos()) / 1_000_000_000.0;
    }
}
