# Runtime and migration database roles (#15)

The [grants policy](../../infrastructure/postgresql/grant-runtime.sql) and
`RuntimeDatabaseRolesIT` rehearse separate database ownership and runtime access
in a dedicated PostgreSQL 17.6 database. The packaged API performs actual booking
and dispute writes, checkpoint restoration and one simulated CAPTURE using only
the restricted runtime login. Its separate migration login is disabled before
either API process starts. This is a tested deployment option, not a change to
existing databases, credentials or runtime defaults.

## Role and permission contract

| Role | Responsibility |
| --- | --- |
| Database administrator | Provision the dedicated database/roles and credentials, assign schema ownership, apply/reapply the grants policy, and manage access/revocation |
| Migration role | Own `public` and migrated objects; apply reviewed Flyway migrations outside the runtime API; not a superuser, database/role creator, replication or RLS-bypass role |
| Runtime login | No role memberships, database/schema/table ownership or elevated attributes; CONNECT, schema USAGE and application-table SELECT/INSERT/UPDATE/DELETE; SELECT only on Flyway history |

The runtime can use sequences with USAGE/SELECT if future migrations introduce
them, without sequence UPDATE or ownership. Current entities use UUIDs. The
policy removes direct runtime and PUBLIC table/sequence privileges before
granting the stated rights, and removes PUBLIC CREATE on `public` plus database
CREATE/TEMPORARY. It grants no TRUNCATE, REFERENCES, TRIGGER, schema CREATE,
role-management authority or migration-history writes.

This affects PUBLIC permissions for every user of the selected database. Use a
dedicated Bliss database only; do not apply it to a shared application database.
The policy verifies the connected database name, distinct roles, runtime login
attributes/no memberships/no ownership, migration-role attributes and exact
schema ownership. It requires an existing Flyway history table. Invalid input
raises a deliberate SQL error before grants, giving `psql` exit status 3 through
ON_ERROR_STOP. PostgreSQL 17 ignores an exit-code argument to `\quit`; the policy
does not rely on that form. Missing required variables also fail before grants.

Runtime still has broad application DML, including private records, workflow
state and the simulated ledger. HTTP role/ownership checks remain essential.
This does not establish row-level tenant isolation, protect data from a
compromised application login, or cover other schemas, extensions, custom
functions, networking or backup roles. New role memberships or grants can change
the boundary; review them before deployment.

## Deployment sequence to adopt after operator review

1. Provision a dedicated database, separate restricted logins and credentials
   through the approved identity/secret process. The runtime must not own the
   database, schema or tables, and must have no role memberships. Assign `public`
   and migrated objects to the migration role. Grant the migration connection
   access it needs. No passwords or role-creation secrets are supplied by this
   repository policy.
2. Fence application workers and apply the reviewed migrations through the
   migration connection. Runtime is not the migration runner. The
   [standalone migration command](DATABASE_MIGRATION_COMMAND.md) uses the pinned
   Flyway API and bundled versioned SQL without starting the API. Hosted release
   orchestration and operator adoption still need implementation and approval.
3. From the repository root, as the database administrator, apply the policy
   using the matching `psql` client, its existing authenticated connection and
   explicit target variables:

   ```sh
   psql -X --dbname=bliss --set=database=bliss --set=runtime_role=bliss_runtime --set=migration_role=bliss_migrator --file=infrastructure/postgresql/grant-runtime.sql
   ```

   Select the host/administrator connection using the approved configuration
   before running this command. `--dbname` selects the database; the `database`
   variable independently guards that selection. Do not put passwords in command arguments,
   committed files or logs. The test uses disposable generated credentials and
   executes this exact policy inside its own container.
4. Supply only the runtime database credentials to the packaged API through its
   existing `BLISS_DB_URL`, `BLISS_DB_USER` and `BLISS_DB_PASSWORD` configuration.
   From `services/api`, select the tested startup options explicitly:

   ```sh
   java -Dquarkus.flyway.migrate-at-start=false -Dquarkus.flyway.validate-at-start=true -jar target/quarkus-app/quarkus-run.jar
   ```

   Keep the required issuer configuration and one-worker boundary. Read-only
   Flyway validation and ORM schema validation must pass before readiness. The
   current repository default still migrates at startup for its existing local
   flows; this option must be selected deliberately for the separated setup.
   Do not solve a migration/permission failure by giving the runtime ownership.
5. Reapply the policy after each migration, then verify permissions, readiness,
   application writes and workflow restoration before enabling traffic. The
   policy does not install default privileges for future objects; newly created
   tables do not automatically receive runtime access. Reapplying is tested.

These are adoption steps for an operator to review, not commands executed against
an existing project database by this task. Hosted secret storage, migration job
orchestration, release/rollback rehearsal, private networking and operator
acceptance remain open. The separate
[credential rotation rehearsal](CREDENTIAL_ROTATION.md) now tests password changes,
NOLOGIN, explicit runtime session termination and a controlled workflow restart
with a replacement secret. Managed credential delivery and operator adoption
still remain open. NOLOGIN prevents new connections; it does not terminate existing sessions. The test first closes the
migration connection, disables its login, verifies connection refusal and requires
zero migration sessions while the API runs. The runtime revocation rehearsal
addresses existing pools and sessions explicitly; it does not claim cluster-wide revocation
or a hosted secret-management service.

## Reproduce local verification

Use Java 21, Docker and an isolated checkout/build directory. From `services/api`:

```sh
./mvnw -B -ntp verify -Dtest=OperationsSmokeTest -Dit.test=RuntimeDatabaseRolesIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

Use `mvnw.cmd` on Windows. The two new cases accept no external database, roles
or credentials. The first migrates to V10 with the migration role, applies the
policy, proves runtime migration fails without changing V10, then upgrades to
V11 with the migrator and reapplies the policy. Repeated application succeeds;
wrong database, owner, privileged and member roles are refused without altering
the table ACL. Runtime operations fail with SQLSTATE 42501 for schema/table
creation, ALTER/DROP, temporary tables, TRUNCATE, role switching/grants/elevation
and all three Flyway-history write operations.

The second verifies these denials before and after actual packaged execution.
Server session inspection confirms runtime connections and zero migration
connections. HTTP creates identities/provider/offering, authorizes and advances
a booking, opens a dispute and persists its evidence-wait checkpoints. A distinct
API process retains the same dispute instance, wait age and evidence; provider
evidence and an independent admin decision resolve with one AUTHORIZE and one
CAPTURE. Decision replay remains processed. The test-only process launcher gains
an explicit credentials overload; other packaged drills keep their prior startup
contract. Test roles/passwords disappear with the containers; no shared roles or
secrets are changed. See [progress](../agent/PROGRESS.md) for executed results and
[readiness](READINESS.md) for the deferred CI/native and production gates.
