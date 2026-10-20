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

## 2026-09-27 — Database merge and release evidence (#12)

The final local dispute restart recheck passed both packaged-process scenarios.
PR #29 merged as `41e564f`, closing #11, after all four API/mobile jobs passed at
`c95ea84` (push run 36286965393 and PR run 36286967602). Each final API job ran all
63 backend scenarios; Flutter formatting, analysis, 39 tests and web build passed.

The release documentation now links the verified marketplace, chat, review and
dispute runs, adds an 8–10 minute presentation script, and explains how to prove
the same workflow instance survives a restart. README and architecture/action
catalogs describe the implemented functionality. The new
`docs/product/RELEASE_CHECKLIST.md` distinguishes demonstrated local web behavior
from unfinished native-device validation. Screenshots come from the separate
verified runs; no narrated video or native run is claimed.

#7 and #12 remain open: the latest workstation check has no connected Android/iOS
target, Android command-line tools are missing and license status is unknown.
All 68 local Markdown links, diff whitespace and tracked-file hygiene checks
passed. The Windows backend helper started Docker/API with the V10 data intact;
PostgreSQL/Keycloak were healthy, readiness UP, and Swagger/OpenAPI/metrics/Dev UI
returned HTTP 200. OpenAPI included booking/dispute routes and startup warmed both
workflows. The documentation slice still requires its own green CI before merge.
Production policy, hosting, real payments and license decisions remain outside
the local portfolio demonstration.

### Release docs merged; Android environment advanced

PR #30 merged as `42e98ff` after all four API/mobile CI jobs passed at `3d17c9b`
(push 36301363619, PR 36301370072). This delivers the timed walkthrough, release
checklist and current architecture/action catalog while leaving #12 open for #7.

The Android investigation found an existing API 36 emulator and accepted SDK
license files despite Flutter doctor's missing command-line tools warning.
`flutter build apk --debug` passed; Gradle installed the missing NDK/platform/CMake
dependencies using those existing licenses. The existing emulator had insufficient
internal storage, so a separate project-local Bliss_Test AVD was created without
deleting its data. Installation succeeded and, after boot completed, cold launch
returned Status: ok. Android resolves the AppAuth callback to Bliss's receiver.
Evidence, APK hash and remaining native acceptance steps: `docs/demo/ANDROID.md`.
Kotlin's generated session directory is now ignored.

This is build/startup evidence only. Native PKCE login, persisted refresh-token
recovery, logout and iOS remain unverified; #7 and #12 stay open. No native UI flow
or secure-storage success is inferred from a registered callback.

## 2026-09-27 — Native session lifecycle (#7)

The Android catalog visibly loaded real API data. Native Windows input failed
twice with `SendInput sent 0 of 1 events; GetLastError=87`; UI automation stopped.
No native browser login is claimed. Independent code verification reproduced four
ordering defects: late refresh restored a logged-out session, failed old refresh
cleared a new account, an in-flight storage write outlasted deletion, and a delayed
storage read revived old credentials. NativeAuthTokens now guards asynchronous
results with a session generation and serializes credential writes/deletes.

Seven platform-channel tests pass, including late authorization, write-failure
recovery, shared refresh and token rotation/logout hints. Flutter analysis and
all 46 ordinary tests pass. The Android integration test uses real AppAuth/secure
storage plugins with synthetic OIDC HTTP responses. Three separate processes
verify persisted seed, refresh/rotation/deletion, then continued sign-out after
process death. All three phases passed on the dedicated Bliss_Test/API 36 emulator.
The first persistence run exposed Flutter's default test-app uninstall; the
corrected `--no-uninstall` command retains storage between phases.

The fixture has no demo credentials, browser login or real identity-provider
logout. It refuses to overwrite an existing seed-phase session and runs only on
a dedicated test emulator. Reproduction and limits: `docs/demo/ANDROID.md`.
The normal release web and debug APK rebuilds pass; the normal APK was reinstalled
and cold-launched successfully after replacing the fixture. All 71 local Markdown
links, diff whitespace and tracked-file hygiene checks pass. PR CI remains before
merge; #7/#12 stay open for real native authentication/lifecycle evidence and iOS.

### Native session merge checkpoint

PR #31 merged as `ba1699e` after all four API/mobile jobs passed at `4a19f98`
(push 36327118198, PR 36327121173). API CI passed all 63 scenarios; mobile CI passed
formatting, analysis, 46 tests and release web compilation. Main is synchronized.
The normal APK replaced the fixture build, launched successfully, and the dedicated
emulator was stopped with its data preserved. API readiness is UP. #7/#12 remain
open for the interactive native and iOS checks; no real native browser login or
identity-provider logout is inferred from the successful fixture tests.

## 2026-09-27 — Real Android browser authentication (#7)

The separate UiAutomator driver reproduced a real Keycloak callback failure twice:
the redirect receiver ran, but Chrome remained visible and the authenticated
profile never appeared. Removing MainActivity's empty task affinity fixed it.
The same test then passed in 51.818 seconds; the tracked PowerShell runner repeated
the full check successfully in 47.963 seconds after rebuilding/reinstalling the
normal debug app and both driver APKs.

The run verifies customer PKCE login and API profile, customer recovery after
force-stop, logout persisting across restart, fresh provider credentials and
provider controls, provider recovery, then identity-provider logout and switching
back to customer without provider controls. A final logout/restart leaves the app
signed out. Real emulator screenshots are in docs/demo/ANDROID.md. Windows window
activation failed twice, so no further desktop input was attempted; the Android
instrumentation runs on-device and does not depend on Windows focus.

The runner restricts execution to Bliss_Test, checks local services, reads generated
credentials without printing them, and removes staged inputs in finally. Host and
device cleanup were verified after the repeat. A separate Android CI job compiles
the app and instrumentation driver; real Keycloak emulator execution stays a local
gate. No credentials, APKs or raw device logs are tracked.

Current branch: codex/7-native-oidc-e2e, based on pushed main checkpoint 8cc689a.
CI and merge remain pending. #7/#12 remain open for the complete native marketplace
lifecycle and iOS validation; the latter needs a macOS host. The existing browser
marketplace evidence and 63 backend/46 Flutter scenario counts are unchanged.

Final local checks: Flutter analysis and all 46 tests passed. The documentation
checker passed 74 local Markdown links and 253 tracked/new paths; diff whitespace
is clean. Actual Android runner screenshots were visually reviewed before adding
only the three credential-free application screens to documentation.

### 2026-09-28 — Android authentication merge checkpoint

PR #32 merged as `a892dd9` after all six API/mobile/Android jobs passed at
`aae6927` (push 36346822438, PR 36346825784). API CI passed all 63 scenarios;
Flutter CI passed analysis, 46 tests and release web compilation. Clean Linux
Android app and driver compilation passed. Main CI 36347290131 also passed.
Current branch is main, synchronized to merge commit a892dd9. Issues #7 and #12
remain open and were updated with actual authentication evidence and remaining
native marketplace/iOS acceptance work. Bliss_Test was stopped with data intact.
The next slice verifies the full native customer/provider marketplace lifecycle.

## 2026-09-28 — Native Android request-to-review verification (#7)

The normal Android APK completed a real customer/provider marketplace run on
Bliss_Test/API 36 against local Keycloak, Quarkus and PostgreSQL. UI controls created
the request and quote, accepted the quote, exchanged two messages, scheduled work,
waited for the real scheduled time, started/finished work, confirmed completion,
and displayed simulated capture. After app-process restart the same booking and
messages reopened; the customer published and reopened a review, backgrounded and
resumed the app, then restarted and signed out. The final fresh run passed all
checkpoints in 375.264 seconds. No marketplace product change was required.

Driver development corrected Flutter hint-label selection, explicit input focus,
and review-card combined semantics. An earlier run completed/published its review
but failed the final rating selector; that result was not counted as an end-to-end
pass. The fresh native-20260928-b run passed. Read-only database inspection found
one authorization, one capture, two messages and one review on its completed booking.
Shared native controls were extracted without changing authentication behavior;
the separate real OIDC regression passed again in 48.438 seconds. Generated host
credential inputs were absent after cleanup. Reviewed app-only screenshots and
reproduction commands are in docs/demo/ANDROID_MARKETPLACE.md.

The PowerShell runner now supports explicit marketplace phases for diagnosis,
a ten-minute full-flow limit and an existing-run guard (verified against the first
persisted request). It preserves demo data; partial phases do not claim full-flow
success. The local Docker startup helper restored healthy services without resetting
volumes. No backend tests were run alongside the API dev process; CI retains the
full 63 backend/46 Flutter checks and compiles both native drivers.

## 2026-10-05 — Resume and prepare native marketplace merge

Saved full-run and authentication recheck logs both confirm success. Current branch:
codex/7-native-marketplace, based on pushed main checkpoint 5227c12 (its CI passed).
The emulator is currently stopped. Evidence dates remain September 28; no new device
run is inferred from this documentation update. Older README/demo statements about
unverified Android login are corrected. #7/#12 remain open for iOS and secondary
native paths; this slice still needs its own clean-checkout CI and merge.

Pre-PR documentation checks passed 92 local Markdown links and 259 tracked/new
paths for generated artifacts and high-confidence secret patterns. Diff whitespace
is clean. The saved shared-driver authentication run passed all checkpoints; no
additional runtime changes were made after that successful recheck.

### Native marketplace merge checkpoint

PR #33 merged as `0e04a80` after all six API/mobile/Android jobs passed at
`1f0f9ac` (push 37397402890, PR 37397405582). Each API run passed all 63 scenarios;
Flutter passed all 46 tests, analysis, formatting and release web compilation.
Both native scenarios compiled with the normal Android APK on clean Linux CI.
Current branch is main, synchronized to the pushed merge commit 0e04a80. The
documentation checkpoint following it changes no runtime or test behavior.
Issues #7/#12 remain open for the explicitly listed native platform/secondary-path
checks. No iOS or production release is claimed. The emulator remains stopped;
the successful local UI evidence and generated demo data are preserved.

## 2026-10-09 — Android secondary flows and connection recovery (#7/#12)

The fresh native-20261009-d full run passed in 303.93 seconds, using native
UI mutations for two direct bookings: cancellation/void and a real scheduled
service followed by customer filing, provider evidence and an independent admin
customer-favored decision. The customer reopened the voided payment and applied
decision after background/resume and process restart, then signed out. Read-only
database verification found exactly one AUTHORIZE and VOID per booking, one
provider evidence item and one processed decision; no capture/refund occurred.
Reviewed screenshots and commands are in docs/demo/ANDROID_SECONDARY.md.

An interrupted October 5 run lacked a final result; its final read/check phase
passed in 45.269 seconds after inspecting the applied database state. A fresh run
then exposed an immediate-selector race while opening the admin form; waiting for
Outcome fixes the driver. Those partial runs are not full-flow evidence. The
earlier account-switch failure was fixed by waiting for enabled Sign in after
logout. No marketplace product code was changed.

Catalog outage/retry passed in 3.589/1.533 seconds by removing and
restoring only the dedicated emulator's API reverse mapping. Retry returned real
data on the same failed screen, without an app restart. The backend stayed UP.
Real Android Keycloak login, recovery, logout and switching passed again in 51.846
seconds. Host/device credential staging was removed, including the interrupted
run's leftover input. Existing demo data and the personal emulator were preserved.

Inspection also found main checkpoint 009a44e's API CI failure (run 37398060266),
tracked in [#34](https://github.com/Stuaarts/Bliss/issues/34):
BookingWorkflowTest submitted a future schedule only one rounded second ahead,
which could expire before reaching the API. The test now allows five seconds and
still waits for the actual start time; production schedule validation is unchanged.
New clean-checkout API/mobile/Android gates are required before merging.
CI push checks now include every branch, preparing for the user's requested
removal of codex/ from branch names. Renaming will preserve commits and tracking
after this slice is merged. Current branch: codex/7-native-secondary-flows.
iOS, physical-device coverage and signed distribution remain unverified; captured
refunds/provider-favored/timeouts retain their separate API/web evidence.

The corrected workflow scenario passed locally (one test, zero failures/errors/skips,
elapsed 28.41 seconds) using isolated Testcontainers PostgreSQL after stopping the
API dev process. Spotless completed; document/hygiene checks passed 107 local links
and 268 tracked/new paths. The secondary duplicate-run guard rejected the existing
run before build or mutations. Clean-checkout CI remains the merge gate.

### Secondary flows merge and branch naming checkpoint

[PR #35](https://github.com/Stuaarts/Bliss/pull/35) merged as `03a5437` after
all six API/mobile/Android jobs passed at `c3556e3`: [push CI](https://github.com/Stuaarts/Bliss/actions/runs/37883184012)
and [PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37883188349). Each API run
passed 55 ordinary plus eight migration/packaged-process scenarios (63 total).
Flutter passed all 46 tests, analysis, formatting and release web compilation.
The normal Android app and all native scenarios compiled on clean Linux runners.
Issue #34 is resolved; #7/#12 retain the remaining iOS/platform gate.

After merging, the user's requested branch rename removed codex/ from all
14 existing GitHub branches and 14 local branches. There were
no open PRs or name collisions. Every branch's commit identity was checked before
and after renaming, and local tracking was updated. Default main and its history
are preserved. The ignored .work/branch-rename-map.json records the exact mapping.
Historical progress entries retain the branch names used at those checkpoints.
Future Bliss work should use issue-linked names without the codex/ prefix.

Current branch: main, synchronized to the merge above before this documentation
checkpoint. The local API is healthy, Docker data is preserved and the dedicated
emulator is stopped with its data intact. Staged credentials are absent. No iOS,
physical-device or signed-distribution result is inferred from these checks.

## 2026-10-09 — iOS simulator build and native session fixture (#7/#12)

Current branch: `7-ios-simulator-verification`, using the user's requested naming
without codex/. The Windows workstation has no Xcode; standard macOS CI now
builds the normal iOS app and runs three isolated native session processes.
The pinned secure-storage plugin requires Keychain entitlements that were missing
from Runner. All three configurations now reference Runner.entitlements with the
documented empty keychain-access-groups array. Existing AppAuth callback/scene
registration is retained for compilation and subsequent native verification.

The guarded macOS script creates and deletes only its own disposable simulator;
synthetic fixture tokens never use personal/demo accounts. Initial code checkpoint
`8cce162` is pushed; [native run](https://github.com/Stuaarts/Bliss/actions/runs/37884919565)
passed. It reported Xcode 16.4 / 16F6, Flutter 3.47.5 and an iPhone 11 / iOS 26.2
simulator. The normal app compiled in 42.0 seconds; the built callback scheme
check and each seed/restore/signed-out process passed. Downloaded artifacts and
the job log confirm actual native plugin execution. Local Flutter analysis,
integration-test formatting, Bash syntax and entitlement XML/configuration checks
passed. The final runner revision adds separate evidence directories and explicit
interruption cleanup; its native and ordinary CI still need to pass before merge.
See [iOS validation](../demo/IOS.md) for commands, evidence boundaries and the
remaining real browser/marketplace acceptance checks. #7/#12 remain open.

The next native attempt at `e3afdfd` (run 37885539835) compiled the actual app
and passed seed, then stalled after restore's Xcode build, before the test body
started. Normal cancellation did not complete promptly; force-cancellation
recovered job logs and fixture artifacts. This attempt is not counted as a pass.
All six ordinary API/mobile/Android jobs passed at that head, including 63 backend
scenarios and 46 Flutter tests. The runner now reboots the same simulator between
phases, preserving app/Keychain data, and bounds command groups including Flutter's
otherwise unbounded pre-test launch wait. Verbose phase logs and recent Runner
logs on failure make a repeat stall diagnosable. There is no automatic test retry.

Run 37886765697 at `0e7d2b4` passed seed and restore (real refresh/rotation),
then the five-minute watchdog failed signed-out before its test started. Verbose
logs show the app launch PID and a wait for Flutter's VM service; no signed-out
assertion ran. Reboots alone were insufficient. The iOS gate now uses Flutter's
official native XCTest integration bridge, replacing the empty RunnerTests
template and using the app host's Swift package plugins. It runs the same Dart fixture
through Xcode without Flutter CLI debug-service discovery. The runner requires
the expected phase and exactly one successful XCTest per invocation. Fresh CI
must validate this revision; no failing or empty test is accepted as success.
The isolated Linux-container watchdog check passed normal/nonzero exit
propagation, timeout status 124, killing a TERM-ignoring descendant and preserving
an unrelated process. All six ordinary jobs also passed at `0e7d2b4`.

The first XCTest attempt, run 37887805680 at `49a4915`, failed in build planning:
Xcode rejected linking the generated static plugin package into both the app
and its test bundle. The redundant RunnerTests dependency is removed; the test
bundle uses its app host's existing static plugin linkage, as Flutter's Swift
package testing guidance specifies. No XCTest was executed by that failed build.

Run 37888126778 at `1d197d8` compiled and ran the native seed XCTest, failing
Flutter's semantics-handle invariant because iOS enabled platform accessibility
mid-test. The fixture owns no semantics handles. XCTest-only setup now waits for
platform semantics before WidgetTester records its baseline, with a 30-second
deadline and listener removal in finally. Normal leak checks and all storage/auth
assertions are retained. The native bridge demonstrably propagates Dart failures
as a failed XCTest/job; this attempt is not counted as a passing fixture.

### iOS native gate merge checkpoint

The final XCTest revision `4da6f18` passed in
[native CI 37888748649](https://github.com/Stuaarts/Bliss/actions/runs/37888748649).
Downloaded artifacts match the full commit and record Xcode 16.4, Flutter 3.47.5,
an iPhone 11 / iOS 26.2 simulator, the normal app build (39.4 seconds) and callback
registration. Seed, restore and signed-out each ran the expected XCTest with
exactly one test and zero failures. Keychain persistence, native AppAuth refresh,
rotation and local logout are verified across separate processes. The accessibility
startup wait resolved the baseline race while retaining normal leak checks.

All seven checks passed on that head, including all six ordinary checks in
[push CI](https://github.com/Stuaarts/Bliss/actions/runs/37888748623) and
[PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37888752272): 63 backend
scenarios, 46 Flutter tests, analysis/formatting, release web build and Android
app/native-driver compilation. The branch was checked against current main before
[PR #36](https://github.com/Stuaarts/Bliss/pull/36) merged as `60b23fd`.
Current branch: `main`, fast-forwarded to that merge for this documentation checkpoint.
Completed work is pushed; branch names continue to omit codex/ as requested.

#7/#12 remain open for real iOS browser PKCE/callback, process recovery,
background/resume, identity-provider logout/account switching and marketplace UI
acceptance. Run these against isolated local services on a macOS host; do not infer
them from synthetic fixture tokens or expose the Windows development services.
Physical devices and signed distribution remain separate. No live payments,
public deployment, database reset or upstream Flow changes were introduced.

## 2026-10-09 — real iOS browser authentication (#7/#12)

Active branch: `7-ios-browser-auth`, from main `096ccd9`, without codex/.
The next slice uses a separate RunnerUITests target and BlissAuth scheme to drive
the normal app and Apple's authentication browser. A macOS runner creates fresh
PostgreSQL 17, pinned/digest-checked Keycloak 26.7.4 and the packaged Bliss API on
loopback, with generated disposable credentials. The existing Windows services
are not exposed or modified. No password grant or token injection is introduced.

The required native observations are customer/provider browser callbacks,
authenticated profile/capability checks, process termination/relaunch recovery,
background/resume, identity-provider logout and switching back without stale
provider state. The runner checks the expected XCTest, test count, eight explicit
checkpoints and post-authentication screenshots. Raw XCTest bundles and browser
captures are private temporary files, excluded from uploaded evidence; retained
text logs redact generated passwords and JWT-shaped values. Native execution
and final CI remain pending. #7/#12 remain open for this gate and marketplace UI.

Initial native execution at `a51b530` (run 37943867260) built the XCUITest target
and passed the first four customer checkpoints with real browser authentication.
Provider browser login returned `Hello, provider`, failing the expected seeded
Alex Demo profile. API logs explicitly report Flyway locations fixed to
db/migration at build time, excluding db/demo despite the runtime local profile.
The runner now builds and starts the API with the same local profile and checks
the seeded provider row before UI execution. No assertion was weakened. All
seven other API/mobile/Android/synthetic-iOS checks passed at the first head.
Local log-redaction and subprocess normal/nonzero exit checks also passed.

### Real iOS browser authentication merge checkpoint

The corrected runner passed at `5685e9e` in
[native browser CI 37945058779](https://github.com/Stuaarts/Bliss/actions/runs/37945058779).
The downloaded evidence matches the full commit and records the Alex Demo seed,
healthy real API, Xcode 16.4, Flutter 3.47.5, PostgreSQL 17.11, Keycloak 26.7.4 and
iPhone 11 / iOS 26.2. One XCTest passed in 92.921 seconds with zero failures,
all eight checkpoints and all eight named post-authentication screenshots.
Customer/provider browser callbacks and refresh recovery, customer background/
resume, local/identity-provider logout and switching back without stale provider
state now pass. Three original screenshots were visually reviewed and retained
in the documentation. Raw browser captures/credentials were not uploaded.

All six ordinary checks passed in
[push CI](https://github.com/Stuaarts/Bliss/actions/runs/37945058709) and
[PR CI](https://github.com/Stuaarts/Bliss/actions/runs/37945065853), including
63 backend scenarios and 46 Flutter tests, analysis/formatting/web build and
Android app/native-driver compilation. The separate manually dispatched
[synthetic iOS run 37945150445](https://github.com/Stuaarts/Bliss/actions/runs/37945150445)
also passed on the exact head; manual dispatch is outside the seven-check PR
rollup. All eight checks and current-main ancestry were verified before
[PR #37](https://github.com/Stuaarts/Bliss/pull/37) merged as `080a11f`.
Current branch: `main`, fast-forwarded to that merge for this release-doc checkpoint.

#7/#12 remain open for real iOS request-to-review marketplace UI, chat, simulated
settlement and the secondary cancellation/dispute/connection-recovery scenarios.
The isolated macOS services and native UI target now provide the foundation for
that next slice. Physical devices and signed distribution remain separate.
Branch names continue to omit codex/. Existing Windows service data, legacy
inputs and upstream Flow are preserved; no live payments or public deployment.

## 2026-10-09 — iOS request-to-review marketplace (#7/#12)

Active branch: `7-ios-marketplace`, from main `59a9795`, without codex/.
The separate native UI target now includes a request/quote/booking/chat/scheduled
work/simulated-capture/review scenario. Browser/session controls are extracted
without changing production app auth. The existing isolated macOS runner selects
one explicit XCTest per scenario and adds read-only database corroboration for
marketplace results. Both real auth and synthetic native gates remain required.
Local Python/scheme checks pass; native execution and final validation are pending.
See [required iOS marketplace scenario](../demo/IOS_MARKETPLACE.md). Secondary
cancellation/dispute/connection-recovery, physical devices and signing remain open.

First native marketplace run `37952486236` at `0bb9a85` compiled and created the
request/quote through real browser-authenticated UI. The sent customer chat row
appeared in the failure accessibility tree, with its body in the row's value;
the test searched only labels. Displayed-content lookup now accepts static text
or noneditable row labels/values, excluding the editable composer. This attempt
is not full acceptance. All eight other ordinary/auth/synthetic checks passed;
the real auth recheck ran one XCTest in 120.205 seconds with eight checkpoints.

The second run `37953684385` at `4c39f93` passed all nine native UI checkpoints
in 359.194 seconds, including chat, real schedule, capture, saved review and
process recovery. Its final database assertion incorrectly required an active
Flow instance after completion. The local upstream persistence implementation
removes terminal instances; Bliss's execution endpoint explicitly reports FINISHED
when a terminal booking has no active row. The runner now observes the scheduled
human wait with three persisted task checkpoints and checks that the final booking
retains the same correlation. All other eight checks passed at this revision;
the failed overall job is not full marketplace acceptance.

### iOS marketplace merge checkpoint

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

The downloaded evidence matches the full head, all markers, original screenshots
and database results. Selected chat/completed/review images were visually reviewed
and retained. Local observer capture, identifier guarding and stop behavior also
passed, alongside redaction/process-exit and docs/hygiene checks. Current branch
is main; completed work is pushed without a codex/ prefix. #7/#12 stay open for
secondary iOS cancellation/dispute/connection-recovery. Physical devices, signing,
public deployment and live payments remain separate. Existing data and upstream
Flow were preserved. Completed Flow instances are intentionally not retained;
the durable wait snapshot and final correlation supply the persistence proof.

### Post-merge CI account blocker

Main documentation checkpoint `0cfecde` was pushed with reviewed screenshots.
The merge and documentation CI jobs were rejected before any steps ran.
[Main CI 37956907374](https://github.com/Stuaarts/Bliss/actions/runs/37956907374)
and [marketplace 37956907568](https://github.com/Stuaarts/Bliss/actions/runs/37956907568)
annotations report that recent account payments failed or the spending limit
needs increasing. Authentication and synthetic jobs were rejected likewise.
This does not establish which billing condition applies; the account owner must
check GitHub Billing & plans. No payment/settings changes or repeated reruns were
attempted. All nine checks passed on `e8c7724` before merge; diff verification
confirms main has identical app/API/test/runner/workflow source. Main execution
remains unverified until the external account blocker is resolved and checks rerun.
#7/#12 retain this blocker alongside the remaining secondary iOS acceptance.

## 2026-10-10 — operations readiness and correlated validation (#15)

Branch `15-operations-readiness` starts from main `f1421a3` in its own managed
checkout/build directory, independently of draft PR #39 (secondary iOS) and #40
(database recovery). See the [operations readiness plan](../operations/READINESS.md)
for topology/secrets, management access, telemetry, recovery, load, abuse/moderation
and data export/deletion/retention acceptance. Implemented foundations and pending
controls are explicit; retention periods, infrastructure and staffing are not
invented. Both prior drafts remain preserved and unmerged.

The new HTTP smoke reproduced endpoint validation bypassing the generic mapper:
invalid pagination returned Quarkus's validation report without body correlation.
An endpoint-specific mapper now uses the existing generic problem/UUID, preserving
500 for invalid return contracts. Five smoke cases cover health/OpenAPI, safe
validation correlation, return-contract failures, actual Prometheus recording and
80 category reads through four threads with distinct UUIDs and correct data.
The deliberately invalid return endpoint is test-only and excluded from the
packaged application. There is no tracing/exporter, rate limiter or privacy
lifecycle implementation in this slice.

The first full verify passed all 60 ordinary scenarios and the dispute restarts,
but one booking restart case killed the API with only two task checkpoints after
a fixed 6.5-second sleep. The test now waits up to 20 seconds for all three
persisted loop checkpoints before crashing; the original post-crash count,
identity, restoration and single-settlement assertions remain. This changes test
synchronization, not Flow or application behavior. Repeated full verification
passed against new disposable PostgreSQL 17.6 containers: 68 scenarios
(60 ordinary and 8 migration/packaged restart), zero failures/errors/skips, with Spotless clean.
Both booking restart cases and both dispute restart cases pass. The bounded
80-read smoke observed p50 2.852 ms, p95 7.481 ms and max 98.151 ms, including
HTTP/validation; no warmup, capacity or production SLO claim is made. Test-profile
OIDC uses an ephemeral signing key and workflow dispatch is disabled; packaged
restart cases separately exercise dispatch. No Flutter/native rerun is claimed.

Validation command: Java 21 `mvnw.cmd -B -ntp spotless:apply verify -Dquarkus.http.test-port=0`.
Reports/logs remain in the isolated checkout under `services/api/target`; the
initial failed run is retained as `operations-verify-first.log`, and the
successful full run as `operations-verify.log`. Documentation links,
generated-file/secret-pattern hygiene and `git diff --check` pass. The isolated
test resources were removed on exit; existing data is preserved.

At the user's request, GitHub account resolution and native iOS execution remain
at the final release gate. #7/#12/#15 remain open; no paid services, public hosting,
live payments, existing volumes or upstream Flow changes are authorized by this
operations plan.

## 2026-10-10 — production management role boundary (#15)

Branch `15-management-access` builds on draft PR #41 (`90d6abb`) in the same
isolated managed checkout. Default packaged production now gates the
non-application root and children with a shared HTTP permission requiring an
OIDC operator role. Marketplace customer/provider/admin roles do not imply that
role; public discovery and business authorization keep their existing policies.
Packaged restart probes now use ephemeral operator tokens. The local profile
explicitly disables this permission and keeps its loopback tools. See
[management access and probe instructions](../operations/MANAGEMENT_ACCESS.md).

The initial packaged check passed the endpoint/role matrix, method/path variants,
public catalog and provider authorization, but the missing-subject token returned
403 rather than 401. The fixture had used Quarkus's offline public-key path, which
does not apply the subject check in pinned 3.39.0. It now serves public JWKS on a
random loopback port, signs with a key ID and uses normal OIDC JWT verification.
Production issuer configuration and upstream sources are unchanged. The negative
test now gives the subjectless token the operator role and labels each invalid
case without logging tokens. A separate actual-local-profile test creates its
own PostgreSQL container, overrides the local datasource and disables dispatch.

That local-profile check initially caught an incomplete disabled permission entry:
Quarkus requires its policy definition even when disabled. Definitions now exist
consistently, with enforcement enabled only in prod and explicitly disabled for
the local demo. The focused local/packaged verification passed all five new
scenarios. Failsafe now initializes the same LogManager as Surefire before the
loopback JWKS fixture starts. The final full run includes signature tampering
as well as subject, issuer, audience, expiry and role checks.

Full Java 21 verification passed: `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0`, 73 scenarios (61 ordinary and 12 migration/packaged
cases), zero failures/errors/skips, with Spotless clean. The packaged management
matrix and all four booking/dispute recovery cases passed against fresh disposable
PostgreSQL 17.6 containers. Logs and XML reports remain under `services/api/target`;
the final log is `management-verify.log`. The initial failed checks are retained
as `management-package.log` and `management-local-failure.log`, followed by
`management-focused.log`. Documentation links, tracked/new-file hygiene and
`git diff --check` pass. No Flutter/native rerun is claimed.

No hosted networking, separate management listener, paid services, real operator
account or native acceptance is claimed.
PR #41 and drafts #39/#40 are preserved; the user-requested deferred account/CI
and iOS gate remains at the end. #7/#12/#15 remain open.

## 2026-10-10 — database outage and readiness recovery (#15)

Branch `15-database-outage` builds on draft PR #42 at `3cf3c33` in the existing
isolated managed checkout. `DatabaseOutageIT` launches the actual packaged prod
API, an ephemeral JWKS fixture and a new PostgreSQL 17.6 container. It checks the
unique drill ownership label and Testcontainers label before stopping/starting
that exact container. A random loopback binding preserves the JDBC address.
No existing database, volume or API is used as the fault target.

The scenario verifies initial liveness/readiness UP and saves a synthetic provider
through HTTP. During the database outage, an actual 503/DOWN readiness response
must name the database health check as DOWN; liveness stays 200/UP, metrics remain
available to the operator, and anonymous/customer management requests stay
401/403. The same API process must recover readiness after the same database
container starts, retain the saved profile ID/data/version and public catalog,
and successfully update/read the profile with its next version. Production pool
and health configuration are unchanged; HTTP timeouts do not count as health
evidence. See the [drill and operator response boundary](../operations/DATABASE_OUTAGE.md).

The first focused Java 21 verify passed the local management check and outage
scenario (two total), with Spotless clean. It observed detection in 335 ms and
readiness recovery in 426 ms, including Docker commands; these are workstation
observations, not service objectives. The final full run adds an explicit
post-recovery update and checks the named database health result directly.
Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify -Dquarkus.http.test-port=0`
passed all 74 scenarios (61 ordinary + 13 migration/packaged), with zero
failures/errors/skips and Spotless clean, in 5:19. The updated outage scenario
observed detection in 337 ms and readiness recovery in 421 ms, retaining the
same API process and passing the post-recovery write. Migrations/invariants,
management authorization, and all four booking/dispute restart cases pass.
Logs remain under `services/api/target/outage-focused.log` and `outage-verify.log`,
with XML reports in Surefire/Failsafe directories. Documentation links,
tracked/new-file hygiene and `git diff --check` pass. Owned temporary resources
were cleaned up; the existing PostgreSQL 16 container remains untouched.

This exercises a stopped database and pool recovery, not hung queries, network
partitions, failover, backup restore, active-workflow settlement, identity-provider
outage, hosted ingress or alert delivery/human response. No Flutter/native rerun,
public hosting, paid services, live payments, billing settings or upstream change
is claimed. Preserve drafts #39–#42 and keep #7/#12/#15 open; the user-requested
external CI/native gate remains deferred to the end.

## 2026-10-10 — bounded REST diagnostics and mapped-error log content (#15)

Branch `15-request-diagnostics` builds on draft PR #43 at `29d6424` in the same
isolated managed checkout. Matched REST response preparation now logs one INFO
event with generated request ID, allowlisted HTTP method, code-defined resource
operation, status and monotonic filter elapsed milliseconds. The event does not
read bodies, headers, query strings, raw paths or actor/resource IDs. Correlation
stays in request properties; there is no new MDC propagation or end-to-end timing
claim. Mapped API failures now log only request ID and exception type, omitting
exception messages, nested causes and throwables; generic HTTP problems are
unchanged. See [diagnostic scope and operator limits](../operations/REQUEST_DIAGNOSTICS.md).

Flow payload tracing is explicitly disabled in all profiles. The unmodified local
reference listener serializes workflow input/output and failure causes; `javap`
confirmed the same tracing configuration interface exists in pinned 1.2.0.CR1.
No upstream source, engine/persistence logic, dependency or schema was changed.
Other library/dispatcher logs still need review before external collection.

Focused Java 21 `mvnw.cmd -B -ntp spotless:apply test
-Dtest=RequestDiagnosticsTest,OperationsSmokeTest -Dquarkus.http.test-port=0`
passed eight scenarios, zero failures/errors/skips. The three new ordinary cases
capture actual HTTP log records: success/validation/matched missing paths omit
synthetic private headers/query/body/path/token/subject values; a test-only nested
private exception produces a correlated generic 500 with no logged throwable;
24 concurrent reads through four clients retain distinct IDs and exactly one
response event each. A new packaged case checks the real production JSON console
for three status/operation cases and absence of private sentinels from its process
segment. Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 78 scenarios (64 ordinary + 14
migration/packaged), zero failures/errors/skips, with Spotless clean, in 5:24.
The packaged JSON case, database outage recovery, management authorization and
all four booking/dispute process-restart cases pass. The full Maven log contains
no Flow trace-listener messages while ordinary booking/dispute workflows execute.
Logs remain in `services/api/target/diagnostics-focused.log` and
`diagnostics-verify.log`; XML reports and packaged API output remain under target.
Documentation links, tracked/new-file hygiene and `git diff --check` pass. Owned
test resources were cleaned up; existing services/data remain preserved.

This is bounded matched-REST diagnostics, not all HTTP traffic, a universal log
redactor, async workflow tracing or a managed telemetry service. The private
failure resource and capture fixtures remain test-only. No Flutter/native rerun,
public hosting, paid service, live payment, billing settings or existing data
change is claimed. Preserve drafts #39–#43; #7/#12/#15 and the user-requested
deferred final CI/native gate remain open.

## 2026-10-10 — bounded authenticated write/retry/restart workload (#15)

Branch `15-authenticated-workload` builds on draft PR #44 at `a302532` in the
existing isolated managed checkout. `AuthenticatedWorkloadIT` creates its own
PostgreSQL 17.6, ephemeral loopback JWKS and one packaged production API. Eight
synthetic customers book one offering through four client threads, with two
identical calls/keys per customer. The test requires eight 201/eight 200 responses
and exactly eight booking IDs. It waits for all eight WAIT_PROVIDER states and
three checkpoints each, force-kills only its API, checks persisted state, restarts
against the same database, and requires the same eight workflow correlations.

Concurrent customer/provider/outsider reads require 16 correct owner reads and
eight 404 cross-account denials. Sixteen duplicate cancellation submissions must
return their expected accepted event receipts. Terminal action replay and original
creation-key replay must not add records. HTTP payment views and read-only SQL
require eight VOIDED simulated payments at CAD 80.50, one AUTHORIZE and one VOID
per booking, eight processed cancellation events, and exactly one each of the
CREATED/WAIT_PROVIDER/CANCELLED history entries. Every recorded response has a
distinct generated request ID. See [scope and reproduction](../operations/AUTHENTICATED_WORKLOAD.md).

Focused Java 21 `mvnw.cmd -B -ntp spotless:apply verify -Dtest=OperationsSmokeTest
-Dit.test=AuthenticatedWorkloadIT -Dquarkus.http.test-port=0` passed six scenarios
(five smoke + one packaged workload), zero failures/errors/skips and clean
Spotless. The workload passed in 36.22 seconds, recording 419 API requests:
91 fixed + 328 status polls. Observed create p50/p95/max were
16.575/52.990/52.990 ms and cancellation 10.875/79.985/79.985 ms. These are small
local phase observations, not capacity or service objectives. Full Java 21
`mvnw.cmd -B -ntp spotless:apply verify -Dquarkus.http.test-port=0` passed all
79 scenarios (64 ordinary + 15 migration/packaged), zero failures/errors/skips,
with Spotless clean, in 5:54. That workload run recorded 251 API requests
(91 fixed + 160 polls); create p50/p95/max were 27.576/85.577/85.577 ms and
cancellation 12.062/75.482/75.482 ms. Its eight restored waits, ownership denials
and single-settlement assertions passed again. Existing migrations, management,
database outage, JSON diagnostics and all four booking/dispute restart scenarios
also passed, with no Flow trace-listener messages. Logs remain under
`services/api/target/workload-focused.log` and `workload-verify.log`.
The workload fixture is absent from the application jar. Documentation links,
tracked/new-file hygiene and `git diff --check` pass; owned test resources are
cleaned up and existing services/data preserved.

No application/dependency/schema/workflow changes were required by the
workload. It covers direct booking and simulated cancellation with eight restored
waits, not full endpoint mixes, saturation/soak, live identity/payment services,
multi-replica ownership or backup/dependency-fault settlement. No Flutter/native
rerun, paid/public deployment, billing settings, upstream source or existing data
change is claimed. Preserve drafts #39–#44 and keep #7/#12/#15 open; required
CI/native acceptance remains the user-requested final gate.

## 2026-10-10 — actual paused workflow backup/restore (#15)

Branch `15-workflow-backup-restore` builds on draft PR #45 at `c8e20ea` in the
existing managed checkout. `WorkflowBackupRestoreIT` creates an actual direct
booking through one packaged production API against owned PostgreSQL 17.6/JWKS.
It waits for WAIT_PROVIDER and three persisted task checkpoints, then stops the
sole worker before custom-format `pg_dump`. A distinct empty owned PostgreSQL
container receives `pg_restore --exit-on-error`; every public table's row count
and sorted content digest must match before API startup. The same pinned artifact
starts only against the restored database and retains the original workflow ID.

Cancellation, two terminal action replays and original creation-key replay retain
one cancelled booking, one processed event, one simulated AUTHORIZE/VOID and
exactly CREATED/WAIT_PROVIDER/CANCELLED history. The frozen source's complete
snapshot remains unchanged, still waiting with only AUTHORIZE. Temporary host
archive and owned runtime resources are removed. See the
[scope, fencing and reproduction](../operations/WORKFLOW_BACKUP_RESTORE.md).

Focused Java 21 `mvnw.cmd -B -ntp spotless:apply verify -Dtest=OperationsSmokeTest
-Dit.test=WorkflowBackupRestoreIT -Dquarkus.http.test-port=0` passed six scenarios
(five ordinary smoke + one packaged restore), zero failures/errors/skips and clean
Spotless, in 59.904 seconds. The restore case passed in 25.81 seconds and matched
all 28 tables. Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 80 scenarios (64 ordinary + 16
migration/packaged), zero failures/errors/skips, with Spotless clean, in 6:25.
The restore case passed again in 25.17 seconds with the same 28-table,
same-instance, single-settlement and unchanged-source assertions. Existing
migrations, management, database outage, JSON diagnostics, authenticated workload
and all four booking/dispute process-restart cases also passed. The full log has
no Flow trace-listener messages. The fixture is absent from the application jar;
no temporary host backup remains. Documentation links, tracked/new-file hygiene
and `git diff --check` pass; owned resources are cleaned up and existing services
preserved. Logs remain in
`services/api/target/workflow-backup-focused.log` and `workflow-backup-verify.log`.

This extends PR #40's structural synthetic recovery evidence with actual restored
Flow execution. No application/dependency/schema/workflow changes were needed.
It is one offline synthetic WAIT_PROVIDER cancellation on the same versions;
managed/online/PITR recovery, capture/refund/dispute/external reconciliation,
multiple restored waits, changed-version compatibility and approved RPO/RTO are
still open. No Flutter/native rerun, billing settings, public/paid hosting, live
payment, upstream source or existing project database change. Preserve drafts
#39–#45 and keep #7/#12/#15 open; CI/native acceptance remains the deferred final
gate.

## 2026-10-16 — aggregate workflow inbox metrics (#15)

Branch `15-workflow-backlog-metrics` builds on draft PR #46 at `17da5eb` in the
existing managed checkout. `WorkflowBacklogMetrics` periodically collects one
read-only SQL/transaction snapshot of pending booking starts/actions and dispute
starts/decisions. Four code-defined queue labels expose counts and oldest ages;
availability, monotonic snapshot age, last-attempt success and a process-local
failure counter make stale/unknown observations visible. Twelve custom Prometheus
series contain no record IDs, tokens, actor text or Flow payloads. Scrapes use
memory only; failure retains last good values rather than reporting false zero.
Default collection is ten seconds with a three-second startup delay, skipped
overlaps and a two-second SQL timeout request. Ordinary test-profile collection
is disabled. Existing production operator authorization is unchanged. See the
[contract, sampling limits and reproduction](../operations/WORKFLOW_BACKLOG_METRICS.md).

Docker initially hit the known Windows stale `sailor-ingest.sock` startup failure.
After the failed Desktop stopped, the existing guarded helper preserved runtime
directories and started the Linux engine (29.8.0); no reinstall or data reset.
A test compilation error from treating the pinned SimpleMeterRegistry as
AutoCloseable was corrected to explicit cleanup before successful verification.
Focused Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=WorkflowBacklogMetricsTest,OperationsSmokeTest -Dit.test=WorkflowBacklogMetricsIT
-Dquarkus.http.test-port=0` passed seven scenarios (six ordinary + one packaged),
zero failures/errors/skips with Spotless clean, in 55.760 seconds. The packaged
case passed in 20.53 seconds. Its one-second test-only collection interval observes
one HTTP-created pending booking start/action with positive ages, anonymous and
marketplace-role denial, and twelve fixed aggregate series without private values.
Stopping only its labelled database retains counts, freezes oldest ages, advances
snapshot age and records failure while actual operator scrapes still return 200.
Database recovery resumes collection in the same API process; restarting only
the owned API with dispatch enabled drains the queues and preserves event replay.
The ordinary test checks disabled collection/unknown values without database use.
Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips, with Spotless clean, in 5:23.
The packaged metrics case passed again in 19.88 seconds with its pending,
outage/freshness, same-process collection recovery, operator and drain assertions.
Existing migrations, management, diagnostics, database outage, eight-workflow
contention/restart, real backup/restore and all four booking/dispute restart cases
also passed. The full log has no Flow trace-listener messages. Test fixtures are
absent from the application jar; documentation links, tracked/new-file hygiene
and `git diff --check` pass. Owned resources are cleaned up and existing services
preserved. Logs remain in `services/api/target/backlog-focused.log` and
`backlog-verify.log`.

No dependency/schema/upstream changes, new endpoint, workflow transition or
payment behavior change. This measures admission inboxes, not engine failures,
healthy human waits, capacity or alert effectiveness. The positive packaged case
covers booking queues; dispute queues are observed empty. Private collection,
engine failure/wait-age metrics, dashboards, threshold approval and alert/operator
rehearsal remain open. No Flutter/native rerun, public/paid deployment, live
payment, billing settings or existing project database change. Preserve drafts
#39–#46 and keep #7/#12/#15 open; CI/native acceptance stays at the deferred final
gate.

## 2026-10-16 — durable unresolved dispute-failure metrics (#15)

Branch `15-dispute-failure-metrics` builds on draft PR #47 at `2823027` in the
existing managed checkout. The cached read-only snapshot now includes count and
oldest saved failure age for `dispute.workflow_failed_at IS NOT NULL AND
resolved_at IS NULL`. The two gauges have no labels or private content, and share
the existing collection availability/freshness/failure contract. The four inbox
labels/twelve backlog series remain; fourteen custom aggregate series now exist.
The same single SQL statement/transaction reads all five aggregates. Scrapes
remain memory-only and failed collection retains last good values.

The existing DisputeFailureListener already records a content-free failure marker.
The read-only upstream JPA reference and actual regression confirm that failed
engine checkpoints are removed, so engine-row counts alone miss this condition.
No listener/schema/dependency/upstream, failure-recording, settlement or recovery
behavior changed. See the [contract and remaining limits](../operations/WORKFLOW_BACKLOG_METRICS.md#durable-dispute-failure-signal).

Focused Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=DisputeTest,WorkflowBacklogMetricsTest -Dit.test=WorkflowBacklogMetricsIT
-Dquarkus.http.test-port=0` passed nine scenarios (eight ordinary + one packaged),
zero failures/errors/skips and clean Spotless, in 1:24. The seven dispute scenarios
passed in 29.63 seconds; their test-only profile enables one-second collection.
After four actual exhausted simulated settlement attempts, SQL verifies a saved
unresolved marker and zero engine rows for that case. Actual Prometheus output
must rise by one failed case with positive oldest age and exactly two fixed,
unlabeled failure samples. The case/decision remain UNDER_REVIEW/unprocessed and
the payment still has only AUTHORIZE. The ordinary metrics test verifies both
new gauges are NaN before successful collection. The packaged metrics case passed
in 22.52 seconds with fourteen series, empty failure gauges, operator access,
database outage retention/freshness, same-process collection recovery and queue
drain/replay. Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips and clean Spotless, in 5:41.
The positive retry-exhaustion/failure-marker/Prometheus assertions and packaged
fourteen-series checks passed again. Migrations, management, database outage,
diagnostics, authenticated workload, backup/restore and all four booking/dispute
restart scenarios passed; no Flow trace-listener messages appear in the full log.
Test-only metrics profile and payment fault fixtures are absent from the
application jar. Documentation links, tracked-file hygiene and `git diff --check`
pass; owned resources are cleaned up and existing services/data preserved. Logs remain in
`services/api/target/dispute-metrics-focused.log` and `dispute-metrics-verify.log`.

These gauges cover saved unresolved dispute failures, not all booking/engine
faults, marker-persistence failures, healthy human waits, automatic recovery or
alert effectiveness. Positive failure evidence uses real Quarkus/PostgreSQL/Flow
with the test-only payment fault alternative; the packaged outage case observes
empty failure gauges. No live gateway, Flutter/native rerun, public/paid hosting,
external collection, billing settings or existing-data changes. Preserve drafts
#39–#47 and keep #7/#12/#15 open; CI/native acceptance remains the deferred final
gate.

## 2026-10-16 — booking decision-wait metrics (#15)

Branch `15-booking-wait-metrics` builds on draft PR #48 at `2e38d3b` in the
existing isolated managed checkout. The cached read-only SQL snapshot now includes
current WAIT_PROVIDER/WAIT_CUSTOMER booking counts, oldest latest matching
transition ages and missing-history counts. Two code-defined stage labels produce
six additional samples (twenty total custom series). No record IDs, actor content
or Flow payloads enter metric labels/values. A stage with any missing matching
history retains its booking count, increments the missing count and reports NaN
age rather than an invented/partial value. Empty stages report zero after success;
unknown/stale/failed collection uses the existing shared snapshot contract.
See the [contract and test-only history fault](../operations/WORKFLOW_BACKLOG_METRICS.md#booking-decision-waits).

Focused Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=WorkflowBacklogMetricsTest,OperationsSmokeTest
-Dit.test=WorkflowRestartIT,WorkflowBacklogMetricsIT -Dquarkus.http.test-port=0`
passed nine scenarios (six ordinary + three packaged), zero failures/errors/skips
with clean Spotless, in 2:02. The actual quote/rejection and direct/completion
restart cases passed in 57.90 seconds: provider/customer counts and positive
transition ages survive process crashes with unchanged workflow IDs, terminal
records leave the stage metrics, and ledger checks retain one AUTHORIZE and one
VOID or CAPTURE. The owned quote fixture temporarily removes one history row,
requires count one/missing one/age NaN, restores the exact row, then proceeds
through crash/restart and rejection. This is isolated reversible fault injection,
not an operator command or project-data edit. The twenty-series packaged metrics
outage/access/drain scenario passed in 22.49 seconds with empty wait stages; the
ordinary test requires six unknown NaN samples before successful collection.
Only these owned test scenarios override collection to one second; production
defaults remain ten seconds. Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips, with Spotless clean, in 5:49.
Both strengthened booking restart cases passed again in 60.24 seconds, including
missing-history/NaN and exact-row restoration, provider/customer age continuity,
terminal exclusion and single settlement. Dispute failure, migrations, management,
database outage, diagnostics, authenticated workload, backup/restore and dispute
restart assertions also passed. The full log has no Flow trace-listener messages.
Test fixtures are absent from the application jar. Documentation links,
tracked-file hygiene and `git diff --check` pass; owned resources are cleaned up
and project services/data preserved. Logs remain in
`services/api/target/wait-metrics-focused.log` and `wait-metrics-verify.log`.

The gauges describe saved booking decision states, not healthy engine waits or
an approved deadline/SLO. Scheduling/in-progress/terminal/disputed states,
dispute-party/admin waits, broader engine failures, hosted collection/dashboards/
alerts and operator acceptance remain separate. No schema/dependency/upstream,
workflow transition, settlement or automatic recovery change. No Flutter/native
rerun, public/paid hosting, live payment, billing settings or existing project
database change. Preserve drafts #39–#48 and keep #7/#12/#15 open; required
CI/native acceptance remains at the deferred final gate.

## 2026-10-16 — dispute evidence/admin-review wait metrics (#15)

Branch `15-dispute-wait-metrics` builds on draft PR #49 at `db9ba62` in the
existing managed checkout. Six new fixed-stage samples expose current evidence/
admin-review case counts, latest matching transition ages and missing-history
counts. The read-only statement now computes all nine inbox/failure/wait
aggregates in the same snapshot; twenty-six custom time series use only fixed
queue/stage labels. Missing history includes the case in the count and makes the
entire stage's oldest age NaN. Empty stages are zero after success; existing
shared availability/freshness/failure behavior and operator access remain.
See the [dispute wait contract](../operations/WORKFLOW_BACKLOG_METRICS.md#dispute-evidence-and-admin-review-waits).

Focused Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=WorkflowBacklogMetricsTest,OperationsSmokeTest
-Dit.test=DisputeRestartIT,WorkflowRestartIT,WorkflowBacklogMetricsIT
-Dquarkus.http.test-port=0` passed eleven scenarios (six ordinary + five packaged),
zero failures/errors/skips with Spotless clean, in 3:34. Both actual dispute
outcomes passed in 93.21 seconds: evidence/admin-review counts and positive ages
survive distinct API crashes with unchanged workflow IDs; resolved cases leave
both stages; evidence, decision replay and single CAPTURE/REFUND ledger assertions
remain valid. One owned fixture removes a dispute history row, requires count
one/missing one/NaN age, restores the exact saved row, then continues through both
crashes and resolution. The twenty-six-series access/outage/drain test passed in
19.93 seconds with empty dispute waits; both booking restart cases passed in
57.99 seconds. The ordinary test includes six new unknown/NaN samples.

Booking and dispute history faults/HTTP assertions now share the test-only
DecisionWaitMetrics helper. Table/status/stage choices are code-defined; record
IDs are bound parameters. Restoration uses saved ID/actor/timestamp and also
tolerates a failure before deletion. The booking helper consolidation followed
the focused run. Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips with Spotless clean, in 6:00.
Both dispute outcomes and both booking outcomes passed using the final shared
helper, including missing-history NaN, saved-row restoration, age continuity,
resolved/terminal exclusion and single-settlement assertions. The twenty-six-series
access/outage/drain test, dispute failure, migrations, management, diagnostics,
authenticated workload and backup/restore also passed. The full log has no Flow
trace-listener messages. Test helper/profile/payment fixtures are absent from
the application jar. Documentation links, tracked/new-file hygiene and
`git diff --check` pass; owned resources are cleaned up and project data preserved.
Logs remain in `services/api/target/dispute-wait-focused.log` and
`dispute-wait-verify.log`.

Counts describe saved states, including failed cases that remain under review;
cross-check the durable failure signal and authorized case/execution views.
They do not classify engine health, approve settlement or define a review SLA.
Broader faults, hosted collection/dashboards/alerts and operator acceptance remain
open. No schema/dependency/upstream, workflow transition, policy, settlement or
automatic recovery change. Fixture manipulation is isolated synthetic fault
injection, not a project-data edit or operator procedure. No Flutter/native rerun,
public/paid hosting, live payment or billing settings change. Preserve drafts
#39–#49 and keep #7/#12/#15 open; CI/native acceptance remains the deferred final
gate.

## 2026-10-16 — durable booking failure handling (#15)

Branch `15-booking-failure-handling` builds on draft PR #50 at `3088da5` in the
existing managed checkout. Flow removes failed checkpoints; an exhausted
authorization previously left an unacknowledged start eligible for reclaim after
thirty seconds. V11 adds nullable UTC `booking.workflow_failed_at`; the lifecycle
listener records canonical active ordinary failures once and increments version.
Terminal/disputed bookings are excluded. No historical failure is inferred from
missing checkpoints. Scheduled dispatch, the locked claim and restored ticks skip
marked bookings. Owner execution returns ERROR while a booking remains active
and ordinary; historical markers do not override terminal/disputed outcomes.
New ordinary actions are rejected with the existing generic 409; exact prior
receipts retain their processed flag and conflicting reuse remains 409. The
booking start callback logs identifiers without an exception/payload. Two cached
unlabeled failure gauges share existing availability/freshness/failure semantics;
the single statement now computes ten aggregates and twenty-eight custom samples.
See [booking failure handling](../operations/BOOKING_FAILURES.md) and the
[metric contract](../operations/WORKFLOW_BACKLOG_METRICS.md#durable-booking-failure-signal).

Focused Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=BookingWorkflowTest,WorkflowBacklogMetricsTest
-Dit.test=DatabaseInvariantsIT,WorkflowBacklogMetricsIT
-Dquarkus.http.test-port=0` passed 13 scenarios (eight ordinary + five migration/
packaged), zero failures/errors/skips with Spotless clean, in 2:03. Both actual
engine failure cases exhaust all four simulated AUTHORIZE/VOID attempts after
ledger writes. Transactions roll back; the failure marker survives checkpoint
removal; aged claims keep their workflow ID; guarded ticks do not act; owner
ERROR/outsider denial, new-action rejection, receipt replay, stable marker and
positive failure-count delta/age pass. Existing transient-retry and unmarked
abandoned-claim recovery pass. An initial test expected a custom 409 detail;
correcting the assertion to the existing generic problem contract produced this
green focused run. The problem mapper was not changed.

The packaged metrics drill passed in 38.44 seconds: existing operator access,
outage/frozen snapshot, same-process collection recovery and drain/replay remain.
A separate synthetic saved-marker phase restarts a distinct API process with
scheduled dispatch enabled and verifies retained count/age/instance, no reclaim
or authorization, ERROR/action denial, and exclusion of an older terminal marker.
This phase is persistence fault injection, not an actual packaged gateway failure.
Populated V10-to-V11 adds only the nullable UTC marker, preserves all old booking
values and other tables, performs no failure backfill and repeats as a no-op.
Existing populated V8/V9 boundary checks explicitly retain their V10 target;
the new populated V10 test covers V11. Before full verification, terminal execution
precedence and start-acknowledgement assertions were tightened; the focused run
predates those final assertions.

Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 85 scenarios (67 ordinary + 18 migration/
packaged), zero failures/errors/skips, in 6:27, finishing at 15:25:19 -04:00.
Spotless keeps 85 Java files clean. Final terminal execution precedence and
start-acknowledgement assertions pass. Both actual failure cases, populated V11
migration, synthetic marker restart (35.29 seconds), both booking restart outcomes
(57.48 seconds) and both dispute restart outcomes (92.11 seconds) pass, alongside
management access, diagnostics, authenticated workload, outage recovery and the
28-table paused-workflow restore/single simulated VOID. No Flow trace-listener
messages occur. V11 is packaged; test identity/profile/payment alternatives and
helpers are excluded from the application jar. Local Markdown links, tracked/new
file hygiene and `git diff --check` pass; owned resources are cleaned up and
existing project data preserved. Logs remain in
`services/api/target/booking-failure-focused.log` and `booking-failure-verify.log`.

No automatic recovery/reset, historical repair, dependency/upstream, business
transition or settlement-policy change. A marker-write failure is outside these
guards. An older binary ignores the marker and must not run dispatch against
marked bookings; the operator guide requires compatible roll-forward/recovery
review. No Flutter/native rerun, public/paid hosting, live gateway, billing change
or existing project database migration. Preserve drafts #39–#50 and keep
#7/#12/#15 open; required CI/native acceptance remains at the deferred final gate.

## 2026-10-18 — dispute backup/restore with admitted decision (#15)

Branch `15-dispute-backup-restore` builds on draft PR #51 at `9f30ca3` in the
existing managed checkout. The prior backup drill resumed one WAIT_PROVIDER
booking; dispute waits only had same-database process-restart coverage. Two new
packaged cases now restore actual DisputeFlow data/checkpoints into distinct empty
PostgreSQL 17.6 targets. The evidence-wait path restores an authorized payment
and two customer evidence records, then admits provider evidence and an independent
admin decision for one simulated CAPTURE. The admin-review path restores a
captured payment, three evidence rows and a customer-favored decision admitted
through actual HTTP before backup, then records one simulated REFUND.

The admin fixture stops the source API, restarts with both dispatch and automatic
checkpoint restoration disabled, admits the decision and proves it remains
unprocessed beyond a polling interval. Disabling dispatch alone does not pause
restored instances. Both flags are test-process overrides; defaults are unchanged.
The source exits before snapshot/restore. Every public table's row count and
sorted row digest must match before candidate startup. The candidate first starts
with both controls disabled to verify exact evidence/receipt replay, saved wait
age, workflow identity, cross-account denial and the pending decision. Only the
restored worker then resumes. Resolution, duplicate decision receipt/timestamp,
one AUTHORIZE/one CAPTURE and conditional one REFUND are checked. The stopped
source's full snapshot remains unchanged in its original wait. See the
[recovery contract](../operations/DISPUTE_BACKUP_RESTORE.md).

The new test-only `WorkflowBackup` helper consolidates the existing booking drill's
quiesced dump, fresh-target/empty-schema checks, all-table comparison and temporary
archive cleanup. It creates its own archive and removes it before returning,
including failures. Comparison failures do not print digest maps. The test-only
`DisputeRecoveryFixture` extracts the unchanged HTTP booking/dispute lifecycle
used by both old restart outcomes and the new restore cases. Existing checkpoint,
wait/history and single-settlement assertions remain. The dispute restart client
now closes explicitly.

Docker was stopped at turn start. The existing guarded helper preserved stale
socket-only runtime directories and started Docker 29.8.0 without reset/reinstall
or volume changes. Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=OperationsSmokeTest
-Dit.test=DisputeBackupRestoreIT,WorkflowBackupRestoreIT,DisputeRestartIT
-Dquarkus.http.test-port=0` passed ten scenarios (five ordinary + five packaged),
zero failures/errors/skips, Spotless clean, in 5:06. Both new restore paths passed
in 119.4 seconds, both dispute restart outcomes in 124.3 seconds, and the booking
restore/cancellation case in 24.76 seconds. During the focused run, final
digest-comparison diagnostics and client cleanup were tightened; the full run
below checks the final compiled sources.

Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 87 scenarios (67 ordinary + 20 migration/
packaged), zero failures/errors/skips, Spotless clean, in 10:34, finishing at
23:21:46 -04:00. Spotless keeps 88 Java files clean. Both new dispute restore
paths passed in 124.3 seconds; both dispute restart outcomes in 126.4 seconds;
booking restore in 25.22 seconds; both booking restart outcomes in 84.10 seconds.
Final comparison diagnostics and client cleanup are included. Durable failure,
management, diagnostics, authenticated workload, outage recovery, all 28 metrics
samples and populated/empty migration checks also pass. No Flow trace-listener
messages occur. Recovery helpers, test identity and payment alternatives are
absent from the application jar. No temporary host backup remains. Local Markdown
links, tracked/new-file hygiene and `git diff --check` pass; owned resources close
and existing project services/data remain preserved. Logs remain in
`services/api/target/dispute-backup-focused.log` and `dispute-backup-verify.log`.

No application code, runtime defaults, schema, dependency/upstream, business
transition or settlement-policy change. This is one synthetic case per isolated
source/target pair on pinned versions, not managed/online/PITR, simultaneous or
in-flight settlement recovery, real-provider/identity reconciliation, least-privilege
database roles, RPO/RTO or operator acceptance. No Flutter/native rerun, public/paid
hosting, live payment, billing change or existing project database migration.
Preserve drafts #39–#51 and keep #7/#12/#15 open; required CI/native acceptance
remains at the deferred final gate.

## 2026-10-19 — restricted runtime database-role rehearsal (#15)

Branch `15-runtime-database-roles` builds on draft PR #52 at `4d2ad30` in the
existing managed checkout. A new guarded `psql` policy applies post-migration
runtime grants to a dedicated database. It checks the connected database,
restricted distinct roles, no runtime role memberships/ownership, exact migration
schema ownership and the existing history table. It removes PUBLIC/direct runtime
CREATE/TEMP and table/sequence rights before granting CONNECT, schema USAGE,
application DML and sequence use. Flyway history is SELECT-only. No credentials
are embedded or provisioned by the policy; no existing database/role is changed.
See [database role contract](../operations/DATABASE_ROLES.md).

The first integration case creates separate generated logins in its owned
PostgreSQL container, migrates to V10 with the owner, applies grants and verifies
runtime migration fails with V10/history/column state intact. The migrator then
applies V11; reapplying the policy succeeds. Wrong database, owner, privileged and
member roles are refused without changing the booking ACL. Twelve actual runtime
operations fail with SQLSTATE 42501, covering schema/table creation, ALTER/DROP,
TEMP, TRUNCATE, role switch/grant/elevation and three history writes. An initial
negative test caught PostgreSQL 17 psql returning success for `\quit 3`; deliberate
SQL refusal under ON_ERROR_STOP now returns status 3. The corrected focused run
passes, including the refusal/unchanged-permission assertions.

The second integration case disables the migration login before API startup and
verifies new migration connections fail. The packaged API receives runtime
credentials, explicitly disables startup migration and enables read-only Flyway
validation. Server session checks require runtime connections and zero migrator
connections. Actual HTTP identity/provider/offering, booking authorization/work
and dispute admission persist checkpoints. A distinct API process retains the
same dispute instance, wait age and evidence; provider evidence and an independent
admin decision settle with one AUTHORIZE/one CAPTURE and processed receipt replay.
Privileged-operation denial still holds afterward. The shared test launcher has
an explicit credentials overload; existing callers retain their prior behavior.

Docker was stopped at turn start; the guarded helper preserved stale socket-only
runtime directories and started the daemon without reset/reinstall or volume
changes. Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=OperationsSmokeTest -Dit.test=RuntimeDatabaseRolesIT
-Dquarkus.http.test-port=0` passed seven scenarios (five ordinary + two packaged),
zero failures/errors/skips, Spotless clean, in 1:28. Both role cases passed in
49.05 seconds. Generated passwords stay in the test process/owned containers and
are not printed in proof output.

Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passed all 89 scenarios (67 ordinary + 22 migration/
packaged), zero failures/errors/skips, Spotless clean, in 11:43, finishing at
08:52:13 -04:00. Both role cases pass in 49.71 seconds with twelve denied runtime
operations before/after actual writes, repeatable policy application and all
refusal guards. Existing dispute restores/restarts, booking restore/restarts,
durable failures, management, diagnostics, workload/outage, all 28 metrics samples
and migrations remain green through the launcher overload. Spotless keeps 89
Java files clean. No Flow trace-listener messages occur. Role/recovery/identity/
payment fixtures and administrator grants SQL are excluded from the application
jar. Temporary backup archives are absent; owned resources are cleaned up and
existing project services/data preserved. Documentation links, tracked/new-file
hygiene and `git diff --check` pass. Logs remain in
`services/api/target/runtime-roles-focused.log` and `runtime-roles-verify.log`.

No application code/default, schema, dependency/upstream, business transition or
settlement-policy change. This is tested role separation for the current public
schema, not per-tenant isolation, a compromised-runtime data boundary, managed
secret storage, hosted migration orchestration, credential/session revocation or
production acceptance. NOLOGIN does not terminate existing sessions; the test
closes migration connections first. No Flutter/native rerun, public/paid hosting,
live gateway, billing change or existing project database migration. Preserve
drafts #39–#52 and keep #7/#12/#15 open; current-head CI/native acceptance remains
at the deferred final gate.


## 2026-10-19 — runtime password and session revocation rehearsal (#15)

Branch `15-runtime-credential-rotation` builds on draft PR #53 at `3a7fb1b`
in the existing managed checkout. Docker was already healthy; no repair/reset was
needed. The new integration case uses generated disposable credentials and SCRAM
host authentication. It creates a real booking and evidence-wait dispute with
restricted runtime access, saves checkpoints and stops the sole worker. A paused
probe API disables both dispatch and auto-restore before authentication changes.
The replacement password rejects the old password; NOLOGIN rejects the new one.
An established JDBC session and pooled API readiness remain usable after both
changes. Explicit administrator termination is bound to the owned database and
runtime role. The observer loses its connection; the old API becomes unready with
HTTP 503/database DOWN, while its process and liveness remain UP. All public
records retain their count/digest during the fenced interval.

After stopping the old probe, the login is re-enabled and the old password still
fails. A new packaged process uses the replacement runtime secret, with migration
login still disabled, startup migration off and validation on. The same case/
instance, transition age and evidence survive without a failure marker; provider
evidence and an independent admin decision resolve once through simulated CAPTURE.
Decision replay stays processed and privileged operations remain denied. A shared
test-only role fixture replaces duplicated provisioning/grants/denial helpers;
existing role tests retain their behavior. See
[rotation procedure and limits](../operations/CREDENTIAL_ROTATION.md).

The initial negative guard detected four host trust rules left by image initdb
when only POSTGRES_HOST_AUTH_METHOD was selected. The owned fixture now also sets
POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256; it requires all host rules to use
SCRAM and actual server authentication SQLSTATEs. No project configuration or
credentials were changed to correct the fixture.

Corrected Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=OperationsSmokeTest -Dit.test=RuntimeDatabaseRolesIT,RuntimeCredentialRotationIT
-Dquarkus.http.test-port=0` passes eight scenarios (five ordinary + three
packaged), zero failures/errors/skips, Spotless clean, in 2:27. Rotation passes in
58.80 seconds and both existing role cases in 47.67 seconds. All authentication,
revocation, unchanged-record, readiness, restored workflow and single-settlement
assertions pass. Generated credentials are not printed in proof output.
Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passes all 90 scenarios (67 ordinary + 23 migration/
packaged), zero failures/errors/skips, Spotless clean, in 12:27, finishing at
09:14:59 -04:00. Rotation passes again in 52.95 seconds, both role cases in
48.79 seconds, dispute backup/restore in 122.5 seconds, both dispute restart
outcomes in 119.3 seconds, metrics in 52.83 seconds, booking restore in 24.61
seconds and both booking restart outcomes in 86.61 seconds. Spotless keeps 91
Java files clean. All prior ownership, workload/outage, durable failure and
migration evidence remains green. No Flow trace-listener messages occur. The
application jar excludes role/rotation/recovery/identity/payment fixtures and
administrator grants SQL. Temporary backup archives are absent; owned resources
close and existing project services/data remain preserved. Documentation links,
tracked/new-file hygiene and `git diff --check` pass. Logs remain in
`services/api/target/credential-rotation-focused.log` and
`credential-rotation-verify.log`. Exact pushed-head and remote-run evidence is
recorded in the stacked draft PR and milestone updates on #15/#12.

No application code/default, schema, dependency/upstream, business transition or
settlement-policy change. This is controlled local revocation/restart evidence,
not managed secret delivery, hosted ingress fencing, cluster-wide revocation,
zero-downtime rotation, unfenced workflow outage recovery or operator acceptance.
No Flutter/native rerun, public/paid hosting, live gateway, billing change or
existing project database migration. Preserve drafts #39–#53 and keep #7/#12/#15
open; required current-head CI/native acceptance remains at the deferred final gate.


## 2026-10-19 — standalone migration command (#15)

Branch `15-migration-command` builds on draft PR #54 at `c742f4b` in the
existing managed checkout. Docker is healthy; no repair/reset is needed.
`DatabaseMigration` is a plain Java entry point shipped in the existing fast-jar,
using its existing Flyway/PostgreSQL dependencies. It never starts Quarkus, HTTP,
OIDC, CDI, schedulers or Flow restoration. Dedicated migration environment
variables and an explicit expected database name select the connection. A bound
preflight query checks actual database/user, restricted attributes/no memberships,
public schema ownership and ownership of existing public relations before
migration. Only the artifact's versioned SQL, validate and migrate are exposed;
clean/repair/baseline/undo, extra options, alternate locations, out-of-order work
and automatic baselining are unavailable. Runtime defaults remain unchanged.

The two packaged tests invoke the actual shipped class in distinct Java processes
against owned SCRAM PostgreSQL databases, with misleading runtime/local/Flyway
environment settings to verify isolation. The fresh case checks V11/28 tables,
no demo identities/providers/workflows, repeat no-op and validation. After grants
and migration NOLOGIN, the real restricted API starts and writes a customer.
The populated V10 case tests missing configuration, wrong database/password,
runtime/admin/member-role refusal, unsafe commands, pending validation, checksum
drift and newer unresolved history. Full public count/digest snapshots stay
unchanged across refused attempts. Only the fixture reverses its injected
corruption; then the command applies V11 and preserves the synthetic profile.
Successful validation uses the PowerShell wrapper on Windows as well.
See [operator procedure](../operations/DATABASE_MIGRATION_COMMAND.md).

The initial output guard detected a bundled JBoss logging-provider warning after
successful/refused command execution. Initializing its log manager before using
Flyway fixes the standalone logging setup. Flyway's SQL/connection-bearing logs
are suppressed in this separate process; output is limited to fixed stage/action/
count results without exception messages, credentials or SQL. This does not alter
the API logger or prove privacy for external deployment/database tools.

Corrected focused Java 21 verification passes seven scenarios (five ordinary +
two packaged), zero failures/errors/skips, Spotless clean, in 1:00. Both command
cases pass in 25.90 seconds, including PowerShell validation on Windows and exact
fixed-output checks across all refusals/successes. A separate wrapper invocation
with missing dedicated configuration emits only the fixed configuration failure
and preserves Java exit status 2. Final results follow below.

The initial full run completed 91 of 92 scenarios successfully; the existing
metrics drill expected a cancelled execution FINISHED after it had already
stopped/restarted the API while checkpoint cleanup could still be RUNNING.
The fixture now waits, with a twenty-second bound, for FINISHED and no checkpoint
before that stop. The later terminal-marker FINISHED assertion remains unchanged;
ERROR or missing cleanup still fails. This changes test synchronization only.
The failed log/report are retained under `target/migration-command-initial-failed*`.
Corrected Java 21 verification with `OperationsSmokeTest` and
`DatabaseMigrationCommandIT,WorkflowBacklogMetricsIT` passes eight scenarios
(five ordinary + three packaged), zero failures/errors/skips, Spotless clean,
in 1:56. Migration cases pass in 26.13 seconds and the corrected metrics/restart
case in 56.75 seconds, retaining the post-restart FINISHED assertion.
Final Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passes all 92 scenarios (67 ordinary + 25 migration/
packaged), zero failures/errors/skips, Spotless clean, in 12:23, finishing at
09:56:20 -04:00. Both command cases pass in 24.91 seconds; the corrected metrics
case passes in 52.70 seconds. All existing role/rotation, restart/backup restore,
ownership, failure, workload/outage and migration checks remain green. Spotless
keeps 93 Java files clean. The shipped jar contains the command and excludes
migration/role/recovery/identity/payment fixtures and administrator grants SQL.
No Flow trace-listener messages occur; temporary backup archives are absent.
Documentation links, tracked/new-file hygiene and `git diff --check` pass.
Owned resources close and existing project services/data remain preserved.
Final/focused/correction/initial-failure logs remain under
`services/api/target/migration-command-*`. Exact pushed-head, remote-run and
backlog evidence is recorded in the stacked draft PR and milestone updates
on #15/#12.

No additional dependency, application startup default, migration/schema, business
transition, payment policy or upstream change. Only disposable generated roles/
credentials/data are used. Hosted migration job orchestration, immutable artifact
distribution, managed secrets, ingress/worker fencing, TLS/network adoption,
rollout/rollback and operator acceptance remain open. No public/paid hosting,
live gateway, billing change, existing database migration or Flutter/native rerun.
Preserve drafts #39–#54; #7/#12/#15 stay open and required current-head CI/native
acceptance remains at the deferred final gate.


## 2026-10-19 — REST write-admission pause and maintenance rehearsal (#15)

Branch `15-maintenance-admission` builds on draft PR #55 at `daba8f9` in the
existing managed checkout. Docker is healthy; no repair/reset is needed.
The new startup setting `bliss.operations.writes-paused` defaults false. A REST
request filter, after server correlation, refuses matched non-GET/HEAD/OPTIONS
requests with a fixed application/problem+json 503 and no-store. It invokes no
resource/body validation, admits no receipt, and includes no submitted values.
The readiness check `API write admission` becomes DOWN independently from database
health; liveness and existing management authorization remain unchanged. The
filter shares the readiness-qualified configuration bean. Initial CDI validation
caught the missing readiness qualifier at injection; explicit qualification fixes
the wiring before any focused tests run.
See [procedure and limits](../operations/MAINTENANCE_ADMISSION.md).

Two ordinary tests cover public GET/HEAD/OPTIONS, anonymous private-read denial,
readiness DOWN/database UP/liveness UP, multiple roles and correlated POST/PUT/
DELETE refusal before malformed JSON parsing, with user count unchanged. The
packaged restricted-role case disables the migrator login, creates a real booking
and evidence-wait dispute, saves checkpoints and stops the worker. A separate
maintenance process uses the admission flag plus disabled dispatch and engine
auto-restore. Operator health/metrics and owned reads work; anonymous/outsider
private reads remain denied. Six refused writes and two dispatch intervals leave
all public table counts/digests unchanged. A normal process reopens admission and
restores the same instance/evidence/age. Previously rejected evidence/decision
keys are admitted, one simulated AUTHORIZE/CAPTURE settles the case and decision
replay stays processed. Database privilege denials remain. The shared launcher
can expect readiness 503; existing callers still require 200, and the new drill
separately verifies admission DOWN/database UP.

Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dtest=OperationsSmokeTest,WriteAdmissionTest -Dit.test=MaintenanceAdmissionIT
-Dquarkus.http.test-port=0` passes eight scenarios (seven ordinary + one
packaged), zero failures/errors/skips, Spotless clean, in 1:48. Both new ordinary
cases pass in 10.28 seconds and the packaged maintenance case in 61.64 seconds.
Full Java 21 `mvnw.cmd -B -ntp spotless:apply verify
-Dquarkus.http.test-port=0` passes all 95 scenarios (69 ordinary + 26 migration/
packaged), zero failures/errors/skips, Spotless clean, in 14:08, finishing at
10:36:56 -04:00. The maintenance case passes in 58.72 seconds; both ordinary
admission cases in 15.25 seconds. All prior role/rotation, migration-command,
backup/restart, ownership, failure, workload/outage and metrics checks remain
green through the shared launcher. Spotless keeps 97 Java files clean. The jar
contains the admission controls with default false plus the existing migration
command, and excludes maintenance/role/recovery/identity/payment fixtures and
administrator SQL. No Flow trace-listener messages occur; temporary backup
archives are absent. Documentation links, tracked/new-file hygiene and diff
checks pass. Owned resources close and original project services/data remain
preserved. Logs remain in `services/api/target/maintenance-admission-focused.log`
and `maintenance-admission-verify.log`. Exact pushed-head and remote/backlog
evidence is recorded in the stacked draft PR and milestone updates on #15/#12.

No existing project credentials, database, volume, migration/schema, dependency,
business transition, settlement policy or upstream change. Normal startup keeps
write admission open and the prior dispatch/restore behavior. The flag alone does
not stop active work, drain existing requests, fence ingress/another process,
freeze deadlines or establish in-flight/external-effect safety. Hosted controls,
operator rehearsal and production acceptance remain open. No public/paid hosting,
live gateway, billing change or Flutter/native rerun. Preserve drafts #39–#55;
#7/#12/#15 stay open and the required CI/native gate remains deferred.


## 2026-10-20 - Flutter maintenance access and explicit retry (#15)

Branch `15-client-maintenance-recovery` builds on draft PR #56 at
`ca2bc527d328743d225dc97efdf661e055c42ed5` in the existing managed checkout.
Normal sign-in/restore keeps POST bootstrap first. Only HTTP 503 triggers the
current-token GET profile fallback, allowing an existing account to enter its
authorized read screens while writes are paused. A missing/unavailable profile
shows a fixed unavailability message plus Retry account access and Sign out;
retry does not start another interactive sign-in. Non-503 bootstrap refusals do
not trigger the read fallback, and 401 clears the local session/retry state.
The shared HTTP 503 message contains no server response body. Existing
idempotent submission code is unchanged; a new evidence widget test proves
original body/key retention across 503, no automatic retry while waiting, and
manual retry after reopening followed by pending-entry removal. Pending writes
remain in memory only and are cleared on sign-out/session clearing.
See [behavior and limits](../operations/CLIENT_MAINTENANCE.md).

Eleven new tests cover fallback/denials/retry, account retry at 320-pixel phone
width, sign-out, safe messages and evidence replay. Focused tests pass 21 cases.
After fixing three new-test brace lint findings, Flutter 3.47.5 analysis reports
no issues, formatting checks 35 Dart files without changes, and the full machine
run passes all 57 tests with zero failures/errors/skips. Release web compilation
passes (24.8 seconds), including the Wasm dry run; the existing optional Cupertino
font-family notice remains non-fatal. Java 21/Android SDK debug APK compilation
passes (assembleDebug 44.9 seconds). No dependency upgrades or lockfile changes.
Logs are retained under ignored `.work/client-maintenance-*` paths. Backend
source is unchanged: the parent maintenance branch's 95 passing scenarios remain
historical evidence and were not rerun. Docker's daemon is stopped; no Docker
repair/start, database, credential or volume change is required for this slice.

These are synthetic token/HTTP unit/widget checks and normal app compilation;
no real backend maintenance UI, Keycloak or native/iOS execution is claimed.
Ordinary authorization, server write admission, business transitions and settlement
policy remain unchanged. Hosted ingress/worker safety, production maintenance
acceptance and the required current-head CI/native gate remain open. Preserve
prior drafts, the original checkout and #7/#12/#15. GitHub's payment/spending-limit
issue stays at the user's deferred final gate. The stacked draft PR and milestone
updates on #15/#12 record the pushed head and freshly inspected remote results.


The first automatic checks at `677931b` execute normally rather than receiving
payment-limit rejections: both Flutter and Android CI jobs pass. Both iOS
browser/marketplace jobs start real PostgreSQL, Keycloak and the packaged API,
then fail the shared runner's first simulator inventory at its 30-second
deadline, before UI execution. The runner now allows a bounded 180 seconds for
inventory, retaining process-group cleanup and existing boot/test deadlines and
assertions. Python syntax compilation passes locally without writing bytecode;
macOS execution must be established by the new pushed-head runs. No app behavior,
backend source, ownership check or native assertion is relaxed. Exact final
remote results and any remaining failures are recorded in the PR and backlog.


At `e7df284`, both complete push/PR CI runs pass: each verifies 95 backend cases
(69 ordinary + 26 packaged, zero failures/errors/skips), 57 Flutter tests, clean
formatting/analysis, web build and Android app/native-driver compilation. The
separate iOS app/session workflow passes at the initial unchanged Flutter source
`677931b`. The browser run confirms simulator inventory completes in 51.63 seconds,
then compiles and runs XCTest. The first Keycloak page remains at 10% loading
when the old 30-second credential-form wait expires. The shared UI helper now
allows bounded 90-second first-form discovery, retaining the real form, realm,
profile, logout and account-switch requirements. This changes test timing only;
no silent SSO, credential injection or application retry is added. New pushed-head
native/CI results must verify it; no browser/marketplace acceptance is inferred.


At `f8079bf`, PR CI passes all 95 backend scenarios while the identical push run
passes 94/95: WorkflowBacklogMetricsIT observes zero pending counts with an old
age gauge (9.773826 seconds) at line 178. The existing metric contract explicitly
permits a scrape to span a sampler refresh. The fixture now awaits zero ages for
all four queues as well as zero pending counts before its unchanged zero-age,
queue, replay and restart assertions. This is a test synchronization correction;
backend runtime, SQL, metric sampling and settlement behavior remain unchanged.
After the initial client-only checks, this fixture required Docker. The guarded
startup helper preserves stale runtime directories and starts the engine without
resetting data or volumes. Java 21 focused `spotless:apply verify` with
`-Dtest=OperationsSmokeTest -Dit.test=WorkflowBacklogMetricsIT
-Dquarkus.http.test-port=0` passes six scenarios (five ordinary + one packaged),
zero failures/errors/skips, Spotless clean across 97 Java files, in 1:31. The
metrics drill passes in 54.72 seconds. Owned containers close; the unrelated
`t36_comp3078_w26-db-1` remains running. No project database migration or credential
change. Logs are retained in `services/api/target/client-maintenance-metrics-focused.log`.
Final pushed-head remote results remain in the PR/backlog, with all native and
required-check gates retained until verified.
