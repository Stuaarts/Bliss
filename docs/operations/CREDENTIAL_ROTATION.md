# Runtime credential rotation rehearsal (#15)

`RuntimeCredentialRotationIT` exercises password rotation and explicit session
revocation in an owned PostgreSQL 17.6 container, with a real persisted dispute.
It uses the [separate runtime/migration roles](DATABASE_ROLES.md) and the actual
packaged API. Existing project roles, credentials, databases and volumes are
untouched. This is local evidence for a controlled restart procedure; managed
secret delivery and production operator acceptance remain open.

## Controlled sequence

1. Fence incoming writes and stop the sole workflow worker after its durable
   checkpoints are saved. Keep the database and recovery evidence. Do not change
   credentials underneath an executing settlement or assume readiness controls
   admission everywhere. The test stops its active API, then starts a probe API
   with both `bliss.workflow.dispatch-enabled=false` and
   `quarkus.flow.persistence.auto-restore=false`. It sends no business traffic
   during revocation. Hosted ingress fencing is still an operator responsibility.
2. Through the approved administrator/secret process, generate and store the
   replacement runtime secret. Change the password and disable new runtime
   logins while draining the old deployment. Keep the migration credential
   separate. Never put real passwords in shell arguments, SQL transcripts,
   committed files or collected logs. This test generates disposable UUID
   secrets internally and prints only fixed proof flags.
3. Account for existing sessions explicitly. Password changes and NOLOGIN deny
   new authentication but leave existing authenticated sessions usable. In this
   drill an established JDBC connection still executes SQL, and the paused API
   can still report readiness UP through its pool after both changes. Readiness
   alone therefore does not prove credential revocation.
4. As an authorized database administrator, terminate the old runtime sessions
   with selection bound to the intended database and runtime role. The fixture
   accepts no external database or role; its prepared query only selects its own
   generated runtime's sessions in its own container. Require zero matching
   sessions afterward. Check other databases if the real login was shared; this
   test makes no cluster-wide or compromised-client revocation claim. Do not
   terminate migration, administrator or unrelated application sessions.
5. The old probe API must become unready with an actual authenticated HTTP 503
   and database check DOWN, while liveness stays UP in the same process. Stop
   that old deployment before re-enabling the runtime login. A password update
   does not reload the packaged API's datasource configuration automatically.
6. Deliver the replacement credential through the approved deployment secret
   path and start one worker with the original dispatch/restore settings.
   Keep startup migration disabled and read-only validation enabled, as described
   in the database-role guide. Verify readiness, restricted permissions, saved
   workflow identity/evidence and an idempotent business completion before
   restoring admission. The old password must still fail after LOGIN is restored.

If replacement startup fails, retain fencing and investigate; do not start a
second worker or restore the revoked secret as an automatic rollback. This
rehearsal does not implement managed rollback, emergency secret distribution,
secret version retention, certificate/identity-key rotation or incident automation.
An unfenced active-workflow outage may exhaust retries and needs separate
reconciliation. No automatic repair or zero-downtime rotation is claimed.

## Local verification

With Java 21 and Docker, from `services/api` in an isolated build directory:

```sh
./mvnw -B -ntp verify -Dtest=OperationsSmokeTest -Dit.test=RuntimeDatabaseRolesIT,RuntimeCredentialRotationIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. The new case requires SCRAM host authentication and
actual PostgreSQL SQLSTATE 28P01 for incorrect/old passwords and 28000 for a
disabled login. It checks an established connection survives the authentication
changes, then fails after explicit termination. It inspects health through the
existing operator boundary and compares all public table counts/digests across
the fenced interval. The restarted API uses only the replacement runtime
credential, while the migrator remains disabled. Runtime DDL/history/role/TEMP/
TRUNCATE restrictions still hold.

The waiting dispute retains its instance, transition age and evidence, without
a workflow failure marker. Provider evidence and an independent admin decision
produce exactly one simulated AUTHORIZE and CAPTURE; decision replay is already
processed. The shared test-only role fixture also runs the existing migration
denial and restricted workflow cases. No application configuration default,
schema, business transition or settlement policy changes. See
[progress](../agent/PROGRESS.md) for executed results and
[readiness](READINESS.md) for remaining release gates.
