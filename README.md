# Bliss V2

A local-services marketplace connecting customers with individual professionals
and companies. Rebuilding the preserved prototype as a Java 21 / Quarkus modular
monolith with PostgreSQL and a Flutter client.

**Status: foundation under construction, not a completed portfolio release.**

Implemented: public category/provider/offering discovery backed by Flyway and
PostgreSQL, a Flutter HTTP catalog client, bounded pagination, health/metrics,
OpenAPI, tests and GitHub Actions. Authentication, booking and durable workflows
are upcoming slices tracked in GitHub. The Flow dependency is pinned; no booking
workflow is registered yet.

## Run locally

Requirements: Java 21, Docker with a working Linux engine, and Flutter 3.47.5.
Maven 3.9.11 is supplied by the wrapper. PostgreSQL 17.6 is pinned in Compose.
Quarkus core 3.39.0 and Flow 1.2.0.CR1 match the inspected upstream version;
the release-candidate dependency will be reevaluated before product beta.

1. Copy `.env.example` to `.env` and replace its password placeholder.
2. On Windows, run:

   ```powershell
   ./infrastructure/scripts/dev-api.ps1 -JavaHome 'C:\Program Files\Java\jdk-21'
   ```

   On macOS/Linux, export the three `BLISS_DB_*` values from your local `.env`,
   run `docker compose up -d --wait`, then:

   ```sh
   cd services/api
   ./mvnw clean quarkus:dev -Dquarkus.profile=local
   ```

3. In another terminal:

   ```sh
   cd apps/mobile
   flutter pub get
   flutter run -d chrome --web-port 3000 --dart-define=API_BASE_URL=http://localhost:8088
   ```

The `local` profile loads explicitly marked sample offerings. Production uses
only reference categories; never reuse the local-demo database for production.
On this workstation the ignored SDK is `.tools/flutter/bin/flutter.bat`.
For Android emulator networking, see [mobile setup](apps/mobile/README.md).

- API: [categories](http://localhost:8088/api/v1/categories), [offerings](http://localhost:8088/api/v1/offerings)
- Operations: [readiness](http://localhost:8088/q/health/ready), [metrics](http://localhost:8088/q/metrics)
- Documentation: [Swagger](http://localhost:8088/q/swagger-ui), [Dev UI](http://localhost:8088/q/dev-ui)

Stop infrastructure with `docker compose stop`; data stays in the named volume.
No destructive reset is required for normal development.

## Verify

```sh
cd services/api
./mvnw verify
./mvnw spotless:apply  # only when formatting Java changes
cd ../../apps/mobile
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```

Tests use a real PostgreSQL Dev Service when Docker is available. To use an
existing **dedicated test database**, supply `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME` and `QUARKUS_DATASOURCE_PASSWORD`. Tests apply demo
migrations; do not point them at production or another application's database.
CI provisions an ephemeral PostgreSQL service and runs the same checks.

Docker Desktop startup was repaired by preserving its stale runtime socket folder
and allowing Docker to recreate it. Local PostgreSQL, API readiness, seven backend
tests and live Flutter catalog browsing have now been verified. Bliss defaults to
database port 5433 and API port 8088 to coexist with other local projects.
See [progress](docs/agent/PROGRESS.md) for evidence.

## Structure

`apps/mobile` contains the Flutter client. `services/api` contains the Quarkus
modular monolith organized by capability. `infrastructure` contains local startup
scripts. `docs` records product scope, architecture decisions and execution state.

The core process will be request → quote → booking → service → confirmation or
dispute → simulated settlement. Standard offerings will also support direct booking.
Quarkus Flow coordinates the long waits, timeouts, retries and payment compensation;
the relational domain remains authoritative for API reads.

- [Reconnaissance](docs/agent/RECONNAISSANCE.md)
- [Execution plan](docs/agent/PLAN.md)
- [Progress and validation](docs/agent/PROGRESS.md)
- [GitHub backlog](https://github.com/Stuaarts/Bliss/issues)

The original `Bliss-*` folders and `quarkus-flow` are preserved local references,
excluded from this repository. The upstream checkout must not be edited.

License: not yet selected. No license grant is implied; resolve before public release.
