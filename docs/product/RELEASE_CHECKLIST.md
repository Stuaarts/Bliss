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
| Backend quality gates | 63 scenarios: 55 ordinary + 8 migration/packaged-process scenarios |
| Flutter quality gates | Analysis, formatting, 46 tests; release web build and Android app/test-driver compilation are checked for each PR |
| Operations and docs | Swagger/OpenAPI, health/metrics, action catalog, ADRs, workflow diagrams and setup/demo instructions |
| Native device validation | Real Android Keycloak login/recovery/logout, request-to-review, direct cancellation, customer/provider/admin dispute and catalog outage/retry pass. **Open #7:** iOS verification; other settlement branches retain API/web evidence |
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

On September 27, the documented Windows backend helper started Docker and the
API against the existing V10 database. PostgreSQL and Keycloak were healthy;
readiness was UP; Swagger, metrics, Dev UI and OpenAPI returned HTTP 200. The
OpenAPI document included booking and dispute routes, and startup warmed both
workflow definitions. Local documentation checks passed all 68 file links and
the tracked-file hygiene scan. No additional UI run is claimed for this docs update.

## Remaining boundaries

Docker's ordinary Windows restart issue (#17) is mitigated by a guarded helper
that preserves stale runtime directories; no volume reset is needed. Flow's
release-specific JPA adapter remains until verified upstream fixes (#22).
Run one API worker. Multi-replica ownership, operator recovery tooling, production
rate limits, retention/moderation and backup-restore operations remain future work.

No live payment gateway, government-ID verification, public hosting, license grant
or approved commercial cancellation/refund policy is included. The configurable
local timing rules are demo policy. Product-beta decisions remain #13–#16, as the
master prompt requests. See [security](../architecture/SECURITY.md),
[payment boundary](../architecture/ADR-004-payment-abstraction.md) and
[pending decisions](../agent/DECISIONS_PENDING.md).
