# Durable booking workflow failures (#15)

When `booking-lifecycle` exhausts retries or otherwise fails, Flow can remove its
checkpoint. Previously an authorization failure left an unacknowledged start
intent that could be claimed again after thirty seconds. An action failure could
also appear PENDING after checkpoint cleanup, with no durable failure signal.

V11 adds nullable `booking.workflow_failed_at` (UTC timestamp). The booking lifecycle
listener writes it once for the canonical workflow instance of an active ordinary
booking, increments the optimistic version, and excludes terminal/disputed bookings.
No exception content is stored. Existing bookings are not backfilled: an absent
checkpoint alone is insufficient evidence of failure.

The scheduled start query and locked claim both skip marked bookings, and a
restored tick returns without acting on one. Unmarked abandoned claims retain
the existing recovery behavior. The owner-authorized execution endpoint returns
`ERROR` for active ordinary marked bookings, the same instance ID and the actual
checkpoint-presence flag. Historical markers do not override terminal outcomes
or the handoff to DisputeFlow. Booking business status, history, pending actions
and simulated ledger remain at their
last committed state. New ordinary booking actions return the existing generic
409 problem response. An exact prior event replay still returns its original
receipt and processed flag; conflicting reuse remains 409. Ownership gates apply
before these results are exposed. Dispute eligibility and settlement rules are
unchanged; this is not a new recovery or cancellation policy.

Two unlabeled cached gauges expose active ordinary marked bookings and oldest
saved failure age. See the [metric contract](WORKFLOW_BACKLOG_METRICS.md#durable-booking-failure-signal).
The booking start callback logs identifiers only, without the exception or payload.
This is a bounded diagnostic change, not a universal library-log privacy audit.

## Operator investigation

Check collection availability, freshness and latest-attempt success before
interpreting metrics. Use existing authorized booking, execution, history and
payment views to investigate the last committed state and pending receipt.
Preserve the failure marker, pending inbox rows, workflow correlation and unique
settlement guards. Do not clear the timestamp, replace checkpoints, replay a new
action or edit payment rows to force progress. No operator reset endpoint or
automatic recovery is provided. External-provider reconciliation, repair design
and production incident acceptance remain open.

Keep one API worker. A failure that cannot persist its marker (for example a
database outage during the listener) is outside this guarantee. No historical
failure backfill or general engine-health classification is claimed. Current
wait, pending inbox and failure counts can overlap and must not be summed as
distinct bookings.

## Migration and rollback

V11 only adds the nullable column; it preserves existing values and does not
alter business statuses, payments, history or checkpoints. Verify populated
V10-to-V11 migration and clean installation before hosting. An older binary
ignores the marker and can restart a failed authorization, even though it can
read the expanded schema. Do not roll back to such a worker with dispatch enabled
against marked bookings. Stop workers and use a separately reviewed compatible
roll-forward or recovery plan. Do not drop the column or discard recorded failures.

## Verification

From `services/api`, using Java 21 and Docker:

```sh
./mvnw -B -ntp verify -Dtest=BookingWorkflowTest,WorkflowBacklogMetricsTest -Dit.test=DatabaseInvariantsIT,WorkflowBacklogMetricsIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. Tests own disposable PostgreSQL/JWKS/API resources and
accept no external database. Two actual Quarkus/PostgreSQL/Flow cases fail after
simulated ledger writes on all four authorization or VOID attempts. They verify
rollback, checkpoint removal, the retained marker, unchanged workflow identity,
no dispatch after claim expiry, guarded ticks, owner-only ERROR, new-action denial,
receipt replay, and a positive failure-count delta/age. Existing transient retry
and unmarked abandoned-claim tests still apply.

The packaged metrics case separately seeds a synthetic saved marker, restarts a
distinct API process with scheduled dispatch enabled, and verifies retained age,
identity, ERROR, no authorization and exclusion of an older terminal marker.
This tests persistence/restart behavior; it does not inject an actual gateway
fault in the packaged binary. The migration case checks populated V10 records,
nullable UTC precision, no inferred historical failures and a repeated migration
no-op. See [progress](../agent/PROGRESS.md) for executed evidence and the deferred
GitHub/native release gates.
