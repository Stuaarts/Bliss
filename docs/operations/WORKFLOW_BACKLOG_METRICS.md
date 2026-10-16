# Aggregate workflow operations metrics (#15)

`WorkflowBacklogMetrics` periodically reads four durable inbox aggregates and one
durable dispute-failure aggregate, plus booking decision-wait aggregates, in a
single SQL statement/transaction.
Prometheus scrapes read only its in-memory snapshot;
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
| `bliss_workflow_dispute_failed_cases` | Unresolved dispute records with a saved workflow failure timestamp |
| `bliss_workflow_dispute_failed_oldest_seconds` | Age of the earliest saved failure timestamp among those records at snapshot time; zero when none |
| `bliss_workflow_booking_waiting_bookings{stage="..."}` | Current bookings in the corresponding saved decision-wait state |
| `bliss_workflow_booking_waiting_oldest_seconds{stage="..."}` | Oldest latest matching transition age at snapshot time; NaN if any matching booking lacks that history |
| `bliss_workflow_booking_waiting_history_missing{stage="..."}` | Current decision-wait bookings without a matching transition-history row |

There are exactly four code-defined `queue` values and twelve backlog time series,
plus two dispute-failure gauges without labels and six booking decision-wait
samples with two fixed `stage` labels (twenty custom series total):

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

## Durable dispute failure signal

The dispute-failure gauges select `workflow_failed_at IS NOT NULL AND
resolved_at IS NULL` from `dispute`. Existing V10 constraints align resolution
timestamps with dispute states. Count means unresolved failed cases, not individual
retry attempts or a cumulative error counter. Oldest age starts at the saved
failure timestamp, not case creation or the start of evidence/admin review.

The existing `DisputeFailureListener` writes the content-free timestamp once when
DisputeFlow fails. In the pinned Flow integration, failed engine checkpoints are
removed; counting engine rows alone would miss this condition. The domain marker
survives that cleanup. Neither metric reads the cause, rationale, evidence, actor,
booking/dispute ID or serialized workflow state. Failure-marker/listener behavior,
schema and settlement rules are unchanged by these gauges.

Both gauges share the backlog snapshot availability, age, last-attempt success
and collection-failure counter. They are NaN before first success and remain
frozen with the last good snapshot on failed collection. Consider those freshness
signals before interpreting their values. They are aggregate operator telemetry;
they neither identify a case nor grant permission to read evidence or settle it.
Use existing authorized dispute/execution views and the operator investigation
procedure. Do not replay decisions, clear failure markers or edit ledger rows
to force recovery. Automatic recovery and real-provider reconciliation remain open.
Booking failures without a durable dispute marker, faults that prevent marker
persistence, healthy human-wait ages and comprehensive engine health are outside
this signal. A zero count does not prove the absence of all workflow failures.

## Booking decision waits

`stage="provider_decision"` selects current `WAIT_PROVIDER` bookings;
`stage="customer_decision"` selects current `WAIT_CUSTOMER` bookings. These are
saved domain states awaiting the corresponding decision, not an engine-health
classification. A faulted booking can remain in either state. Scheduling,
in-progress work, disputed/terminal bookings and dispute-party/admin waits are
outside these two stages. Completed, cancelled and expired bookings are excluded.

For each booking, the query takes the latest `booking_status_history.occurred_at`
whose status matches the current state. The stage age is database transaction time
minus the earliest of those latest transitions, clamped to zero if negative. It
measures time in the saved state, not booking age, process uptime, request latency
or an approved decision deadline. Restarting the API does not reset the history
timestamp. Empty stages have count, age and missing-history values of zero after
successful collection.

If any current-stage booking lacks matching history, its count remains included,
the missing-history gauge rises, and the entire stage's oldest age is NaN. The
collector does not substitute booking creation time or calculate a partial oldest
age that could hide an older unknown wait. This is a successful observation of
incomplete history, distinct from SQL collection failure. Investigate through
authorized views and restricted records; do not edit history to suppress telemetry.
Counts/ages/missing-history values share the existing snapshot freshness contract.
Scrapes read memory; labels contain only the two code-defined stage names.

The packaged quote/rejection and direct/completion restart scenarios verify
positive provider/customer counts and transition ages, unchanged workflow IDs,
non-resetting ages across process crashes, removal from stages after terminal
settlement and one simulated settlement per booking. The rejection scenario
temporarily removes one history row in its owned synthetic database, requires
count one/missing-history one/age NaN, then restores that exact row before the
crash. This reversible test-only fault injection is not an operator recovery
procedure and accepts no external target. Ordinary disabled-collection tests
require all six samples to be NaN before successful collection. The twenty-series
packaged access/outage/drain scenario observes empty decision stages throughout.

## Sampling and failure behavior

`bliss.operations.backlog.enabled=true` enables collection by default.
`bliss.operations.backlog.every=10s` sets the default interval; initial sampling
is delayed three seconds. Concurrent scheduled collections are skipped. The test
profile disables collection to avoid background work in ordinary database fixtures;
gauges remain registered with unknown values. The dispute regression explicitly
opts into one-second collection for its isolated test profile. The packaged
verification and the two booking restart scenarios also use a one-second interval
only for their owned scenarios. Other packaged scenarios retain default sampling.

The SQL requests a two-second statement timeout. Pool acquisition and transaction
timeouts are separate runtime limits; a stalled attempt can take longer. This is
a periodic read-only aggregate scan, not a scrape-driven or per-record query.
Measure database cost before increasing sample frequency or dataset scale. No
index/schema change or scalability claim is included.

Counts and oldest ages are NaN before the first success. On a runtime collection
failure, the collector increments its failure counter, marks the latest attempt
unsuccessful and retains the entire last good snapshot. It never substitutes zero
for unknown work. Counts/oldest ages remain frozen; snapshot age keeps increasing.
A subsequent success replaces all queue/failure/wait aggregates and resets snapshot age,
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
./mvnw -B -ntp verify -Dtest=DisputeTest,WorkflowBacklogMetricsTest -Dit.test=WorkflowBacklogMetricsIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dtest=WorkflowBacklogMetricsTest,OperationsSmokeTest -Dit.test=WorkflowRestartIT,WorkflowBacklogMetricsIT -Dquarkus.http.test-port=0
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
by domain regression, not by this metrics drill. The empty dispute-failure gauges
also remain zero in this packaged scenario, including outage/recovery.
The six booking decision-wait samples likewise remain zero there: its cancellation
is admitted before authorization and never enters either decision-wait state.

The actual DisputeFlow exhausted-retry regression now reads Prometheus output:
four failed simulated settlement attempts roll back, the saved failure timestamp
exists after engine checkpoint removal, and the failed-case count rises by one
with positive oldest age. Exactly two fixed, unlabeled failure samples are allowed.
The payment still has only AUTHORIZE and the unprocessed decision/case remain
under review; no automatic settlement/recovery is claimed. This test uses actual
Quarkus/PostgreSQL/Flow and a test-only fault-injecting payment alternative. It
does not run against a live gateway. Its failure-metric polling has a fifteen-second
test deadline. Positive failure/rollback evidence is separate
from the packaged empty-gauge/access/outage check.

Cancellation is admitted while the booking is CREATED, before authorization;
this metrics scenario makes no new payment-settlement claim. Existing packaged
workload/restore tests cover simulated settlement. The packaged metrics test has
a four-minute JUnit bound, 60-second metric waits and five-second HTTP deadlines; those are
failure guardrails, not service objectives. See [progress](../agent/PROGRESS.md)
for executed evidence. Booking wait-metric polling has a twenty-second test bound;
the history fixture's SQL statements have ten-second limits. Private collection,
dashboards, alert rehearsal, broader booking/engine failures, dispute waits,
engine-health classification and capacity acceptance remain
open under
[#15](https://github.com/Stuaarts/Bliss/issues/15) and [readiness](READINESS.md).
