# Durable booking lifecycle

`BookingLifecycleFlow` uses the released Flow 1.2.0.CR1 Java DSL and PostgreSQL/JPA
checkpoints. V5 follows that release's persistence schema; Hibernate validates it
using the documented camel-case-to-underscores naming strategy. Catalog/profile
CRUD remains ordinary application services.

```mermaid
stateDiagram-v2
    CREATED --> WAIT_PROVIDER: authorize simulated payment
    CREATED --> CANCELLED: customer cancels before authorization
    WAIT_PROVIDER --> SCHEDULED: provider accepts with future schedule
    WAIT_PROVIDER --> CANCELLED: rejection or customer cancellation / void
    WAIT_PROVIDER --> EXPIRED: decision deadline / void
    SCHEDULED --> CANCELLED: customer cancellation / void
    SCHEDULED --> IN_PROGRESS: provider starts after scheduled time
    IN_PROGRESS --> WAIT_CUSTOMER: provider marks complete
    WAIT_CUSTOMER --> COMPLETED: customer confirms / capture
```

Quote acceptance and direct offering booking share `BookingCreation`, inserting a
durable start request in the same transaction as the snapshot/history/audit. A
dispatcher claims it under the booking lock, stores the canonical engine
instance ID, commits, then starts the instance. A crash before engine persistence
leaves a claim recoverable after 30 seconds. Existing checkpoints belong to the
engine's startup restoration; the dispatcher never starts a replacement for them.
Every domain step checks the canonical instance ID to neutralize abandoned starts.

Authorized HTTP actions commit to a PostgreSQL inbox and return 202. Flow waits
for two seconds using its durable timer, then calls a thin domain step. This local
adapter provides durable correlation without a broker; it polls the inbox rather
than publishing CloudEvents. One pending action per booking prevents ambiguous
simultaneous decisions. A booking lock serializes action admission and processing.
The domain transition, event acknowledgment, status history, audit entry and fake
payment ledger commit together. Replayed steps and duplicate action UUIDs are safe.
The action admission timestamp is stored explicitly so replay uses the same
schedule/deadline decision, including actions admitted just before a timeout.

Demo policy: provider decisions expire after `bliss.booking.provider-decision-seconds`
(default 86400). Schedules must be future UTC timestamps, at most microsecond
precision. Starting requires reaching that schedule. Customer cancellation is
allowed through SCHEDULED, but not after work starts. Customer confirmation waits
indefinitely: absence of a response never triggers an automatic capture. These
are local simulation policies, not approved commercial cancellation/payment terms.

`PaymentGateway` isolates the side-effect boundary. `FakePaymentGateway` implements
AUTHORIZE, CAPTURE, VOID and REFUND with a unique booking/operation ledger key.
No card data, payment credentials or external money movement exists. REFUND is
verified at the adapter boundary and through DisputeFlow (#10).
Transient payment/persistence failures retry up to three times after the initial
attempt, with a 250 ms base delay. Exhaustion faults the workflow for diagnosis;
automated operator recovery and real-provider reconciliation remain hardening work.

## Release-specific compatibility fix

The forced-restart test reproduced a defect in Flow 1.2.0.CR1: JPA writes a row for
each task iteration, but restoration builds a map keyed only by task pointer and
throws `Duplicate key do/0/advanceBooking` after multiple loop iterations.
`LoopSafeJpaOperations`, a Bliss-local CDI alternative, retains the newest completed
iteration for each task in the same engine transaction. It matches the reader's
one-checkpoint-per-task contract and bounds the three-task loop's checkpoint rows.
The upstream checkout is unchanged. Keep the restart regression when upgrading;
remove the adapter only after the upstream release passes it independently.
The adapter also materializes the startup scan and closes its transaction before
returning instances to the asynchronous restorer. Otherwise the release propagates
the scan transaction into worker threads, producing Narayana concurrent-transaction
warnings and rollback during startup. The scan is intentionally in-memory for the
local portfolio workload; large deployments need paged restoration. Follow-up: #22.

## Verification and operating limits

`BookingWorkflowTest` exercises actual Flow execution, completion/capture, repeated
events, cancellation, rejection/void, provider timeout, transient retry, abandoned
start recovery, ownership checks and history/payment visibility. `WorkflowRestartIT`
runs during Maven verify: an isolated PostgreSQL container and a packaged API are
started, waiting loops checkpoint, and the API is forcibly killed. The same database
restores the same instance, then rejection or completion verifies a single settlement.
The completion case starts from a direct offering booking and also crashes while
waiting for the customer; the rejection case starts from a quote. This is a real
process test, not a mock restoration call.

`WorkflowBackupRestoreIT` separately stops the sole worker with a real
WAIT_PROVIDER booking, dumps the source database, restores a distinct empty
database and checks every public table's row count/content digest before startup.
The same artifact resumes the original instance and cancels once, including
action/creation replays, while the stopped source stays unchanged. See the
[paused workflow backup/restore drill](../operations/WORKFLOW_BACKUP_RESTORE.md)
for fencing, archive cleanup and limits. This offline synthetic case does not
establish managed/PITR recovery or external payment reconciliation.

Run one API worker against this local database. Multi-replica workflow ownership,
distributed leases, dead-letter/operator tooling, retention and real payment
outbox/reconciliation are outside this portfolio slice. Do not roll back V5 while
instances exist or change workflow identity/version without a migration plan.
History, payment and execution endpoints enforce booking ownership. Execution
status exposes engine state without stack traces or other users' data.

## DisputeFlow (#10)

```mermaid
stateDiagram-v2
    WAIT_CUSTOMER --> DISPUTED: customer files within window
    COMPLETED --> DISPUTED: customer files within window
    DISPUTED --> COLLECTING_EVIDENCE: durable case start
    COLLECTING_EVIDENCE --> UNDER_REVIEW: both parties submit or deadline
    UNDER_REVIEW --> RESOLVED_CUSTOMER: independent admin favors customer
    UNDER_REVIEW --> RESOLVED_PROVIDER: independent admin favors provider
```

The booking's latest WAIT_CUSTOMER history starts a seven-day filing window.
Opening the case atomically records its initial evidence, history, audit and start
intent and sets the booking DISPUTED. BookingLifecycleFlow then stops. DisputeFlow
(`com.bliss/dispute-resolution/1.0.0`) retains one canonical instance and checks
durable evidence/decision rows using a two-second persisted wait loop. Its input
contains only case ID and done flag, never evidence text.

After both parties submit or the saved 24-hour deadline expires, manual review
begins. Timeout never picks a winner. An independent admin records one immutable
decision and rationale. Customer-favored settlement refunds CAPTURED payments
(booking REFUNDED), or voids AUTHORIZED payments (booking CANCELLED).
Provider-favored settlement captures AUTHORIZED payments or retains CAPTURED
payments (booking COMPLETED). Settlement, case/booking history, audit and decision
processing commit together. Transient payment/database failures retry three times
after the first attempt, each in a new transaction.

Flow's pinned persistence writer deletes failed checkpoints. A Bliss lifecycle
listener therefore retains a failure timestamp on the case, including failures
after restoration. The execution API reports FAULTED without exception details.
No automatic redispatch or new evidence/decision is admitted on a failed case;
the original decision remains retry-readable and payment changes roll back.
Operator investigation/recovery tooling is still future hardening, not a UI retry
that bypasses the decision. Single-worker operating limits above also apply here.

`DisputeTest` covers authorization, deadlines, competing decisions, idempotency,
all settlement outcomes and exhausted retry rollback. `DisputeRestartIT` runs a
real booking through completion, kills the packaged API in both evidence and
admin waits, then verifies the same instance and one capture/refund ledger entry.
