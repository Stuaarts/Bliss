# Bounded authenticated booking workload (#15)

`AuthenticatedWorkloadIT` exercises contention and recovery on the actual packaged
production API with one worker, an owned PostgreSQL 17.6 container and ephemeral
loopback JWKS. Eight synthetic customers share one provider/offering. Four client
threads submit bounded batches. No external target, real credentials or persistent
project volume can be supplied to this scenario.

This extends the read-only category smoke with authenticated writes, retries,
cross-account reads, multiple durable human waits and a forced API restart. It is
a small correctness workload, not production sizing, a saturation/soak test or
an approved service objective.

## Reproduce

Use Java 21 and Docker in an isolated checkout with its own build directory.
From `services/api`:

```sh
./mvnw -B -ntp verify -Dtest=OperationsSmokeTest -Dit.test=AuthenticatedWorkloadIT -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

On Windows use `mvnw.cmd`. Do not run dev mode in that build directory or set
datasource overrides to a shared database. The packaged helper supplies explicit
properties for its own test database and issuer. It starts one API on a random
loopback port, kills only that owned process, and restarts it against the same
database. Test resources are cleaned up on success/failure; existing services,
volumes and upstream sources remain untouched. The fixture is test-only and is
excluded from the application artifact. The normal full Maven verify discovers it.

## Workload and correctness checks

| Phase | Fixed work | Required result |
| --- | --- | --- |
| Setup | 11 sequential API calls: provider/customer bootstrap, provider profile and offering | Nine 200 and two 201 responses; one fixed-price CAD offering |
| Create | 16 calls through four threads; two identical requests/keys per customer | Eight 201 and eight 200 responses; each pair returns one booking ID; eight distinct bookings |
| Wait and crash | Poll all eight bookings; wait for persisted loop checkpoints; force-stop the API | Eight WAIT_PROVIDER states and distinct workflow IDs; eight workflow rows, 24 task checkpoints, eight started intents and eight AUTHORIZE entries survive the crash |
| Restore | Restart one API against the same database and poll all waits | New process ID; the same eight booking/workflow correlations in WAIT_PROVIDER |
| Scoped reads | 24 calls through four threads: customer owner, provider owner and another customer for each booking | 16 correct 200 reads; eight 404 cross-account denials |
| Cancel | 16 calls through four threads; duplicate cancellation key per booking | 16 accepted 202 receipts correlated to the expected booking/event; eight bookings reach CANCELLED |
| Action replay | Eight calls through four threads after cancellation | Same eight processed event IDs; no new action rows |
| Settlement reads | Eight sequential participant reads | VOIDED, simulated, CAD 80.50 and exactly AUTHORIZE then VOID for each booking |
| Creation replay | Eight sequential original booking-key replays after settlement | Same cancelled booking IDs; no new bookings or ledger entries |

The database is fresh and exclusive to this test, so aggregate SQL assertions
can require exactly eight bookings/payments/events, 16 payment transactions and
24 history rows. Per-booking groups must contain one AUTHORIZE, one VOID and one
each of CREATED/WAIT_PROVIDER/CANCELLED. No CAPTURE, REFUND, duplicate settlement
or extra transition can satisfy those assertions. All SQL is read-only evidence;
the HTTP API creates and changes the domain records.

There are 91 fixed recorded API requests plus a variable number of status polls.
Startup/operator probes, JWKS fetches, SQL checks and Docker/JVM operations are
not part of that count. Every recorded response must have a distinct valid
server-generated request ID. A batch gate releases submitted work and the client
pool has four threads; this bounds concurrency, without imposing an arrival rate.
The wait phases, sequential setup/settlement/replay and batched contention are
reported separately rather than combined into a throughput figure.

Each HTTP request has a five-second timeout. Batches and each status/checkpoint
wait have 60-second failure bounds; the complete test has a five-minute JUnit
timeout. The existing packaged startup helper allows 45 seconds per start. These
are test guardrails, not production timeout settings or recovery objectives.

## Measurements and limits

The test prints count, actual HTTP status distribution and nearest-rank p50/p95/max
elapsed milliseconds per phase. Timing begins immediately before HTTP send and
ends after status/correlation/JSON validation. It includes server token validation,
normal INFO response logging and client validation, while excluding client token
signing, payload construction, executor queue delay and phase waits. Fresh
ephemeral JWTs are generated before each call; this is local signature/JWKS
verification, not identity-provider login/token-service performance. There is no
warmup, latency threshold, request-rate target or production capacity claim.

See [progress](../agent/PROGRESS.md) for the executed runs and observations. Small
sample p95 values can equal max and are not stable percentile estimates. Poll
counts/timings vary with scheduler and workflow recovery. The existing
[REST diagnostic timing](REQUEST_DIAGNOSTICS.md) has a different scope from these
client measurements; do not compare them as equivalent latency metrics.

This scenario covers direct booking and cancellation with simulated AUTHORIZE/
VOID. Request/quote flows, scheduling/completion/capture, chat, review and dispute
have separate regression/demo evidence; they are not under this mixed workload.
Eight restored waits do not establish long-duration reliability, multi-replica
ownership, database outage settlement, real payment safety, storage growth or
recovery from backup. Native/browser identity flows are also separate.

Before product beta, agree representative actor/endpoint mixes, dataset sizes,
arrival/burst rates, observation periods, resource budgets and SLOs with the owner.
Exercise write/ledger contention, long waits, dependency faults and recovery at
those scales; collect pool/JVM/workflow backlog evidence and rehearse alerts.
Those acceptance items remain open under [#15](https://github.com/Stuaarts/Bliss/issues/15)
and the [readiness plan](READINESS.md). Do not add replicas to resolve saturation
until distributed workflow ownership is designed and verified. The deferred
CI/native release gate still applies.
