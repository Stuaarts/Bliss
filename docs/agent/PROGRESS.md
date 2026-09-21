# Progress

## 2026-09-19 — Docker repair and authentication continuation

User authorized Docker repair/reinstallation and continuation. Reinstallation was
unnecessary: Docker Desktop was stopped; its exact runtime directory
`C:\Users\Lucas\AppData\Local\Docker\run` was renamed to
`run.before-bliss-repair-20260919-120503` in the same parent and a fresh `run` created.
This preserved both stale sockets. Engine 28.3.3 started successfully; no Docker
images, containers, volumes, VHDX or unrelated project data were deleted.
Matching upstream report: https://github.com/docker/desktop-feedback/issues/460.

Local foundation verification now passes:
- Docker info responds; Bliss Compose PostgreSQL is healthy.
- Another project's database owns 5432 and another Java process owns 8080.
  Bliss now defaults to DB 5433 and local API 8088; unrelated services untouched.
- Java 21 Maven verify: 7 tests, zero failures/errors/skips, BUILD SUCCESS.
- Clean startup applies V1/V2 and repeatable seed; readiness UP; API returns 2 offerings.
- Live Flutter web at localhost:3000 loads real API data, filters Cleaning to one
  offering and opens its details with CAD 80.00. Browser interactions verified.
- Detected stale removed V1000 SQL in target/classes from the previous incremental
  build. Startup helper now uses Maven clean. The newly initialized demo schema
  was preserved as bootstrap_before_clean; clean migrations created the replacement
  public schema. No existing user/project database was reset.

Phase 1–2 exit gate passed. Proceeding to #2: Keycloak, PKCE, validated tokens,
subject/profile mapping and ownership tests. Original session details below are
historical, including the now-resolved Docker blocker.

## 2026-09-19 — initial session

Current phase: Phases 1–2 foundation implemented; local-startup exit gate blocked
by Docker Desktop (#17). Phase 0 bootstrap completed with the missing-ZIP adaptation.

Completed: read master prompt; inspected legacy source and process diagram;
verified private empty GitHub remote/auth; inspected local Flow branch/POM/docs;
identified matching released candidate; preserved all original folders and
excluded legacy credentials/nested Git repositories from version control.

Validation: Git status/remotes, gh repo/auth/issues/milestones, Java versions,
Docker CLI/engine, Maven Central artifact metadata. See RECONNAISSANCE.md.

Known blockers: source ZIP missing (extracted source available); Docker engine not
responding; Maven/Flutter absent from PATH. Java 21 located and usable explicitly.

Branch: codex/bliss-v2-foundation. Baseline pushed to main: `29de611`.
Synchronization checkpoint: implementation and docs pushed through `a2a4d727f45a39ce31f9cb502c99a42415dbe029`; follow-up
documentation commits are visible in `git log` and origin on this same branch.
GitHub issues: #1–#17 created after checking existing issues for duplicates.
20 labels, 3 milestones, 4 issue templates and PR template created.

Implemented: Java 21 / Quarkus 3.39.0 with released Flow 1.2.0.CR1; Maven wrapper
3.9.11; Flyway catalog/provider foundation and isolated local-demo seeds; bounded
public catalog API; problem responses/request IDs; OpenAPI/health/metrics; Compose
and PowerShell startup helper. Core BOM uses `io.quarkus`, because the matching
`io.quarkus.platform` 3.39.0 BOM/plugin are not published.

Flutter 3.47.5 / Dart 3.13.4 downloaded from official release archive with SHA-256
verification into ignored `.tools`; Maven bootstrap SHA-512 verified. Generated
new platform scaffold without importing legacy code. Catalog client and five tests
implemented. Flutter analysis is clean and all five tests passed.

Validation:
- Maven wrapper generation, Spotless formatting/check, Java/test compilation: PASS.
- First `mvn verify`: BLOCKED before test discovery by unavailable Docker engine.
- Docker Desktop startup attempted using CLI and desktop executable. Host log
  reports `initializing Inference manager ... dockerInference ... The file cannot
  be accessed by the system`; backend exits. No factory reset or data deletion.
- GitHub CI run [35453307987](https://github.com/Stuaarts/Bliss/actions/runs/35453307987)
  at `b96ec63`: **PASS** for both jobs. Real PostgreSQL migrations applied; backend
  `mvn verify`: 7 tests, zero failures/errors/skips. Flutter analysis/format and 5
  tests pass on Linux. Java compilation and Flutter tests also pass on Windows.
- `flutter build web`: PASS. Nonfatal SDK font tree-shaking warning about unused
  CupertinoIcons family; no build failure. Browser/device demo remains unverified.
- `docker compose config --quiet`: PASS with a placeholder environment value.
- Converted local seed to idempotent repeatable migration so future V3+ schema
  migrations are not blocked by an artificially high demo version. CI rerun: PASS.
- Pinned Actions to verified commit SHAs; Java/checkout upgraded to v5 after CI
  reported deprecation warnings for v4. CI rerun: PASS.
- All required documentation files created, explicitly separating implemented
  catalog functionality from planned auth/booking/workflow behavior.
- Final implementation PR CI run [35453550224](https://github.com/Stuaarts/Bliss/actions/runs/35453550224)
  at `a2a4d72`: **PASS**, both jobs. Three migrations applied, latest version V2
  plus repeatable demo seed; 7 backend tests with zero failures/errors/skips;
  Flutter format/analyze and 5 tests pass. Follow-up commit only records this evidence.
- [Draft PR #18](https://github.com/Stuaarts/Bliss/pull/18) open and attached to the
  task. #1/#7 updated with actual scope and remaining criteria. Root Git working
  tree clean and branch synchronized at the checkpoint; no gitlinks, build output,
  legacy/upstream files or high-confidence secret patterns in tracked files.
- Upstream diff remains exactly the initial six-file/4,069-insertion/509-deletion
  delta. The stalled Docker startup CLI created by this session was stopped.

Backlog status: #1 remains open because local Compose/live-client startup is not
proven. #17 captures the exact host blocker. #2–#16 remain open; nothing was closed
merely because a schema or dependency exists.

Next: restore local Docker without data
loss, run Compose + API + Flutter against live data and close #17; satisfy #1's
startup gate, then proceed to Keycloak/OIDC (#2) and owned provider mutations (#3).
Do not claim booking/authentication/workflow persistence completion without evidence.

## 2026-09-20: resumed identity slice

Foundation PR #18 merged as `82f91d3` after local catalog verification and green CI.
The Docker runtime-folder repair restored the engine on September 19, but the same
stale `dockerInference` socket error returned after the next computer session.
Runtime folders remain preserved; no images, volumes or VHDX were deleted.
Official winget in-place upgrade from Desktop 4.45.0 to 4.91.0 completed, with
installer SHA-256 verification. Engine is now 29.8.0. Ordinary Desktop restart
still exposes inaccessible `sailor-ingest.sock` / `engine.sock` runtime files.
Issue #17 is reopened as a host/upstream limitation. The guarded Windows
`start-docker.ps1` helper preserves both runtime directories only while Desktop is
stopped, refuses unexpected secrets-engine files, and uses bounded engine probes.
The helper restored the engine in two later sessions; both Compose services became
healthy with existing data intact. `dev-api.ps1` invokes it automatically.

Branch: `codex/2-oidc-auth`. Implemented validated OIDC bearer authentication,
subject-bound profile bootstrap/update, provider membership and ownership checks,
optimistic version conflicts, and transactional mutation audit metadata. Local
Keycloak realm uses exact redirect URLs, S256 PKCE, generated ignored credentials
and persistent loopback-only infrastructure. No Bliss password storage.

Validation already passed: Maven verify (14 integration tests, no failures or
skips), Flutter analysis, 9 Flutter tests, release/debug web compilation, and live
PostgreSQL migration to V3. Browser verification caught a missing `basic` default
client scope: Keycloak issued access tokens without `sub`, which the API correctly
rejected. Added that scope to both client templates and to the existing realm via
a temporary local repair admin (removed immediately afterward). Existing realm
users and passwords were preserved. Added a missing-subject negative API case.

Live browser verification now passed: provider login through S256 PKCE, profile
bootstrap, coexisting customer/provider capabilities, editing the display name,
database persistence and audit event, logout, private profile removal, and a fresh
credential prompt proving the identity-server session ended. Browser HTTP caching
initially served old preview JavaScript; temporary cache-busting was applied only
to ignored build artifacts during verification. All temporary diagnostics removed.

Backend checkpoint `d4d3010` CI: both runs 35494437455 and 35494425603 passed.
PR #19 tracks the identity slice. Native AppAuth/secure-storage integration is
implemented and analyzed; device validation remains unperformed and tracked in
#7. Provider onboarding/offering management remains #3; booking/workflows remain
#4–#6. No end-to-end booking completion is claimed.

Final customer browser check: the separate customer account bootstrapped with only
the customer capability. Refreshing after the identity session expired cleared the
private profile and returned to sign-in. Final local checks passed: 14 backend
tests, Flutter analysis, 9 Flutter tests and release web compilation. Docker and
both Bliss containers remained healthy across the six-hour idle interval.

## 2026-09-20: provider management slice

Authentication PR #19 merged as `76595f9` after both API/mobile CI runs passed at
`aad7021` (35537264486 and 35537266448). Issue #2 is complete. Current branch:
`codex/3-provider-offerings`, based on the merged main.

Provider onboarding creates an INDIVIDUAL or COMPANY and its OWNER membership in
one transaction. Provider-role holders may own multiple businesses; onboarding
cannot grant OIDC roles. Owned offering endpoints publish, update and archive,
validate exact decimal strings/currencies/categories, enforce optimistic versions,
retain archive history and audit each successful mutation. Both route provider ID
and offering ownership are checked. MEMBER-only access remains read-only metadata.

Flutter adds business setup/edit, owned service pagination, publish/edit/archive
forms, conflict messages and expired-session privacy. Local verification: Maven
verify passes 18 integration tests, Flutter analyze passes, all 11 Flutter tests
pass. Negative tests cover cross-provider IDs, ordinary membership, revoked roles,
invalid money/category, stale versions, archive idempotence and audit count.
No additional database migration is needed; the foundation schema supports this
slice. Live browser verification passed: created Bliss Demo Studio as a COMPANY,
published Window cleaning demo at CAD 45.50, edited to CAD 51.20 and archived.
Database confirms inactive/version 2 and three audit events. Public discovery
excludes it and its public detail returns 404. Demo history remains available to
the owner. Docker helper healthy-path check passed; final CI is the merge gate.

## 2026-09-20: request/quote transaction slice

Provider PR #20 merged as `a3a1b0b`, after both API/mobile CI runs passed at
`3a1b510` (35548812854 and 35548825992). Issue #3 is complete. Current branch:
`codex/4-requests-quotes`, based on merged main.

V4 adds owned customer requests, open provider opportunities, expiring quotes,
booking snapshots and initial history. Parent-request locks serialize quote
submission, decline, acceptance and cancellation; unique constraints enforce one
accepted quote/booking per request. Same-quote retries return the original booking.
Request scope freezes after quoting; quotes must match request currency and cannot
come from the customer's own identity. Mutation identity/ownership is server-side.
Quote validity defaults to 30 days maximum as a configurable local demo policy.

The eight new API scenarios passed, including simultaneous different-quote
acceptance, simultaneous same-quote retries, cancellation racing acceptance,
expired/declined/self quotes, customer/provider ownership and money constraints.
Final Maven verify passed all 26 backend tests, formatting and packaged build
after API read-route/enum cleanup. V4 migrated the existing local PostgreSQL
successfully and API readiness is UP. The booking is only CREATED: no payment/workflow starts
yet, and request/quote Flutter screens remain #7. Next gate: final verification,
CI, then durable booking orchestration #5.
