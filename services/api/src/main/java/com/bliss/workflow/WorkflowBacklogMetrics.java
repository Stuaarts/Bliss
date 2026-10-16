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

    private enum WaitStage {
        provider_decision,
        customer_decision
    }

    private enum DisputeWaitStage {
        party_evidence,
        admin_review
    }

    private record Waiting(long records, double oldestSeconds, long missingHistory) {}

    private record Snapshot(
            Map<Queue, Backlog> queues,
            Backlog failedDisputes,
            Map<WaitStage, Waiting> waiting,
            Map<DisputeWaitStage, Waiting> disputeWaiting,
            long capturedNanos) {}

    private static final String SQL =
            """
            WITH booking_waits AS (
              SELECT b.status, (SELECT max(h.occurred_at) FROM booking_status_history h
                WHERE h.booking_id=b.id AND h.status=b.status) AS entered_at
              FROM booking b WHERE b.status IN ('WAIT_PROVIDER','WAIT_CUSTOMER')
            ), dispute_waits AS (
              SELECT d.status, (SELECT max(h.occurred_at) FROM dispute_history h
                WHERE h.dispute_id=d.id AND h.status=d.status) AS entered_at
              FROM dispute d WHERE d.status IN ('COLLECTING_EVIDENCE','UNDER_REVIEW')
            )
            SELECT 'booking_start', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(requested_at))),0),0), 0
              FROM booking_workflow_start WHERE started=false
            UNION ALL
            SELECT 'booking_action', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(occurred_at))),0),0), 0
              FROM booking_event WHERE processed_at IS NULL
            UNION ALL
            SELECT 'dispute_start', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(requested_at))),0),0), 0
              FROM dispute_workflow_start WHERE started=false
            UNION ALL
            SELECT 'dispute_decision', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(admitted_at))),0),0), 0
              FROM dispute_decision WHERE processed_at IS NULL
            UNION ALL
            SELECT 'dispute_failure', count(*), coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(workflow_failed_at))),0),0), 0
              FROM dispute WHERE workflow_failed_at IS NOT NULL AND resolved_at IS NULL
            UNION ALL
            SELECT 'provider_decision', count(*),
              CASE WHEN count(*) FILTER (WHERE entered_at IS NULL)>0 THEN NULL
                ELSE coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(entered_at))),0),0) END,
              count(*) FILTER (WHERE entered_at IS NULL)
              FROM booking_waits WHERE status='WAIT_PROVIDER'
            UNION ALL
            SELECT 'customer_decision', count(*),
              CASE WHEN count(*) FILTER (WHERE entered_at IS NULL)>0 THEN NULL
                ELSE coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(entered_at))),0),0) END,
              count(*) FILTER (WHERE entered_at IS NULL)
              FROM booking_waits WHERE status='WAIT_CUSTOMER'
            UNION ALL
            SELECT 'party_evidence', count(*),
              CASE WHEN count(*) FILTER (WHERE entered_at IS NULL)>0 THEN NULL
                ELSE coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(entered_at))),0),0) END,
              count(*) FILTER (WHERE entered_at IS NULL)
              FROM dispute_waits WHERE status='COLLECTING_EVIDENCE'
            UNION ALL
            SELECT 'admin_review', count(*),
              CASE WHEN count(*) FILTER (WHERE entered_at IS NULL)>0 THEN NULL
                ELSE coalesce(greatest(extract(epoch FROM (CURRENT_TIMESTAMP-min(entered_at))),0),0) END,
              count(*) FILTER (WHERE entered_at IS NULL)
              FROM dispute_waits WHERE status='UNDER_REVIEW'
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
        for (WaitStage stage : WaitStage.values()) {
            Gauge.builder(
                            "bliss.workflow.booking.waiting.bookings",
                            this,
                            self -> self.waitingCount(stage))
                    .tag("stage", stage.name())
                    .description(
                            "Bookings in the corresponding saved decision-wait state at last successful snapshot")
                    .register(registry);
            Gauge.builder(
                            "bliss.workflow.booking.waiting.oldest.seconds",
                            this,
                            self -> self.waitingOldest(stage))
                    .tag("stage", stage.name())
                    .description(
                            "Oldest current decision-wait transition age; NaN when history is missing")
                    .register(registry);
            Gauge.builder(
                            "bliss.workflow.booking.waiting.history.missing",
                            this,
                            self -> self.waitingMissing(stage))
                    .tag("stage", stage.name())
                    .description(
                            "Decision-wait bookings without matching transition history at last successful snapshot")
                    .register(registry);
        }
        Gauge.builder("bliss.workflow.backlog.snapshot.age.seconds", this, self -> self.age())
                .description(
                        "Monotonic seconds since last successful snapshot; NaN before first success")
                .register(registry);
        for (DisputeWaitStage stage : DisputeWaitStage.values()) {
            Gauge.builder(
                            "bliss.workflow.dispute.waiting.cases",
                            this,
                            self -> self.disputeWaitingCount(stage))
                    .tag("stage", stage.name())
                    .description(
                            "Disputes in the corresponding saved wait state at last successful snapshot")
                    .register(registry);
            Gauge.builder(
                            "bliss.workflow.dispute.waiting.oldest.seconds",
                            this,
                            self -> self.disputeWaitingOldest(stage))
                    .tag("stage", stage.name())
                    .description(
                            "Oldest current dispute-wait transition age; NaN when history is missing")
                    .register(registry);
            Gauge.builder(
                            "bliss.workflow.dispute.waiting.history.missing",
                            this,
                            self -> self.disputeWaitingMissing(stage))
                    .tag("stage", stage.name())
                    .description(
                            "Dispute-wait cases without matching transition history at last successful snapshot")
                    .register(registry);
        }
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
        Gauge.builder("bliss.workflow.dispute.failed.cases", this, self -> self.failedCases())
                .description(
                        "Unresolved disputes with a durable workflow failure marker at last successful snapshot")
                .register(registry);
        Gauge.builder(
                        "bliss.workflow.dispute.failed.oldest.seconds",
                        this,
                        self -> self.failedOldest())
                .description(
                        "Age of oldest unresolved dispute workflow failure at last successful snapshot; zero when empty")
                .register(registry);
    }

    @Scheduled(
            every = "${bliss.operations.backlog.every:10s}",
            delayed = "3s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void collect() {
        if (!enabled) return;
        try {
            Snapshot collected =
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
                                        var waiting =
                                                new EnumMap<WaitStage, Waiting>(WaitStage.class);
                                        var disputeWaiting =
                                                new EnumMap<DisputeWaitStage, Waiting>(
                                                        DisputeWaitStage.class);
                                        Backlog failedDisputes = null;
                                        for (Object[] row : rows) {
                                            if (Set.of("party_evidence", "admin_review")
                                                    .contains(row[0])) {
                                                disputeWaiting.put(
                                                        DisputeWaitStage.valueOf((String) row[0]),
                                                        new Waiting(
                                                                ((Number) row[1]).longValue(),
                                                                row[2] == null
                                                                        ? Double.NaN
                                                                        : ((Number) row[2])
                                                                                .doubleValue(),
                                                                ((Number) row[3]).longValue()));
                                                continue;
                                            }
                                            if (Set.of("provider_decision", "customer_decision")
                                                    .contains(row[0])) {
                                                waiting.put(
                                                        WaitStage.valueOf((String) row[0]),
                                                        new Waiting(
                                                                ((Number) row[1]).longValue(),
                                                                row[2] == null
                                                                        ? Double.NaN
                                                                        : ((Number) row[2])
                                                                                .doubleValue(),
                                                                ((Number) row[3]).longValue()));
                                                continue;
                                            }
                                            var aggregate =
                                                    new Backlog(
                                                            ((Number) row[1]).longValue(),
                                                            ((Number) row[2]).doubleValue());
                                            if ("dispute_failure".equals(row[0])) {
                                                failedDisputes = aggregate;
                                                continue;
                                            }
                                            values.put(Queue.valueOf((String) row[0]), aggregate);
                                        }
                                        if (values.size() != Queue.values().length
                                                || failedDisputes == null
                                                || waiting.size() != WaitStage.values().length
                                                || disputeWaiting.size()
                                                        != DisputeWaitStage.values().length)
                                            throw new IllegalStateException(
                                                    "Incomplete aggregate snapshot");
                                        return new Snapshot(
                                                Map.copyOf(values),
                                                failedDisputes,
                                                Map.copyOf(waiting),
                                                Map.copyOf(disputeWaiting),
                                                0);
                                    });
            snapshot =
                    new Snapshot(
                            collected.queues(),
                            collected.failedDisputes(),
                            collected.waiting(),
                            collected.disputeWaiting(),
                            System.nanoTime());
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

    private double failedCases() {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.failedDisputes().pending();
    }

    private double failedOldest() {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.failedDisputes().oldestSeconds();
    }

    private double waitingCount(WaitStage stage) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.waiting().get(stage).records();
    }

    private double waitingOldest(WaitStage stage) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.waiting().get(stage).oldestSeconds();
    }

    private double waitingMissing(WaitStage stage) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.waiting().get(stage).missingHistory();
    }

    private double disputeWaitingCount(DisputeWaitStage stage) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.disputeWaiting().get(stage).records();
    }

    private double disputeWaitingOldest(DisputeWaitStage stage) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.disputeWaiting().get(stage).oldestSeconds();
    }

    private double disputeWaitingMissing(DisputeWaitStage stage) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.disputeWaiting().get(stage).missingHistory();
    }
}
