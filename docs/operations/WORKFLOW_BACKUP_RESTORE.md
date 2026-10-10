# Paused workflow backup/restore drill (#15)

`WorkflowBackupRestoreIT` backs up an actual paused BookingLifecycleFlow and
executes its restored state in a fresh PostgreSQL database. It extends the
structural synthetic recovery work in draft [PR #40](https://github.com/Stuaarts/Bliss/pull/40)
with real packaged API/Flow execution. It accepts no external database, archive,
credentials or target. All data, signing keys and containers belong to the test.

## Reproduce

Use Java 21, Docker and an isolated checkout/build directory. From `services/api`:

```sh
./mvnw -B -ntp verify -Dtest=OperationsSmokeTest -Dit.test=WorkflowBackupRestoreIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. Do not supply shared datasource overrides or start dev
mode in the same build directory. The helper passes its own database/issuer
properties to each packaged process. No existing services or volumes are reset.

## Recovery sequence and assertions

1. Create a dedicated PostgreSQL 17.6 source, ephemeral loopback JWKS and one
   packaged production API. Create the customer/provider, offering and direct
   booking through authenticated HTTP with simulated CAD 80.50 payment.
2. Require WAIT_PROVIDER, a nonblank workflow instance ID and three persisted
   task checkpoints. Force-stop the owned API and require that process to exit.
   The source has one workflow, one AUTHORIZE and no booking events.
3. Record row counts and sorted row-content digests for every public base table,
   including Flyway, domain, ledger and Flow state. Run the matching PostgreSQL
   `pg_dump` client inside the source container in custom format. Copy its archive
   to a temporary file in the ignored API build directory.
4. Start a distinct owned PostgreSQL 17.6 container and require its public schema
   to have no tables. Copy the archive into it and run `pg_restore --exit-on-error`
   with no ownership/privilege restoration. Both commands must exit successfully.
   Before API startup, require every table's row count/content digest to match.
5. Start a new API process against only the restored database with the same
   application artifact. Normal Flyway validation and ORM startup must succeed.
   Require the original booking/workflow IDs in WAIT_PROVIDER through HTTP.
6. Cancel through the API, wait for CANCELLED with the same workflow ID, replay
   that action twice and replay the original creation key. Require the same
   processed event and original cancelled booking. The payment view must show
   simulated VOIDED CAD 80.50 and exactly AUTHORIZE then VOID.
7. Read-only SQL requires one booking, one started intent, one processed CANCEL,
   two ledger entries (one AUTHORIZE/one VOID), and exactly three history entries
   (CREATED/WAIT_PROVIDER/CANCELLED). Require the stopped source's complete
   snapshot to remain unchanged and still waiting with only AUTHORIZE.

Only the restored worker runs after recovery. Never run the source and restored
workers together against copies of the same workflow/payment history. The test
checks content equivalence in memory; digests are comparison aids, not an archive
authenticity or cryptographic integrity mechanism. It prints aggregate proof only,
without record IDs, tokens, rows or serialized Flow payloads.

The temporary host archive is removed on success/failure. Testcontainers removes
the owned databases and their internal archives; the API/JWKS/client close too.
A machine/JVM kill can interrupt cleanup, so ignored build files still need local
access controls. Never commit or publish a backup, even synthetic test output.

## Scope and operator acceptance still required

This is an offline, quiesced synthetic recovery of one WAIT_PROVIDER booking on
the same pinned API/Flow/PostgreSQL versions. It proves restored execution and
idempotent simulated cancellation; it does not prove managed backups, online
snapshot races, point-in-time recovery, changed-version compatibility, disaster
availability or an approved RPO/RTO. No recovery-time objective is measured.
Capture, refund, dispute, several simultaneous restored waits, external identity
records, real payment/provider reconciliation and infrastructure secrets remain
separate acceptance work. A row-equivalent archive alone does not establish that
any arbitrary workflow can safely resume.

Before hosting, operators must approve storage encryption/access, scheduling,
retention, off-site recovery and credential handling; fence the former worker;
restore to an isolated candidate; validate migrations, identity routing and
booking/ledger/workflow state; and reconcile external effects before enabling
dispatch or ingress. Do not restore an untrusted archive or use these test
commands against an existing database. This drill creates an empty owned target;
it does not provide an in-place restore or an operator recovery service.

The complete test has a four-minute JUnit failure bound. Packaged startup has
45 seconds per process, status/checkpoint waits have 20 seconds each, HTTP calls
have five seconds and SQL statements ten seconds. These guardrails do not define
production timeouts or a recovery objective.

See [progress](../agent/PROGRESS.md) for executed evidence and the
[operations readiness plan](READINESS.md) for remaining work under
[#15](https://github.com/Stuaarts/Bliss/issues/15). The deferred CI/native gate
still precedes merge and release sign-off.
