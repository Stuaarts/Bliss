# Operations readiness plan (#15)

This is a local portfolio system and a plan for a later product beta. It does
not authorize public hosting, paid infrastructure or real payments. The API is
pinned to Quarkus 3.39.0 and Flow 1.2.0.CR1. Production acceptance below remains
open until implementation and evidence exist; proposed owners are roles to
assign, not staffed services. Track delivery in [issue #15](https://github.com/Stuaarts/Bliss/issues/15).

## Current evidence and limits

| Area | Implemented foundation | Evidence still required before product beta |
| --- | --- | --- |
| Deployment | Loopback API, PostgreSQL/Flyway, one API worker, durable BookingLifecycleFlow and DisputeFlow | Private database networking, TLS, release/rollback rehearsal, health-based deployment, resource sizing and assigned operator |
| Identity and secrets | One OIDC issuer, audience/subject and server-side role/ownership checks; explicit packaged DB and issuer configuration | Managed identity availability, least-privilege runtime/migration credentials, secret storage, rotation and revocation drill |
| Diagnostics | JSON console logging; matched REST response events with generated IDs and operation names; mapped failures omit throwables; Flow payload tracing disabled; operator-gated production management | Private management ingress, credential operations, collection/dashboards/alerts, remaining category access/retention/redaction review and end-to-end tracing |
| Recovery | Packaged-process crash/restart scenarios; isolated database outage/readiness recovery; migrations and relational constraints tested | Managed backup scheduling, encrypted storage, access/retention, restore of a real paused workflow, agreed RPO/RTO and operator rehearsal |
| Capacity | Bounded public category reads; eight-customer authenticated direct-booking/retry workload with four clients, eight restored waits and single-settlement checks | Broader endpoint/actor mixes, agreed dataset/arrival rates, long waits, saturation/soak and resource/backlog evidence against approved SLOs |
| Abuse and moderation | Authentication, participant/owner checks, validation and bounded pagination | Enforced rate limits, reporting/review/removal workflow, escalation/appeal policy and tested abuse controls |
| Privacy | Scoped private reads; public review DTO excludes customer/booking identity | Approved inventory/retention rules, authenticated export/deletion, backup expiry and deletion/hold rehearsal |

The separate [database recovery proposal, PR #40](https://github.com/Stuaarts/Bliss/pull/40)
has a local synthetic dump/restore drill. Its structural/row/constraint checks do
not execute the restored Flow payload or establish a managed backup service.
It remains unmerged pending CI. Do not replace its evidence with the HTTP smoke.

## Reproduce the bounded operations smoke

Use Java 21, Docker and a checkout with its own API build directory. Stop dev mode
in that checkout first. From `services/api` run:

```sh
./mvnw -B -ntp test -Dtest=OperationsSmokeTest -Dquarkus.http.test-port=0
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

On Windows use `mvnw.cmd`. With no datasource override, Quarkus creates a
disposable PostgreSQL 17.6 Dev Service; Testcontainers removes its resources on
JVM exit. Never set datasource overrides to a shared/demo or production database.
The tests apply the test/demo migrations. Packaged restart/migration scenarios
create their own isolated PostgreSQL containers. Existing volumes are not reset.

`OperationsSmokeTest` verifies five operational boundaries:

1. Liveness and readiness return UP and OpenAPI includes the dispute route in
   the test profile. This smoke does not cover dependency outages; the separate
   [packaged database outage drill](DATABASE_OUTAGE.md) checks readiness transitions.
2. Invalid catalog pagination returns a generic problem with the same generated
   UUID in its header/body, without echoing the supplied correlation value or
   exception class.
3. A test-only endpoint with an invalid return value returns a generic correlated
   500 rather than classifying the broken server contract as bad client input.
4. An actual successful category request appears in the Prometheus HTTP registry.
5. Eighty read-only category requests through four client threads each return
   the nine reference categories and a distinct server UUID. Each request has a
   five-second deadline and the batch a thirty-second deadline. The client/pool
   close after the bounded workload. No remote target can be supplied.

The test prints nearest-rank p50/p95/max elapsed milliseconds. Timings include
client HTTP and response validation, without a warmup phase; they are observations
on this workstation, not latency thresholds, capacity sizing or production SLOs.
The test identity resource serves an ephemeral signing key; this does not test
Keycloak browser login. Workflow dispatch is disabled in the test profile. Full
Maven verify separately exercises packaged workflow recovery. See the dated
[progress entry](../agent/PROGRESS.md) for measured results.

The separate [authenticated booking workload](AUTHENTICATED_WORKLOAD.md) adds
four-client write/retry contention, customer/provider/outsider reads, eight
simultaneous durable waits through a process crash, cancellation replay and
per-booking simulated ledger/history assertions. Its bounded phase measurements
are correctness observations; broader workload, resource and SLO acceptance
remain open.

The endpoint-validation mapper replaces the default validation report with the
existing Bliss problem format. It targets Quarkus's endpoint violation type and
preserves 500 for return-value violations. This type is part of the pinned runtime;
rerun both regression cases on a Quarkus upgrade. See Quarkus's
[validation exception guidance](https://quarkus.io/guides/validation/).

## Deployment, diagnostics and incident handling

Run one API worker until a separate distributed ownership/lease design passes
concurrent restart/duplicate settlement tests. Adding replicas for throughput
would not establish safe workflow ownership. Keep the
[Flow compatibility adapter](../architecture/WORKFLOWS.md) tracked under #22.

Before hosting, the infrastructure operator must choose a region and runtime
after owner approval, isolate PostgreSQL from public ingress, require TLS for
clients/identity, and use separate application/migration access. Validate clean
install and populated upgrade before routing traffic. A failed migration must
stop rollout; never assume an older binary can read a changed schema. Rehearse
rollback against the proposed schema and use a verified restore if incompatible.
Do not run local/demo seeds or reuse generated demo credentials in a hosted tier.

Health, metrics, Swagger and OpenAPI share the HTTP server. The default packaged
production profile now requires the dedicated `operator` OIDC role on the
non-application root and all children; marketplace roles do not imply access.
The local demo keeps its tools on loopback. See [management access](MANAGEMENT_ACCESS.md)
for the access matrix, authenticated probes, profile boundaries and tests.
Before public ingress, configure private management access, TLS and credential
operations. Probe liveness for process health and readiness for accepting traffic;
record dependency/credential outage and recovery behavior. No separate management
listener or hosted network is implemented here. The [database outage drill](DATABASE_OUTAGE.md)
verifies database readiness DOWN/UP while liveness remains UP, preserves the API
process, and checks saved records and a successful update after recovery.
Background workflow backlog/age is not proven by an UP health result.

The operations owner should implement private metric collection and dashboards
for route-template/status request counts, error rate, latency, JVM/DB saturation
and durable workflow wait/retry failures. Do not label metrics with user, booking,
message, request ID or raw URL values. Propose alert thresholds from representative
measurements, then rehearse an alert through acknowledgement and recovery. These
dashboards, alerts and custom workflow gauges do not exist yet.

REST correlation is generated server-side. [REST response diagnostics](REQUEST_DIAGNOSTICS.md)
now record the ID, allowlisted method, code-defined operation, status and filter
elapsed time. Mapped server errors log only ID/error type; Flow's payload-bearing
trace logger is disabled. This covers matched REST processing, without request
content or thread-local MDC; it is not a universal HTTP access log or distributed
trace. OpenTelemetry, async/workflow/restart propagation and a universal redaction
pipeline remain unimplemented. Never collect authorization headers, tokens,
credentials, private messages or dispute evidence as telemetry. Other library and
dispatcher logs still need access controls and content/retention review before
external collection; the generic HTTP response does not prove all-log privacy.

Incident runbook acceptance: assign an operator and backup, record notification
and escalation routes, distinguish identity/database/API failures, disable ingress
or dispatch safely when necessary, preserve restricted diagnostic evidence and
reconcile booking/ledger/workflow state before resuming. Never retry settlements
by editing rows manually. The automated database outage drill measures detection
and recovery locally, without active workflows. Hosted ingress/dispatch controls,
alert delivery, human acknowledgement, escalation and settlement reconciliation
still need a supervised rehearsal before declaring the runbook operational.

## Abuse and moderation work

Engineering and the product owner must agree limits for public discovery,
authenticated writes, conversations and dispute evidence, including payload size,
burst/sustained windows, shared-network users and retry behavior. Implement and
test enforcement before hosting; pagination/idempotency are not rate limiting.
Acceptance includes 429 with a documented retry contract, bounds on storage/work,
isolation between actors and normal recovery after the window. No threshold is
approved and no rate-limit middleware is added here.

Define reportable content, reporter privacy, independent moderation permissions,
audit events, escalation, user notification and appeals before building queues.
Exercise public review/provider-content removal and private chat/evidence handling
without granting moderators unnecessary participant access. A dispute admin's
existing settlement authority does not provide a content moderation system.
Reports, moderation queues and removal actions remain unimplemented.

## Data inventory and lifecycle work

| Data class | Present use / access boundary | Policy and implementation to agree |
| --- | --- | --- |
| Identity/profile/provider | OIDC subject, user profile and provider ownership; selected provider/catalog fields public | Identity-provider responsibilities, account closure and published provider attribution |
| Requests, quotes, bookings | Customer/provider scope and price/schedule snapshots, workflow correlation/history | Participant export, retention after completion/cancellation and records required for reconciliation |
| Conversations and dispute evidence | Private participant text; independent admin dispute access | Retention windows, report/hold process, export of third-party text and controlled deletion |
| Simulated payments and workflow state | Ledger idempotency/history and persisted human waits | Audit/reconciliation retention; never delete active checkpoints or unique settlement guards casually |
| Public reviews | Public rating/text and privacy-limited author presentation | Moderation/removal, account deletion effect and aggregate recalculation |
| Backups and telemetry | Future managed copies; current console diagnostics/local synthetic recovery artifacts | Encryption/access, expiry, restore restrictions, identifiers/redaction and deletion propagation |

The product owner must approve retention periods, launch geography, legal terms
and exceptions in [#13](https://github.com/Stuaarts/Bliss/issues/13). Until then,
do not invent retention periods or run automatic purges. Engineering should map
every table and identity-provider record to this inventory, implement authenticated
export and deletion workflows with reauthentication, ownership and audit, and test
cross-account denial. Resolve jointly owned conversations/evidence, active bookings,
holds and ledger referential integrity before deleting data. Demonstrate deletion
from active stores and expiry/restrictions in backup restore; document what cannot
be erased and why after owner review. No export/deletion endpoint is implemented.

## Delivery order and final release gate

Continue independent local work while GitHub account jobs cannot start, as the
user requested on October 10. Preserve drafts [#39](https://github.com/Stuaarts/Bliss/pull/39)
(secondary iOS) and [#40](https://github.com/Stuaarts/Bliss/pull/40) (recovery).
The management access slice, [draft #42](https://github.com/Stuaarts/Bliss/pull/42),
builds on [draft #41](https://github.com/Stuaarts/Bliss/pull/41) (readiness and
correlation). The database outage slice builds on #42; none of these local tests
waive required CI gates.
The [REST diagnostic slice](REQUEST_DIAGNOSTICS.md) builds on draft #43 and the
[authenticated workload](AUTHENTICATED_WORKLOAD.md) builds on draft #44. Private
management networking, broader telemetry review and workload/resource acceptance
remain open. Managed infrastructure,
retention/moderation policy and commercial payments need owner decisions first.

The final gate remains: account owner resolves the GitHub payment/spending-limit
condition, required checks pass on each exact current PR head, native iOS secondary
scenarios execute on macOS, branches are current with main, and main checks pass
after green merges. Do not merge drafts or mark #7/#12/#15 complete on local
evidence alone. Product-beta sign-off additionally needs the production acceptance
in this plan, license/product decisions, and any explicitly approved infrastructure.
