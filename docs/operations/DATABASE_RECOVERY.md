# PostgreSQL backup and recovery

Issue #15 tracks recovery alongside the remaining production operations work.
The local drill below verifies a logical database round trip using synthetic
records. It does not establish production recovery, native acceptance, identity
provider recovery, or successful execution of restored Flow instances.

## Reproduce the local drill

With Python 3 and a ready Linux Docker engine, run from the repository root:

```sh
python3 -B infrastructure/scripts/test-database-recovery.py --isolated-docker
```

On Windows, the existing guarded `infrastructure/scripts/start-docker.ps1` can
start Docker Desktop. The drill creates uniquely named, ownership-labelled
PostgreSQL 17.6 source/target containers with no network, published ports or
persistent volumes. It accepts no existing database address or container name.
It applies V1–V10 SQL migrations and the local catalog seed, then inserts explicit
synthetic fixtures. This is direct SQL setup for a database test; it does not
claim that app users produced these records or that Flyway itself ran.

The fixture includes a request/accepted quote, four bookings, simulated payment
ledger, two-party chat including Unicode, review, dispute evidence and a queued
manual admin decision. Three synthetic workflow rows, checkpoints and a cloud
event contain opaque binary payloads, including NUL and non-UTF-8 bytes. These
are storage fixtures, not executable Flow instances. The drill never starts the
API against them; packaged-process workflow tests remain the execution proof.

The script creates a custom-format `pg_dump`, hashes it with SHA-256, restores it
into the empty target with `pg_restore --exit-on-error --single-transaction`, and
compares every public table's row count and full-row hash. It also compares column
types/defaults/nullability, index definitions, constraint names/keys/validation
metadata and non-CHECK definitions. PostgreSQL can reparse equivalent CHECK array
casts into different displayed expressions; textual CHECK equality is not assumed.
Representative money, status, rating, outcome, unique-ledger and participant-FK
violations must still be rejected after restore.

Negative checks reject an archive with a mismatched checksum, a truncated archive
even when its new checksum matches, and a nonempty restore target. The truncated
restore must leave the target empty. A write made after the backup stays in the
source and is absent from the restored snapshot, demonstrating the backup boundary.
Failed constraint writes must leave the restored row hashes unchanged.

Only hashes, counts, schema metadata, migration names, source revision and observed
dump/restore durations are retained in `.work/database-recovery/run-*/proof.json`.
Raw archives and credentials stay outside that evidence directory and are removed
with owned containers in cleanup. Ownership labels and the private directory's
resolved parent are checked before removal. The [CI workflow](../../.github/workflows/database-recovery.yml)
uploads only the proof directory for seven days. Failed runs record FAIL rather
than being treated as accepted evidence. SHA-256 detects accidental corruption;
it does not authenticate an attacker-controlled archive or encrypt its contents.

## Operator procedure for a future real-data recovery

This procedure is preparation, not an instruction to restore existing local or
production databases now. Infrastructure, retention and recovery objectives still
need project-owner decisions before beta. Follow the official PostgreSQL 17
[pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html) and
[pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html) documentation.

1. Record the application commit, PostgreSQL/client versions, migration checksums,
   backup time, trusted source, archive digest and encryption-key reference.
   Use a compatible client and inspect backup diagnostics. A logical dump gives
   one database snapshot; it does not include cluster roles/tablespaces, WAL/PITR,
   identity-provider state, external media, secrets or deployment configuration.
2. Store archives in restricted, encrypted storage with an independently protected
   key and trusted integrity metadata. Backups include private chat, dispute text,
   identities and audit records. Exclude dumps, passwords and bearer/refresh tokens
   from Git, CI artifacts and ordinary logs. Keep a recoverable copy outside the
   failure domain; choose frequency, retention and deletion rules before collecting
   real data. No real-data backup destination is configured by this slice.
3. Restore first into a newly created, quarantined empty database on a separate
   instance. Verify the exact target independently of the source. Keep API workers
   stopped and outbound integrations disconnected. Restore trusted archives only;
   PostgreSQL restores may execute source-defined code. Reject a nonempty target
   rather than using destructive clean/drop options. Preserve the original source
   and archive so a failed rehearsal cannot destroy the recovery path.
4. Supply credentials through an access-controlled passfile or secret mechanism.
   Avoid passwords in command arguments. The following are templates using
   deliberately supplied connection settings, not runnable defaults:

   ```sh
   pg_dump --format=custom --no-privileges --file="$BLISS_BACKUP_FILE" "$PGDATABASE"
   pg_restore --exit-on-error --single-transaction --no-owner --no-privileges \
     --dbname="$BLISS_RESTORE_DATABASE" "$BLISS_BACKUP_FILE"
   ```

   Set `PGHOST`, `PGPORT`, `PGUSER` and `PGPASSFILE` for the intended source during
   backup and the separate target during restore. Provision destination ownership
   and least-privilege grants explicitly; this example deliberately omits source
   ownership/ACL restoration. Do not automatically point the application at it.
5. Compare schema/migration history and domain/ledger records before starting the
   matching app version. Preserve Flyway history when present; do not repair its
   checksums to bypass a mismatch. Preserve all Flow persistence, inbox/start-intent,
   booking/dispute history, idempotency and ledger tables together. Do not restore
   only domain tables or manually replay decisions/payment events to compensate
   for missing workflow state. A prior backup loses writes after its snapshot;
   reconcile that loss before admitting traffic.
6. Recover the identity provider separately, preserving issuer, subjects, required
   realm/client configuration and managed signing-key material. The committed
   realm template is not a backup of live users. Verify login/refresh and server
   authorization against restored identities without exporting tokens to logs.
7. Start one matching API worker in quarantine only after database checks pass.
   Overdue workflow timers may advance immediately. Inspect restored waits,
   admitted events, pending decisions, audit and simulated ledger; verify no
   duplicate settlement, then exercise customer/provider/admin reads and actions.
   Current Flow support is single-worker. The SQL-only drill supplies no evidence
   for this step; use the packaged restart tests and a separate application drill.
8. Record elapsed detection/restore/validation time and actual data loss against
   agreed recovery objectives. Cutover and traffic admission require the real-data
   release procedure. Retain a rollback path; quarantine failed restores and record
   the failure without overwriting the trusted backup. Dispose of rehearsal data
   according to the eventual retention policy.

## Remaining operations decisions

The owner must choose backup frequency, maximum tolerable data loss (RPO), service
recovery time (RTO), retention, archive/key custody, identity-provider recovery,
off-site copies, PITR and rehearsal frequency. Local dump/restore durations are
measurements of tiny synthetic fixtures, not production objectives or guarantees.
Managed hosting, paid services, deployment, moderation/retention implementation,
multi-worker leases and operator workflow repair remain outside this slice.

GitHub billing and the pending native iOS checks are deferred to the final release
gate at the user's October 10 request. [PR #39](https://github.com/Stuaarts/Bliss/pull/39)
remains draft and unmerged. This independent recovery work can be checked locally;
no full portfolio sign-off is implied while CI/native gates remain pending.
