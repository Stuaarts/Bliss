# Dispute backup/restore drill (#15)

`DisputeBackupRestoreIT` restores actual DisputeFlow checkpoints, private evidence,
and an admitted decision into a fresh owned PostgreSQL database. It extends the
[paused booking drill](WORKFLOW_BACKUP_RESTORE.md), rather than restarting the API
against the same database. All identities, records, keys and containers are
synthetic and owned by the test. It accepts no external database or archive.

## Reproduce

Use Java 21, Docker and an isolated checkout/build directory. From `services/api`:

```sh
./mvnw -B -ntp verify -Dtest=OperationsSmokeTest -Dit.test=DisputeBackupRestoreIT,WorkflowBackupRestoreIT,DisputeRestartIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. Do not supply a shared datasource or run dev mode in
the build directory. The test supplies its own PostgreSQL/JWKS configuration to
the packaged production API. Existing services and volumes are not reset.

## Two recovery paths

Both paths create a direct booking through HTTP, authorize the simulated CAD
80.50 payment, accept its schedule, start and complete the provider's work, and
open a customer dispute. The shared `DisputeRecoveryFixture` also drives the
existing two-crash dispute test. No booking status or decision is fabricated by SQL.

| Source snapshot | Saved payment/evidence | After restore |
| --- | --- | --- |
| COLLECTING_EVIDENCE | AUTHORIZED; opening reason plus a second customer evidence record; no decision | Same instance resumes evidence wait; provider adds evidence; independent admin admits PROVIDER_FAVORED; one CAPTURE completes the booking |
| UNDER_REVIEW with an unprocessed decision | CAPTURED before the dispute; three evidence records from both parties; independent admin's CUSTOMER_FAVORED decision accepted through HTTP | Same admitted receipt initially remains unprocessed; same instance processes it after restoration is enabled; one REFUND completes the case |

For the second path, the source API first exits, then restarts with both
`bliss.workflow.dispatch-enabled=false` and
`quarkus.flow.persistence.auto-restore=false`. Disabling dispatch alone does not
pause engine instances restored at startup. The independent admin then admits
the decision through the API. The test requires it to remain pending beyond a
workflow polling interval before stopping the API again. This is a test fixture
for a durable admitted decision, not an operator pause endpoint or an online
backup procedure. Runtime defaults and settlement rules are unchanged.

## Snapshot and restored execution

1. Require the named saved state, original workflow ID, started dispute intent,
   and three persisted loop task checkpoints. Stop the sole source API and
   require it to exit before any dump or destination worker starts.
2. The test-only `WorkflowBackup` helper records every public base table's row
   count and sorted row-content digest, including Flyway, V11 domain state,
   payment ledger, evidence, admitted decisions and Flow checkpoints. Matching
   PostgreSQL 17.6 `pg_dump --format=custom` runs inside the source container.
3. Start a distinct owned destination, require an empty public schema, and
   restore with `pg_restore --exit-on-error --no-owner --no-privileges`. Require
   all table counts/digests to match before starting an API against the copy.
4. Start the restored API with dispatch and automatic restoration disabled.
   Verify the same case/instance, non-resetting saved-state age, exact customer
   evidence page, exact evidence receipt replay and conflicting-key rejection.
   A nonparticipant retains 404 for case/evidence/history/execution; the customer
   cannot make an admin decision. The saved admin receipt replays as unprocessed.
5. Stop that API, enable both restoration and dispatch in the owned test process,
   and restart against only the restored database. Resume the evidence path or
   process the already admitted admin decision. Verify the original workflow ID,
   resolved state and booking/payment result. Decision replay twice retains the
   original admission timestamp and processed flag; a different key is rejected.
6. Read-only SQL requires three evidence rows, three dispute history entries,
   exactly one processed decision, one AUTHORIZE and one CAPTURE. Only the
   customer-favored captured path has one REFUND; neither path has VOID. The
   frozen source's full snapshot remains identical, still in its original wait
   with no new settlement. Only the restored worker runs after the backup.

The shared backup helper is also used by the existing booking cancellation
drill. It creates its own temporary host archive under ignored `target`, removes
it on success/failure before returning, and never reads a caller-supplied archive.
Comparison digests stay in memory and are not printed by comparison failures.
They are comparison aids, not archive authenticity or cryptographic integrity
checks. Proof lines contain aggregate stages/counts only. Owned APIs, clients,
JWKS and database containers close; the containers' internal archives disappear
with them. Machine/JVM termination can interrupt cleanup, so ignored build files
still require local access controls. Never commit or publish a database backup.

## Scope and remaining operator acceptance

This is quiesced recovery of one case per isolated database on the same pinned
API/Flow/PostgreSQL versions, with test identity keys and a simulated gateway.
It covers evidence wait, admin wait, an admitted decision and CAPTURE/REFUND
idempotency after database restoration. It does not establish simultaneous case
recovery, an in-flight settlement backup, changed-version compatibility, an
online snapshot race, managed or point-in-time backups, real identity restore,
external payment reconciliation, high availability or an approved RPO/RTO.
No recovery-time objective or production incident acceptance is claimed.

Before hosting, operators must fence the former worker, control archive
encryption/access/retention, restore into an isolated candidate, verify schema,
identity routing and domain/ledger/checkpoint state, and reconcile external
effects before enabling execution or ingress. The test's superuser-owned
containers do not establish least-privilege runtime/migration credentials or
production backup permissions. Never run source and restored copies of the same
workflow/payment history together.

Each new parameterized case has a five-minute JUnit failure bound. The existing
packaged harness bounds startup, status/checkpoint polling and HTTP requests;
backup verification SQL has ten-second statement limits. These are test
guardrails, not production objectives. See [progress](../agent/PROGRESS.md) for
executed evidence and [readiness](READINESS.md) for remaining work. Required
current-head CI and native iOS acceptance remain at the deferred final gate.
