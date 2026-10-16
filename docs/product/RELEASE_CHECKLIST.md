# Portfolio release evidence

The local **web** marketplace and **Android marketplace** flows are demonstrated.
This is not a signed mobile distribution or production release. The walkthrough
is documented, with genuine screenshots from
the separate verified runs; no narrated video is claimed.

| Gate | Evidence / current result |
| --- | --- |
| Repository and CI | Private monorepo; completed work committed/pushed; Java and Flutter CI |
| Java 21 / Quarkus build | Full Maven verify on clean CI checkouts; pinned dependency versions |
| PostgreSQL / Flyway / ORM | V1–V10, empty and populated V5/V8/V9 upgrades, Hibernate validation; local V10 readiness UP |
| Identity and ownership | Real Keycloak PKCE browser login, server-side role/owner checks and negative tests |
| Catalog and providers | Provider onboarding, owned publishing/edit/archive and public discovery |
| Request / quote / direct booking | Browser evidence plus concurrent acceptance, price snapshot and duplicate tests |
| Durable orchestration | BookingLifecycleFlow and DisputeFlow; forced packaged-process restart tests retain instance identity |
| Simulated payments | AUTHORIZE/CAPTURE/VOID/REFUND, unique ledger, transient rollback and exhausted-retry tests |
| Flutter connected to API | Customer/provider/admin web flows and native Android primary flow, direct cancellation, admin dispute and catalog recovery against actual local services |
| Chat | Persisted participant-scoped messages, retry/pagination, browser restart evidence |
| Reviews | Completed-customer eligibility, one review per booking, public rating/privacy tests |
| Disputes | Private evidence, independent admin decision, manual review wait and simulated refund verified |
| Backend quality gates | Merged foundation: 63 scenarios. Dispute-failure metrics branch locally passes 82: 65 ordinary + 17 migration/packaged-process scenarios; remote CI remains pending |
| Flutter quality gates | Analysis, formatting, 46 tests; release web build and Android app/test-driver compilation are checked for each PR |
| Operations and docs | Production health/metrics/OpenAPI/Swagger require an independent operator role; local tools remain available. See [management access](../operations/MANAGEMENT_ACCESS.md), action catalog, ADRs, workflow diagrams and [operations readiness plan](../operations/READINESS.md) for remaining hosting/privacy/abuse controls |
| Native device validation | Real Android Keycloak login/recovery/logout, request-to-review, direct cancellation, customer/provider/admin dispute and catalog outage/retry pass. iOS app build, native session fixture and real customer/provider Keycloak browser login/recovery/logout/account switching pass. iOS request-to-review/chat/simulated capture/review recovery also pass. **Open #7:** secondary iOS cancellation/dispute/connection-recovery acceptance; other settlement branches retain API/web evidence |
| Full portfolio sign-off | **Open #12:** retain until the remaining native platform gate is resolved or an explicit web-only scope is agreed |

## Evidence index

- [Marketplace browser run](../demo/MARKETPLACE.md): quote booking, direct cancellation,
  provider timeout, completed capture and phone layout.
- [Chat](../demo/CHAT.md), [reviews](../demo/REVIEWS.md) and
  [disputes](../demo/DISPUTES.md): observed UI behavior, screenshots and limits.
- [Android authentication](../demo/ANDROID.md): real Keycloak browser callback,
  process-death recovery, logout/account switching, screenshots and reproducible
  runner; also includes the three-process synthetic session fixture and remaining
  iOS acceptance steps.
- [Android marketplace](../demo/ANDROID_MARKETPLACE.md): UI-created request,
  quote, real scheduling wait, completed simulated capture, two-way chat, saved
  review reopening, process recovery and background/resume; reproducible driver
  and genuine screenshots. The full live run passed in 375.264 seconds.
- [Android secondary flows](../demo/ANDROID_SECONDARY.md): two UI-created direct
  bookings, cancellation/void, actual scheduling, private evidence, independent
  admin decision, persisted customer result and catalog outage/retry. The fresh
  full run passed in 303.93 seconds; authentication was rechecked separately.
- [Database matrix](../architecture/DATABASE_INVARIANTS.md): constraints, migration
  preservation, invalid-data rollback, preflight and operator recovery.
- [iOS simulator](../demo/IOS.md): normal app compilation, built callback scheme
  and real Keychain/AppAuth refresh/rotation/local logout across three processes.
  Synthetic fixture execution is distinct from real browser/marketplace acceptance.
- [iOS browser authentication](../demo/IOS_AUTH.md): real Keycloak forms/callback,
  customer/provider refresh recovery, customer background/resume, identity-provider
  logout and account switching; full UI scenario passed in 92.921 seconds.
- [Workflow design](../architecture/WORKFLOWS.md): durable waits, local JPA adapter,
  admission/processing boundaries and forced-restart verification.
- Dispute implementation: [PR #28](https://github.com/Stuaarts/Bliss/pull/28), merged
  as `2cab47e`; database hardening: [PR #29](https://github.com/Stuaarts/Bliss/pull/29),
  merged as `41e564f`.
- Final code checks at `c95ea84`: [push CI](https://github.com/Stuaarts/Bliss/actions/runs/36286965393)
  and [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/36286967602), all API/mobile
  jobs passed. Later documentation-only updates have their own CI history.
- Native session ordering and Android fixture verification: [PR #31](https://github.com/Stuaarts/Bliss/pull/31),
  merged as `ba1699e`. All four jobs passed at `4a19f98` in
  [push CI](https://github.com/Stuaarts/Bliss/actions/runs/36327118198) and
  [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/36327121173): 63 backend
  scenarios and 46 ordinary Flutter tests, analysis/formatting/web build. The three
  native fixture phases are verified locally, separately from CI.
- Real Android authentication: [PR #32](https://github.com/Stuaarts/Bliss/pull/32),
  merged as `a892dd9`. All six API/mobile/Android jobs passed at `aae6927` in
  [push CI](https://github.com/Stuaarts/Bliss/actions/runs/36346822438) and
  [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/36346825784).
  [Main CI](https://github.com/Stuaarts/Bliss/actions/runs/36347290131) also passed.
  Android CI compiles the app/driver; real Keycloak execution is verified locally.
- Native Android request-to-review evidence: [PR #33](https://github.com/Stuaarts/Bliss/pull/33),
  merged as `0e04a80` after all six checks passed at `1f0f9ac` in
  [push CI](https://github.com/Stuaarts/Bliss/actions/runs/37397402890) and
  [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37397405582). Each API run
  passed 63 scenarios; Flutter passed 46 tests, analysis/formatting/web build;
  Android app and both instrumented scenarios compiled on clean Linux runners.

- Native Android secondary flows and workflow test timing: [PR #35](https://github.com/Stuaarts/Bliss/pull/35),
  merged as `03a5437` after all six checks passed at `c3556e3` in
  [push CI](https://github.com/Stuaarts/Bliss/actions/runs/37883184012) and
  [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37883188349). Each API run
  passed all 63 scenarios; Flutter passed 46 tests, analysis/formatting/web build;
  all native scenarios compiled with the normal Android APK. Live emulator checks
  remain separate from compilation. The user-requested branch renames preserve
  commits and tracking; CI supports the names without the codex/ prefix.

- iOS simulator build and native session fixture: [PR #36](https://github.com/Stuaarts/Bliss/pull/36),
  merged as `60b23fd` after all seven checks passed at `4da6f18` in
  [native XCTest CI](https://github.com/Stuaarts/Bliss/actions/runs/37888748649),
  [push CI](https://github.com/Stuaarts/Bliss/actions/runs/37888748623) and
  [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37888752272). The normal
  app/callback and all three synthetic Keychain/AppAuth fixture phases passed.
  Each API run passed 63 scenarios; Flutter passed 46 tests and web quality gates;
  Android app/native drivers compiled. Browser acceptance was still open at this
  checkpoint and is verified by PR #37 below; marketplace acceptance remains open.

- Real iOS browser authentication: [PR #37](https://github.com/Stuaarts/Bliss/pull/37),
  merged as `080a11f` after all eight checks passed at `5685e9e`: the
  [real browser run](https://github.com/Stuaarts/Bliss/actions/runs/37945058779),
  [synthetic native dispatch](https://github.com/Stuaarts/Bliss/actions/runs/37945150445),
  [push CI](https://github.com/Stuaarts/Bliss/actions/runs/37945058709) and
  [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37945065853). The browser
  XCTest passed in 92.921 seconds with all eight checkpoints/screenshots. Each
  API run passed 63 scenarios; Flutter passed 46 tests and web quality gates;
  Android app/native drivers compiled. #7/#12 retain iOS marketplace acceptance.

On September 27, the documented Windows backend helper started Docker and the
API against the existing V10 database. PostgreSQL and Keycloak were healthy;
readiness was UP; Swagger, metrics, Dev UI and OpenAPI returned HTTP 200. The
OpenAPI document included booking and dispute routes, and startup warmed both
workflow definitions. Local documentation checks passed all 68 file links and
the tracked-file hygiene scan. No additional UI run is claimed for this docs update.

- Real iOS request-to-review marketplace: see [reproduction and evidence](../demo/IOS_MARKETPLACE.md).
  [Marketplace CI](https://github.com/Stuaarts/Bliss/actions/runs/37955297119)
  passed at `e8c7724d0e82d21f18884393db7464f33bf4d445`: one XCTest, zero failures, 356.597 seconds,
  nine checkpoints/screenshots and all nine database assertions. The observer
  recorded a scheduled human wait with three persisted task checkpoints; the
  completed booking retained the same workflow correlation. All nine checks passed
  before [PR #38](https://github.com/Stuaarts/Bliss/pull/38) merged as `9c81af7`,
  including [real browser auth](https://github.com/Stuaarts/Bliss/actions/runs/37955297128),
  the separately dispatched [synthetic iOS gate](https://github.com/Stuaarts/Bliss/actions/runs/37955296677),
  [push CI](https://github.com/Stuaarts/Bliss/actions/runs/37955297169) and
  [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37955302085).
  Manual dispatch is outside the PR rollup; its exact head was checked separately.
  Backend CI passed 63 scenarios; Flutter passed 46 tests, analysis/formatting/web
  build; Android app and native drivers compiled.
  Secondary iOS paths, physical devices and signing remain open under #7/#12.

## Remaining boundaries

Docker's ordinary Windows restart issue (#17) is mitigated by a guarded helper
that preserves stale runtime directories; no volume reset is needed. Flow's
release-specific JPA adapter remains until verified upstream fixes (#22).
Run one API worker. Multi-replica ownership, operator recovery tooling, production
rate limits and retention/moderation remain future work. The separate local
synthetic backup/restore drill is prepared in draft [PR #40](https://github.com/Stuaarts/Bliss/pull/40);
the separate [paused workflow backup/restore drill](../operations/WORKFLOW_BACKUP_RESTORE.md)
executes one restored WAIT_PROVIDER booking through idempotent simulated
cancellation with the same workflow ID. Managed backup scheduling, broader
workflow/external-effect recovery and operator acceptance remain open.
The [operations readiness plan](../operations/READINESS.md) records concrete
evidence, gaps and delivery order under #15.
The [database outage drill](../operations/DATABASE_OUTAGE.md) verifies readiness
DOWN/UP with continued liveness, retained data and a successful update in the
same API process. Active-workflow outage reconciliation and hosted incident
response still need acceptance.
The [REST diagnostic boundary](../operations/REQUEST_DIAGNOSTICS.md) verifies
correlated metadata without private request/exception content in its events,
including packaged JSON output. Universal library log review, async tracing and
managed collection/alerts remain open.
The [aggregate workflow inbox metrics](../operations/WORKFLOW_BACKLOG_METRICS.md)
expose cached pending counts/oldest ages and collector freshness/failures through
operator-gated production metrics. Failed collection retains the last good values;
it does not report false zero backlog. Unresolved dispute-failure count/oldest-age
gauges use the saved domain marker after engine cleanup, with actual exhausted
retry/rollback evidence. Broader booking/engine faults, healthy human-wait ages,
private collection, dashboards and alert rehearsal remain open.
The [bounded authenticated workload](../operations/AUTHENTICATED_WORKLOAD.md)
checks eight customers through four clients, duplicate booking/cancellation
requests, eight durable waits surviving an API crash, ownership denial and exactly
one simulated AUTHORIZE/VOID per booking. This is local correctness evidence;
broader endpoint mixes, long waits, resource/saturation/soak and approved capacity
objectives remain open.

No live payment gateway, government-ID verification, public hosting, license grant
or approved commercial cancellation/refund policy is included. The configurable
local timing rules are demo policy. Product-beta decisions remain #13–#16, as the
master prompt requests. See [security](../architecture/SECURITY.md),
[payment boundary](../architecture/ADR-004-payment-abstraction.md) and
[pending decisions](../agent/DECISIONS_PENDING.md).

## Deferred final CI and native gate

At the user's October 10 direction, continue independent verifiable work and
resolve the external account blocker at the end. Post-merge main CI cannot start:
GitHub reports an account payment or spending-limit problem
([rejected CI](https://github.com/Stuaarts/Bliss/actions/runs/37956907374)). All nine
checks passed on PR #38's final code revision before merge; no post-merge pass
is claimed. Draft [PR #39](https://github.com/Stuaarts/Bliss/pull/39) prepares iOS
cancellation/dispute/connection recovery; its actual macOS execution remains open.
The account owner must resolve the GitHub condition, then all required checks
must pass on current heads before merge and on main afterward. Preserve drafts
#39/#40 and keep #7/#12 open until those gates are satisfied. Local operations
evidence does not waive CI, native acceptance or the product-beta controls above.
