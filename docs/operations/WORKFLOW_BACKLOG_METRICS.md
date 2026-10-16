# Aggregate workflow inbox metrics (#15)

`WorkflowBacklogMetrics` periodically reads four durable inbox aggregates in one
SQL statement/transaction. Prometheus scrapes read only its in-memory snapshot;
they never query the database. Production `/q/metrics` retains the independent
`operator` OIDC role requirement in [management access](MANAGEMENT_ACCESS.md).
Local/dev/test management policy is unchanged. No exporter, hosted collector,
dashboard, notification destination or alert threshold is configured here.

## Metric contract

| Prometheus sample | Meaning |
| --- | --- |
| `bliss_workflow_backlog_pending{queue="..."}` | Pending rows in the most recent successful snapshot |
| `bliss_workflow_backlog_oldest_seconds{queue="..."}` | Age of the oldest pending row at that snapshot's database transaction time; zero for an empty queue |
| `bliss_workflow_backlog_snapshot_available` | One after any successful snapshot exists; zero before then. It does not imply freshness |
| `bliss_workflow_backlog_snapshot_age_seconds` | Monotonic elapsed seconds since successful collection completed; NaN before first success |
| `bliss_workflow_backlog_collection_success` | One if the most recent collection attempt succeeded; zero initially or after failure |
| `bliss_workflow_backlog_collection_failures_total` | Failed collection attempts since this API process started |

There are exactly four code-defined `queue` values and twelve custom time series:

| Queue | Included rows | Age timestamp |
| --- | --- | --- |
| `booking_start` | `booking_workflow_start.started=false`, including claimed but not yet acknowledged intents | `requested_at` |
| `booking_action` | `booking_event.processed_at IS NULL` | `occurred_at` |
| `dispute_start` | `dispute_workflow_start.started=false`, including claimed intents | `requested_at` |
| `dispute_decision` | `dispute_decision.processed_at IS NULL` | `admitted_at` |

No user/booking/dispute/event/request ID, token, raw route, actor text or serialized
workflow payload is read into these aggregates or used as a label. Queue names
come from code, never submitted data. The existing JVM/HTTP metrics remain separate.
These rows describe durable admission/acknowledgement, not engine task status or
the number of healthy human waits. A started workflow that faults with no pending
inbox rows can have zero backlog. Pending rows can also need operator investigation
rather than an automatic retry. An UP readiness result does not prove workflow
progress, and zero inbox counts do not prove every workflow is healthy.

## Sampling and failure behavior

`bliss.operations.backlog.enabled=true` enables collection by default.
`bliss.operations.backlog.every=10s` sets the default interval; initial sampling
is delayed three seconds. Concurrent scheduled collections are skipped. The test
profile disables collection to avoid background work in ordinary database fixtures;
gauges remain registered with unknown values. The packaged verification uses a
one-second interval only for its isolated scenario.

The SQL requests a two-second statement timeout. Pool acquisition and transaction
timeouts are separate runtime limits; a stalled attempt can take longer. This is
a periodic read-only aggregate scan, not a scrape-driven or per-record query.
Measure database cost before increasing sample frequency or dataset scale. No
index/schema change or scalability claim is included.

Counts and oldest ages are NaN before the first success. On a runtime collection
failure, the collector increments its failure counter, marks the latest attempt
unsuccessful and retains the entire last good snapshot. It never substitutes zero
for unknown work. Counts/oldest ages remain frozen; snapshot age keeps increasing.
A subsequent success replaces all queue aggregates and resets snapshot age,
while the cumulative failure counter remains until process restart. Individual
gauge reads within a scrape are not a transactional cross-metric snapshot.

Operators must consider availability, freshness and last-attempt success together
with backlog and age. Check collector health before interpreting a zero or old
count. Thresholds need representative workload and operator approval; none is
approved here. Metrics neither suspend traffic/dispatch nor repair failed workflows.
Never edit settlement or checkpoint rows to make these numbers disappear.

## Reproduce verification

Use Java 21, Docker and an isolated checkout/build directory. From `services/api`:

```sh
./mvnw -B -ntp verify -Dtest=WorkflowBacklogMetricsTest,OperationsSmokeTest -Dit.test=WorkflowBacklogMetricsIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. Do not supply a shared datasource or run dev mode in
that build directory. The packaged test owns its PostgreSQL/JWKS/API, checks
unique Docker ownership labels before stopping/starting its database, and accepts
no external target. Existing project services/volumes remain untouched.

The ordinary test verifies disabled collection, NaN/availability behavior and
fixed cardinality without database access. The packaged test pauses dispatch,
creates a booking and cancellation through HTTP, and observes one pending start
and action with positive ages. It checks anonymous/customer/provider/admin denial
and operator access, then stops only its database. Actual successful HTTP scrapes
must retain the counts while collection failures and snapshot age increase.
After database recovery, sampling succeeds in the same API process. Restarting
only the owned API with dispatch enabled drains both queues; terminal action
replay retains the same processed event. The two dispute queues are verified empty
in this scenario; positive dispute admission/settlement remains covered separately
by domain regression, not by this metrics drill.

Cancellation is admitted while the booking is CREATED, before authorization;
this metrics scenario makes no new payment-settlement claim. Existing packaged
workload/restore tests cover simulated settlement. The test has a four-minute
JUnit bound, 60-second metric waits and five-second HTTP deadlines; those are
failure guardrails, not service objectives. See [progress](../agent/PROGRESS.md)
for executed evidence. Private collection, dashboards, alert rehearsal, engine
failure/wait-age observability and capacity acceptance remain open under
[#15](https://github.com/Stuaarts/Bliss/issues/15) and [readiness](READINESS.md).
