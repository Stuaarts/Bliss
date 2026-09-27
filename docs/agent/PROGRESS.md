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

## Verified checkpoint

PR #21 merged as `eeeaca8` after both API/mobile CI runs passed at `7b89ad2`
(35554853475 and 35554876126). Issues #2, #3 and #4 are closed by their verified
PRs. Local `main` is synchronized with GitHub; the implementation includes 26
passing backend tests and 11 passing Flutter tests plus web compilation. This
documentation checkpoint follows that merge without changing application code.

Next work is #5: released Flow JPA schema verification, durable booking dispatch,
stored instance IDs, idempotent fake settlement, human events/timeouts, then a real
waiting-instance restart demonstration. Upstream persistence/correlation examples
were inspected read-only; they explicitly leave duplicate-start prevention to the
application. The current booking remains CREATED with no workflow instance.
Direct booking (#6), full Flutter lifecycle/native-device validation (#7), chat,
reviews and disputes (#8–#10) remain open. Docker #17 stays open for the ordinary
Desktop restart defect; the guarded Bliss helper restores startup without deleting
container data. The local catalog preview remains available on port 3000.

## 2026-09-21: durable booking lifecycle

Phase 5 implementation is on `codex/5-booking-workflow`, based on pushed main
`36e04c1`. Quote acceptance now commits a durable start request. Flow stores its
canonical instance ID, authorizes a fake payment and waits for provider/customer
actions through a PostgreSQL inbox and engine-managed two-second waits. Actions
enforce ownership, scheduling/state policy and UUID idempotency. Transitions,
history, audit, inbox acknowledgment and simulated ledger are atomic. Provider
rejection/timeout and eligible cancellation void authorization; confirmation
captures. REFUND is adapter-tested; public dispute/refund actions remain #10.
Owned timeline/payment/execution reads expose progress without exception details.

The real restart test found two Flow 1.2.0.CR1 JPA defects: duplicate loop task
pointers on restoration, and startup scan transactions propagating into asynchronous
worker threads. A Bliss-local CDI adapter compacts completed iteration checkpoints
atomically and materializes/closes the scan before restoration starts. No upstream
source was changed. Dependency follow-up #22 tracks adapter retirement; #15 now
explicitly tracks multi-worker leases, retry-exhaustion operator recovery, retention
and real-provider outbox/reconciliation. This portfolio runs one API worker.

Final local `spotless:apply verify` passed at 11:55 Toronto: 31 PostgreSQL-backed
tests and two packaged-process restart scenarios, with no skips. Three forced
crashes cover provider and customer waits; the original workflow ID survives and
only one authorization/capture or void is recorded. The test also asserts bounded
checkpoint rows and absence of the reproduced restoration/transaction errors.
Injected transient failures occur after fake ledger writes, proving transaction
rollback before a successful retry. Formatting and packaged build pass. Evidence:
ignored `.work/workflow-final-verify.log` and `.work/workflow-restart-server.log`.

Docker was stopped on resumption; the guarded helper restored it while preserving
runtime folders and all persistent data. PostgreSQL/Keycloak are healthy. Local
V4→V5 migration passed with existing data preserved; API readiness is UP on 8088
and the existing Flutter preview is restored on 3000. Final GitHub CI gates merge.
Next slices: direct offering booking #6, Flutter lifecycle/native validation #7,
then chat/reviews/disputes #8–#10. Full portfolio release is still incomplete.

### Verified Phase 5 checkpoint

PR #23 merged as `e6a5a24` after both CI runs passed at `f2b86e4`
(35622355865 and 35622369304). Both API jobs verified migrations, 31 regular
backend scenarios and two packaged-process restart scenarios. Both mobile jobs
passed formatting, analysis, all 11 tests and release web compilation. Issue #5
is closed. Current branch is `main`; latest pushed implementation is `e6a5a24`.
This documentation checkpoint follows that merge without changing application code.
The working tree is synchronized on push. #6 is the next implementation slice;
#17 (ordinary Docker restart) and #22 (upstream Flow compatibility) remain open
with working local mitigations. No real payment movement or public deployment.

## 2026-09-21 evening: direct offering booking

Issue #6 implementation is on `codex/6-direct-booking`, based on pushed main
`09fe274` (its CI is green). Customers can POST an offering revision and city with
a UUID Idempotency-Key to create a direct booking. Scope/price/currency/provider
come from the locked active offering; stale versions and provider self-booking
(OWNER or MEMBER) are rejected. Exact retries return the original booking even
after archive/cancellation. Conflicting reuse returns 409; keys belong to the
authenticated customer. Customer locks and a unique customer/key index serialize
retries, including requests targeting different offerings.

V6 enforces exactly one source (request/quote or offering/revision/key), backfills
accepted quote descriptions, and retains immutable scope on both paths. Shared
BookingCreation commits initial history, audit and workflow start intent together.
Direct bookings use the same existing lifecycle and simulated payment adapter.
Public offering DTOs now include the version needed for booking confirmation.
Flutter booking screens remain #7; this slice is the API foundation for that UI.

Final `spotless:apply verify` passed at 19:15 Toronto: 37 regular backend tests and
three integration scenarios, no skips. New cases cover concurrent exact retries,
conflicting keys across offerings, archive races, frozen price/scope, authorization,
input validation, self-booking and one workflow dispatch/void. The populated V5→V6
test preserves quote scope/price and checks database source constraints. Packaged
restart tests cover quote rejection and direct-booking completion, including both
provider/customer crash waits with the same instance and one settlement. Test
fixtures are archived after each direct-booking case so catalog checks stay isolated.
Evidence is in ignored `.work/direct-verify.log` and `.work/direct-restart-server.log`.

Docker was stopped again between sessions; the guarded helper restored it without
deleting volumes. Compose PostgreSQL/Keycloak are healthy. Local V5→V6 migration
passed at 19:16 with API readiness UP on 8088; public discovery returns the expected
offering revision and exact price. The existing Flutter preview is restored on
3000. GitHub CI is the final merge gate. Next: Flutter lifecycle/native validation #7.

### Verified direct-booking checkpoint

PR #24 merged as `ce6ad6d` after both CI runs passed at `a0b35be`
(35666823303 and 35666826072). API CI passed all 40 backend scenarios; mobile CI
passed formatting, analysis, 11 tests and release web compilation. Issue #6 is
closed. Current branch is `main`; latest pushed implementation is `ce6ad6d`.
This documentation-only checkpoint reconciles scope and the next slice without
changing application code. The next implementation is #7: customer/provider
request, quote, direct-booking and lifecycle screens with real API integration;
native-device authentication validation remains outstanding. #8–#10 and release
hardening are still open. Docker #17 and Flow compatibility #22 retain their local
mitigations. No full portfolio-release or production-readiness claim is made.

## 2026-09-22–23: Flutter marketplace lifecycle

Issue #7 implementation is on `codex/7-flutter-marketplace`, based on main
`68dfb71`. Your activity connects customer requests, provider opportunities and
quotes, quote acceptance, direct-offering confirmation and owned booking details.
Provider schedule/start/completion and customer confirmation/cancellation use the
existing durable action endpoints. Timelines show local times and simulated
payment status; visible nonterminal bookings poll without repainting unchanged data.

Session-bound routes dispose private forms on logout. Late requests from an older
session are rejected. UUID retry keys and frozen request bodies survive uncertain
responses in the current session; processed-event retries do not leave the UI
waiting. Keys are deliberately in memory, so users must check their booking list
after reloading or signing out. Request/quote creation does not permit blind retry
after an uncertain response. Backend ownership checks remain authoritative.

Real browser verification against local Keycloak, PostgreSQL and API completed
the request → quote → acceptance → schedule → start → provider completion → customer
confirmation flow for CAD 85.50 on September 22. On September 23, the local stack
restart restored the unanswered direct booking and expired it after its provider
deadline, voiding CAD 65.50. A new direct booking was created and cancelled through
the UI with a simulated void. Completed/expired/cancelled records remained visible.
Offering and activity layouts were also checked at 390 pixels. Reproducible steps
and screenshots are in `docs/demo/MARKETPLACE.md`.

Flutter analysis, all 22 tests and release web compilation passed, followed
by full GitHub CI before merge. Native AppAuth and secure-storage validation is
still outstanding: no Android/iOS device is connected, Android command-line tools
are missing, and SDK license status is unknown. Issue #7 remains open for this
work. The next independent feature is authorized conversations (#8). No backend
schema/routes changed, no real payment moved, and no public deployment occurred.

Initial PR #25 CI passed both mobile jobs and one API job; the duplicate API run
exposed an existing restart-test timing race. Its provider acceptance schedule
used now + one second rounded down to seconds, leaving less than one second for
HTTP/JWT processing before the server correctly rejected it as past. The fixture
now uses a ten-second lead with microsecond precision and still waits for the
actual scheduled time before starting. Production validation is unchanged. The
full CI suite must pass on this fix before merging.

### Verified Flutter lifecycle checkpoint

PR #25 merged as `a671e58` after both CI runs passed at `cfdbdab`
(35925889787 and 35925893940). Each API job passed 37 regular tests and three
migration/restart integration scenarios without skips; mobile formatting,
analysis, all 22 tests and release web compilation passed. The scheduling fixture
fix passed both independent runners. Current branch is `main`, synchronized with
the merged implementation. This documentation checkpoint records that result.
Issue #7 stays open for native AppAuth/device validation; #8 conversations is the
next independent implementation slice. Local Docker, API and Flutter preview are
running, with the completed demo booking open. No full release claim is made.

## September 24, 2026 — persisted booking conversations (#8)

V7 adds one conversation per booking, snapshotted participants and messages with
sender-scoped retry keys and serialized sequence allocation. Access requires both
snapshot membership and current capability/customer or provider OWNER access.
Outsiders, MEMBERs, admin-only identities and replacement owners cannot read/write
history. Revoked owners lose access. Bounded sequence pagination stays stable while
new messages arrive; exact send retries return the original receipt and audit event.
Text is validated server-side and rendered without HTML interpretation.

Flutter exposes Messages from bookings and the activity list, with three-second
visible-route polling, refresh, earlier messages and session-scoped idempotent send
retry. Read cursors advance only on reads, so a send receipt cannot skip intervening
messages. Authorization loss clears history/composer, and logout disposes private
routes and rejects late responses. Pending sends survive route changes in memory.

Local Maven verify passed 43 regular tests plus three packaged migration/restart
scenarios, including six new chat scenarios. Flutter formatting/analysis, all 28
tests and release web build passed. Real browser checks sent as customer, replied
as provider, stopped/restarted the API and reloaded both messages as customer.
The 390 × 844 layout also passed; evidence is in `docs/demo/CHAT.md`. The API's V7
migration upgraded the existing demo database without reset. One login during
restart failed safely and succeeded once the API was ready.

Remaining: PR/CI and merge; reviews (#9) next. Native auth (#7), invitations/ownership
transfer for chat, attachments, read receipts, push and retention remain outside
this slice. No real payment or public deployment occurred.

### September 25 — chat merge checkpoint

PR #26 merged as `93b1a02`, closing #8, after both API and both mobile CI jobs
passed at `df1bb8e` (runs 36152153242 and 36152194589). Work continued on
`codex/9-completed-booking-reviews`, based on the merged main branch.

## September 25, 2026 — completed-booking reviews (#9)

V8 adds a unique review per booking, constrained 1–5 rating, optional bounded plain
text and UTC time. A booking lock serializes eligibility and creation; only the
owning customer with current capability may submit for COMPLETED bookings. Exact
normalized retries return the original review; changed duplicates return 409.
Public provider pages return average/count over all reviews, with bounded paging
and no booking/customer identity fields. No cached rating counter can drift.

Flutter now exposes Booking review on completed bookings and Provider reviews from
offering detail. The form states public visibility/immutability, validates rating,
preserves uncertain submission intent, reloads on duplicate conflict, and disposes
with the private session. Public ratings need no login and support retry/pagination.

Maven verify passed 48 regular tests plus three packaged migration/restart scenarios
(51 total, no skips). The five new review scenarios cover invalid lifecycle states,
ownership/capability/anonymous rejection, validation, concurrent creation, database
uniqueness and public aggregate/privacy behavior. Flutter formatting/analysis,
33 tests and release web build passed. A retry widget test initially tapped before
the scroll layout settled; awaiting that layout resolved the fixture failure.

Browser checks against real local services verified no-review empty state, customer
publishing on the completed September garden help booking, reopening without a
duplicate form, and signed-out provider reads showing 5.00 / 5 and one review.
Form/saved/public views passed at 390 × 844; screenshots are in
`docs/demo/REVIEWS.md`. V8 upgraded the existing local database without reset.

PR/CI remain before merge. Next feature: #10 manual-admin disputes. #7 native
authentication and #15 production moderation/privacy remain open. No real payment,
public hosting, new vendor service or upstream Flow change occurred.

### Reviews merge checkpoint

PR #27 merged as `9610918`, closing #9, after all four API/mobile jobs passed at
`6dedc34` (runs 36154068172 and 36154116278). Main is synchronized with that change.
The next active slice is #10, durable disputes with manual admin decisions and
simulated settlement. Native-device validation remains #7.

## 2026-09-26 — Manual disputes (#10)

V9 and DisputeFlow add one customer case per eligible booking, bounded private
evidence, a saved collection deadline and independent admin review. Both parties
submitting or timeout opens review; no automatic winner is chosen. Customer-favored
decisions refund captures or void authorizations; provider-favored decisions capture
or retain capture. Case/booking history, payment, decision processing and audit
commit together. Actor-scoped retries and booking-first locks serialize admissions.
Flutter connects filing, activity lists, evidence, admin decisions and polling.

Maven verify passed September 25: 55 ordinary scenarios and five packaged-process/
migration scenarios, zero failures/skips (60 total). New tests cover access denial,
filing/evidence deadlines, duplicate/concurrent actions, all settlement paths,
transient rollback and exhausted retries. Both dispute restart scenarios kill the
packaged API during evidence collection and admin review and retain the canonical
instance with one settlement. Flutter analysis, 39 tests and release web build pass.

The exhausted-retry check found that the pinned Flow persistence writer removes
failed checkpoints. A Bliss lifecycle listener retains a content-free failure
timestamp so execution reads report FAULTED, payments remain unchanged and automatic
redispatch stops. Operator recovery remains hardening; no upstream source was edited
for this change. Existing local upstream modifications were left untouched.

On September 26, real browser checks at phone width verified customer filing,
provider evidence, admin customer-favored decision, pending-to-applied polling and
customer re-login/reopening. September garden help now shows Refunded/CAD 85.50;
the DB ledger has exactly one AUTHORIZE, CAPTURE and REFUND. V9 preserved existing
data. Screenshots and policies: `docs/demo/DISPUTES.md`. All funds are simulated.

The host restart also exposed a Windows helper defect: Move-Item left a socket
reparse point behind in the secrets runtime folder. Replacing runtime-directory
moves with atomic Directory.Move preserved the stale folders and started Docker
successfully. PostgreSQL/Keycloak became healthy with their data intact. #17 remains
open for Docker's ordinary startup limitation; no reinstall/reset was needed.

PR/CI and merge are the remaining gate for this slice. Native auth (#7), final
database hardening (#11), release demo (#12) and production policies remain open.

### Disputes merge checkpoint

PR #28 merged as `2cab47e`, closing #10, after all four API/mobile CI jobs passed
at `17f0e90` (runs 36252989901 and 36253015017). Main is synchronized. Database
hardening (#11) is next: populated upgrades and direct constraint tests across
bookings, payments, chat, reviews and disputes, alongside existing concurrency tests.

## 2026-09-26 — Database invariants (#11)

V10 adds finite-money checks to offering/request/quote/booking/payment, payment
currency format, known booking/dispute history states, and consistent dispute
deadline/resolution timestamps. PostgreSQL numeric NaN passed the previous
nonnegative checks; direct SQL regressions now reject it at all five boundaries.
Released V1–V9 migrations are unchanged. No records are rewritten by V10.

DatabaseInvariantsIT adds three real PostgreSQL scenarios: empty-schema UUID/UTC/
decimal types; complete row preservation across populated V8→V9→V10 (19 tables),
followed by direct invalid-write rejection; and V9 NaN data preventing migration
with every V10 DDL change rolled back, followed by fixture-only correction and a
successful upgrade. The matrix and operator preflight/recovery guide are in
`docs/architecture/DATABASE_INVARIANTS.md`.

Local full verify passed all 55 API scenarios and the five existing migration/
restart scenarios. The new type test initially included Flyway's own installed_on
metadata and failed; limiting the check to application tables fixed the fixture.
The final targeted verify then passed all three new database scenarios and
formatting (63 distinct backend scenarios covered across these runs). The timeout
fixture now moves both case creation and deadline into the past, retaining a
valid chronology. No Flutter code changed in this slice.

The local V9 database was backed up to ignored `.work/before-v10.sql`; every
documented preflight returned zero invalid rows. V10 then applied to the populated
local database and API readiness returned UP. The existing resolved customer case,
one AUTHORIZE/CAPTURE/REFUND ledger, two chat messages and five-star review remain.
CI remains before merge. #7 native-device authentication and #12 full release
evidence remain.

PR #29's first push CI passed all jobs, while its PR run exposed a race in the
dispute restart test helper: the case read could precede workflow dispatch but the
subsequent execution read already contained a checkpoint. The helper now requires
a nonempty case workflow ID matching the execution response before returning the
pre-crash snapshot. This strengthens the recovery assertion instead of treating
an unassigned ID as valid. Both new database migration suites passed in those runs.
