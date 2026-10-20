# Pause REST write admission (#15)

`bliss.operations.writes-paused=true` rejects new matched REST requests whose
method is not GET, HEAD or OPTIONS. It is a startup setting, disabled by default.
Authorized reads retain their existing ownership and role checks. The readiness
check `API write admission` becomes DOWN; liveness is unaffected. The production
operator boundary still protects health and metrics.

This flag controls REST admission only. It does not stop dispatch, restore or
pause an already executing workflow, drain in-flight requests, revoke sessions,
disable non-REST handlers or fence another process. For a read-only maintenance
API, stop the old worker and explicitly disable both dispatch and auto-restore
as described below. Run one worker; no distributed fence or live toggle is added.

## HTTP contract

Refused writes return HTTP 503, `application/problem+json`, `Cache-Control:
no-store` and a generated `X-Request-ID` matching the problem's `requestId`.
The fixed detail is `API writes are temporarily paused.` No supplied correlation,
body, token, resource identifier or exception is included. Normal REST diagnostic
events retain only their existing bounded metadata. The fence aborts before body
validation or resource invocation; route/security rejection can still happen
earlier. Unmatched routes and non-REST HTTP handlers are outside this contract.

All matched write methods are gated, including bootstrap and idempotent replays.
A blocked request creates no business write or idempotency receipt. Keep the same
request/key when retrying after admission reopens. No Retry-After duration is
promised because the operator controls reopening. A 503 does not imply that a
previous request made before the pause failed; inspect its existing receipt/state.

Readiness DOWN prevents routing only where the ingress actually respects that
probe. Direct reads can still work while the database/identity dependencies are
healthy. This is an application guard, not private ingress, a firewall, a database
write restriction or an assertion that every GET implementation is read-only.

## Controlled maintenance and reopening

1. Restrict admission through the approved ingress procedure. Inspect the saved
   workflow/ledger state and ensure the planned operation has a recovery path.
   Stop the sole old API worker after the intended durable wait is saved; do not
   assume this safely interrupts an in-flight external settlement.
2. If authorized inspection requires a running API, start a new process with
   runtime credentials, the existing issuer settings and all three controls.
   For the [separate-role setup](DATABASE_ROLES.md), from `services/api`:

   ```sh
   java -Dbliss.operations.writes-paused=true -Dbliss.workflow.dispatch-enabled=false -Dquarkus.flow.persistence.auto-restore=false -Dquarkus.flyway.migrate-at-start=false -Dquarkus.flyway.validate-at-start=true -jar target/quarkus-app/quarkus-run.jar
   ```

   Check authenticated `/q/health/ready`: HTTP 503, admission DOWN and database
   UP. Check liveness UP, protected metrics, expected owned reads and write 503s.
   Do not treat any arbitrary readiness 503 as proof of the maintenance state.
3. Keep this process on the approved private access path. Read inspection remains
   subject to ordinary participant/owner permissions; the operator health role
   does not grant access to private marketplace records. Database/schema work
   requires stopping this inspection API too and following the
   [migration procedure](DATABASE_MIGRATION_COMMAND.md). The admission flag does
   not make concurrent schema changes safe.
4. To reopen, stop the maintenance process and start one worker with
   `bliss.operations.writes-paused=false`, dispatch enabled and auto-restore
   enabled. Retain runtime credentials and read-only startup validation. Require
   readiness UP, the saved workflow instance/evidence, expected settlement state
   and retry behavior before restoring ingress. Do not leave an old worker alive.

The switch does not freeze business time. Persisted wait ages continue to grow,
and deadlines may have expired when execution resumes. It provides no automatic
repair, deployment rollback, credential rotation or incident reconciliation.
Hosted ingress/draining, in-flight/external-effect safety, multi-worker ownership,
operator acknowledgement and production maintenance acceptance remain open.

## Verification

`WriteAdmissionTest` checks readiness DOWN with database UP, liveness and public
GET/HEAD/OPTIONS, anonymous private-read denial, and correlated POST/PUT/DELETE
refusal before malformed-body parsing. Multiple authenticated roles cannot bypass
the gate; user count remains unchanged. Existing default-profile tests exercise
normal writes with the default false setting.

`MaintenanceAdmissionIT` uses a disposable database, restricted runtime role and
disabled migration login. It creates an actual booking/dispute and saves an
evidence wait, then stops the worker. The maintenance process uses all three
controls. Protected health/metrics and authorized reads work; outsider and
anonymous private reads remain denied. Six blocked writes leave every public
table's count/digest unchanged across two dispatch intervals. After a distinct
normal process starts, the same instance, evidence and wait age survive; the
previously rejected keys are admitted and one simulated AUTHORIZE/CAPTURE settles
the case. Decision replay remains processed and database restrictions still hold.

The test launcher can explicitly wait for expected maintenance readiness 503;
existing callers still require readiness 200. The drill then independently checks
admission DOWN and database UP. There is no external host/database input.

From `services/api`, with Java 21 and Docker in an isolated build directory:

```sh
./mvnw -B -ntp verify -Dtest=OperationsSmokeTest,WriteAdmissionTest -Dit.test=MaintenanceAdmissionIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. See [progress](../agent/PROGRESS.md) for executed results
and [readiness](READINESS.md) for deferred CI/native and production gates. Existing
project credentials, databases and volumes are untouched; identity and payment
in the drill are synthetic.


## Flutter client recovery

The [client maintenance guide](CLIENT_MAINTENANCE.md) describes the 503-only
profile read fallback, manual account retry, fixed error message and pending
submission retention. Those client checks use synthetic HTTP responses; the
backend drill above remains the evidence for actual admission and reopening.
