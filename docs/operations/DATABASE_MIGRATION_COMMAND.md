# Standalone database migration command (#15)

The packaged API now also contains `com.bliss.operations.DatabaseMigration`, a
plain Java entry point for applying the bundled Flyway migrations separately
from the runtime API. It does not initialize Quarkus, HTTP, OIDC, CDI, schedulers
or workflow restoration. Use it with the [separate-role deployment](DATABASE_ROLES.md).
Existing application startup defaults remain unchanged.

## Prepare the release

Build and verify the API with Java 21. Deploy the complete `target/quarkus-app`
directory as one artifact, including `app` and both library directories; a lone
application jar is insufficient. Keep the artifact immutable during execution.
No extra Flyway installation, dependency or downloaded migration source is needed.
Only the artifact's `classpath:db/migration` resources are selected. Local/demo
seeds and environment-supplied migration locations are excluded.

Provision a dedicated database and restricted migration login first. That login
must own `public` and any existing public tables/views/sequences, have no role
memberships, and have no superuser, database/role creation, replication or RLS
bypass attributes. The runtime login is not suitable. The command neither
provisions roles nor changes grants. It checks the actual connected database
against the explicit expected name before invoking Flyway.

Fence admission and stop the sole workflow worker before migration. The command
does not stop workers, control ingress, create a backup or prove that another
deployment is absent. Preserve an accepted backup/recovery path and review the
proposed migration/rollback compatibility before rollout. See
[readiness](READINESS.md) for the single-worker and final release gates.

Provide these three variables through the approved migration secret process:

| Variable | Meaning |
| --- | --- |
| `BLISS_MIGRATION_DB_URL` | Explicit PostgreSQL JDBC network URL for the intended database; configure required TLS/connection options for the approved environment |
| `BLISS_MIGRATION_DB_USER` | The separate restricted migration login |
| `BLISS_MIGRATION_DB_PASSWORD` | Its secret, delivered in the child process environment |

There is no fallback to runtime credentials, local defaults, `.env` loading,
Quarkus profiles or `FLYWAY_*` settings. Do not place secrets in arguments,
committed files, command transcripts or logs. Connection establishment and the
preflight query have ten-second bounds; Flyway lock/retry and migration execution
still require an operator's deployment timeout and failure procedure. This is not
a managed migration job or secret service.

## Run and gate rollout

On Windows, from the repository root with Java 21 and PowerShell 7 selected:

```powershell
./infrastructure/scripts/migrate-db.ps1 -Action migrate -Database bliss
./infrastructure/scripts/migrate-db.ps1 -Action validate -Database bliss
```

`-ApplicationDirectory` can select a complete release artifact directory instead
of the local build. The wrapper assembles its classpath and propagates the Java
exit status. It supplies no secrets or extra migration options.

On Linux/macOS, from `services/api`, invoke the same shipped entry point:

```sh
java -cp 'target/quarkus-app/app/*:target/quarkus-app/lib/main/*:target/quarkus-app/lib/boot/*' com.bliss.operations.DatabaseMigration migrate --database bliss
java -cp 'target/quarkus-app/app/*:target/quarkus-app/lib/main/*:target/quarkus-app/lib/boot/*' com.bliss.operations.DatabaseMigration validate --database bliss
```

The classpath separator is `;` on Windows. Linux/macOS command execution still
needs its platform CI run; the Java integration fixture uses the platform's
classpath separator. These commands target an operator-selected database only
after deployment approval; this task runs exclusively against owned disposable
containers.

The only actions are `migrate` and `validate`, with exactly one `--database` name.
Migrate validates applied checksums and history before applying pending versions,
then validates the result. Validate requires a fully current schema; pending
migrations are a failure, not rollout approval. Both reject unknown newer history.
Cleaning, repair, baseline, undo, arbitrary target versions, alternate locations
and permissive history-ignore options are not exposed. Automatic baselining and
out-of-order execution are disabled. Repeat migration on a current schema is a
successful no-op. A failure can follow earlier successfully applied migrations;
do not assume the entire release is rolled back or retry with repair/clean.

| Exit | Fixed result | Deployment handling |
| --- | --- | --- |
| 0 | `bliss_migration success=true action=migrate applied=N` or `action=validate applied=0` | Continue only with other required release checks satisfied |
| 2 | `success=false stage=configuration` | Fix missing configuration or unsupported arguments |
| 3 | `success=false stage=preflight` | Verify connection, expected database, credentials, role attributes/memberships and ownership |
| 4 | `success=false stage=migration` or `stage=validation` | Keep rollout fenced; inspect restricted database/history evidence and resolve the migration or compatibility issue |

Success goes to stdout; failures go to stderr. The command suppresses Flyway's
SQL/connection-bearing logs and does not print exception messages or stack traces.
The fixed stage intentionally limits diagnostics; use approved restricted
database diagnostics separately. It does not establish privacy for the API,
database server, shell, secret provider or other deployment tools.

After migration, reapply the guarded [runtime grants](DATABASE_ROLES.md), retire
the migration connection/login as appropriate, and start one API worker with
runtime credentials, startup migration disabled and read-only validation enabled.
Require readiness, owned writes and workflow recovery checks before restoring
admission. [Credential revocation](CREDENTIAL_ROTATION.md) describes the separate
pool/session concerns. A zero command exit alone does not authorize rollout.

## Executed-test scope

`DatabaseMigrationCommandIT` starts the actual shipped class in separate Java
processes using only fast-jar libraries, generated credentials and SCRAM host
authentication. It tests fresh V11 creation without demo identities/providers or
workflow rows, repeated migration, validation (through the PowerShell wrapper
on Windows) and restricted API startup/write
after disabling the migrator. A populated V10 case verifies target/role/password/
argument refusals, pending validation, checksum drift and unknown future history
without changing the recorded application/history snapshots. It then upgrades to
V11 while preserving the synthetic profile. Only the fixture reverses its own
injected history corruption; the shipped command provides no repair operation.

Reproduce from `services/api` with Java 21 and Docker:

```sh
./mvnw -B -ntp verify -Dtest=OperationsSmokeTest -Dit.test=DatabaseMigrationCommandIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. See [progress](../agent/PROGRESS.md) for actual results.
Hosted orchestration, immutable artifact distribution, managed secrets, TLS/network
adoption, populated workload rollout/rollback and operator acceptance remain open.
