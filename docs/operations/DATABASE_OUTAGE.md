# Database outage and readiness drill (#15)

`DatabaseOutageIT` exercises a temporary PostgreSQL outage against the actual
packaged production API. It creates a dedicated PostgreSQL 17.6 container, an
ephemeral loopback JWKS fixture and an API process on a random loopback port.
It accepts no external target or credentials. Existing local services and volumes
are not part of this drill.

## Reproduce safely

Use Java 21 and Docker from an isolated checkout with its own API build directory.
Do not supply a shared datasource or start dev mode in the same build directory.
From `services/api`, run:

```sh
./mvnw -B -ntp verify -Dtest=LocalManagementTest -Dit.test=DatabaseOutageIT -Dquarkus.http.test-port=0
```

On Windows use `mvnw.cmd`. This command builds the application, checks local
management access, then runs the packaged outage scenario. Full regression is:

```sh
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

The test creates a unique ownership label and checks that label and the
Testcontainers label before each Docker stop/start command. Only the container
ID returned by its own PostgreSQL resource is eligible. A fixed random loopback
port binding preserves the JDBC address across the container restart. Resource
cleanup removes this temporary database and stops its API/JWKS processes even
when an assertion fails; no existing database or persistent project volume is reset.

## What the scenario proves

1. Authenticated production liveness and readiness initially return 200/UP.
   A synthetic provider account/profile is created and read through the API.
2. Docker stops only the owned PostgreSQL container. The test requires an actual
   readiness response of 503/DOWN containing a DOWN database health check;
   an HTTP timeout does not count as outage detection.
3. Liveness remains 200/UP, metrics remain accessible to the operator, and the
   API process remains alive with the same process ID. Anonymous readiness stays
   401 and a marketplace customer stays 403 during the outage.
4. Docker starts the same owned container. Readiness must return 200/UP, without
   restarting the API. The same provider record, including its random ID and
   version, remains readable. An authenticated update advances its version and
   remains readable; public categories again return the nine entries.

Readiness polling has a 60-second deadline per transition and each HTTP request
has a 12-second timeout. These are test failure bounds, not production probe
settings or service objectives. The test prints detection and recovery elapsed
milliseconds; detection includes the Docker stop command and recovery includes
the start command and database/API recovery. See [progress](../agent/PROGRESS.md)
for actual results. There is no alert delivery or human acknowledgement timer.

The drill uses the existing datasource/health configuration without shortening
connection timeouts or disabling health checks. Quarkus's
[pinned datasource readiness implementation](https://github.com/quarkusio/quarkus/blob/3.39.0/extensions/agroal/runtime/src/main/java/io/quarkus/agroal/runtime/health/DataSourceHealthCheck.java)
checks the pool and reports database failures as DOWN. Liveness describes the
API process; an UP liveness result does not establish database availability or
successful marketplace work.

## Operator response boundary

Use the [management access contract](MANAGEMENT_ACCESS.md) to authenticate probes.
Treat 401/403 as a credential/role failure, not as a database health response.
Read the authorized readiness payload and compare liveness, dependency status
and restricted logs. Health diagnostics may contain connection details; retain
operator access controls and keep them out of public incident updates.

When readiness is DOWN but liveness remains UP, remove the instance from new
traffic through the eventual approved ingress mechanism and investigate database
availability/connectivity. Do not put database availability into liveness or
automatically restart a live API because its database is unavailable. Repeated
API restarts do not repair a failed database. The test's Docker stop/start commands
are fault injection for a disposable database, not an instruction to restart a
shared or managed database.

Restore the dependency through its approved operator procedure. Confirm readiness
UP, controlled reads, required writes and booking/ledger/workflow reconciliation
before restoring traffic. Do not replay settlement actions or edit workflow rows
to force recovery. Define dispatch suspension, ingress control, escalation,
reconciliation and notification procedures for the chosen hosted runtime before
using this runbook operationally.

This bounded drill covers a stopped database container and connection-pool
recovery. It does not prove network-partition or hung-query behavior, database
failover, backup restoration, data-loss recovery, active-workflow settlement,
identity-provider outage, managed ingress, alerts or production recovery targets.
No live Keycloak browser or real operator credential is used. The API has no
active booking/dispute in this scenario; existing packaged-process restart tests
separately exercise workflow restoration. Broader recovery and incident acceptance
remain open in [#15](https://github.com/Stuaarts/Bliss/issues/15) and the
[readiness plan](READINESS.md).
