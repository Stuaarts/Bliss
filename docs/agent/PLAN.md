# Execution plan

Follow the master prompt in validated slices. GitHub Issues are the canonical
backlog; this file records sequencing and exit gates.

1. **Phase 0:** preserve inputs; inventory toolchain and upstream; initialize safe
   baseline, templates, labels, milestones and deduplicated issues.
2. **Phases 1–2:** Java 21 Quarkus monorepo; PostgreSQL/Flyway catalog; public paged
   read endpoints; OpenAPI, health, metrics; connected Flutter catalog; CI.
   Exit: real PostgreSQL tests, API startup and Flutter analyze/tests pass.
3. **Phase 3:** OIDC/Keycloak + PKCE; subject profile mapping; provider memberships;
   ownership and negative authorization tests. No homegrown credentials.
4. **Phase 4:** request/quote/accept transaction; concurrency and ownership tests.
5. **Phase 5:** released Quarkus Flow Java DSL booking lifecycle + JPA persistence;
   fake payment idempotency, event correlation, timeout, retry/compensation,
   restart recovery and audit tests. No CRUD disguised as a workflow.
6. **Phase 6:** mobile customer/provider lifecycle, login, timeline and actions.
7. **Phases 7–8:** persisted authorized conversations, eligible reviews, DisputeFlow.
8. **Phase 9:** full demo, evidence, CI and operational hardening.
9. **Phase 10:** product beta backlog; do not implement live payments or deployment.

Do not advance past a failing foundation gate into unrelated features. Record
external blockers precisely and continue independent, verifiable work.

## Current checkpoint

Phase 0 complete except archive unavailable (existing extraction preserved).
Phases 1–2 verified: CI and local PostgreSQL/API checks passed, Docker repaired,
Compose healthy, and Flutter category filtering/detail navigation verified against
the live API. Phase 3 identity (#2) now includes validated bearer authentication,
Flutter PKCE login, profile editing/logout, and provider ownership checks, verified
against live Keycloak and PostgreSQL. Android login is now verified; the complete
native marketplace flow and iOS remain under #7. Provider onboarding and offering management (#3) now includes atomic
OWNER membership creation, owned offering CRUD/archive, strict decimal/category
validation, version conflicts and Flutter management screens. API and client tests
pass; live browser creation/edit/archive and persistence checks passed. PR #20
merged with green CI. Requests/quotes (#4) now have owned APIs and atomic booking
creation with concurrent acceptance tests; PR #21 merged after final verify/CI passed.
Phase 5 (#5) now implements durable booking actions, fake settlement, timeouts,
retries, compensation and protected history/payment/execution reads. Packaged API
restart tests preserve the same instance through provider and customer waits.
Two release-specific Flow JPA defects have a tested Bliss-local compatibility
adapter; #22 tracks its removal after an upstream fix. PR #23 merged after all
API/mobile CI checks passed, closing #5. Direct offering booking (#6) is implemented
with scope/price snapshots, expected offering revisions, customer-scoped idempotency,
concurrency tests and the same lifecycle. V6 upgrades existing quote bookings and
enforces one source. PR #24 merged after all API/mobile CI checks passed, closing #6.
The Flutter lifecycle (#7) now has requests, quotes, direct booking, provider actions,
customer confirmation and a live timeline. The web flow passed against the real
local services; native-device authentication was unverified at that checkpoint. PR #25 merged as
`a671e58` after both API/mobile CI runs passed. Authorized persisted conversations
(#8) now include V7 tables, participant/current-owner access checks, stable message
cursors, idempotent sends and Flutter chat. Local verification passed 46 backend
scenarios and 28 Flutter tests; both participants and API restart persistence were
verified in the browser. PR #26 merged as `93b1a02` after all four API/mobile CI
jobs passed, closing #8. Reviews (#9) now include V8, completed-customer eligibility,
one review per booking, bounded public reads/averages and Flutter publish/read flows.
Local checks passed 51 backend scenarios and 33 Flutter tests. Real browser publish,
reopen and signed-out aggregate reads passed at phone width. PR #27 merged as
`9610918` after both API/mobile CI runs passed, closing #9.
DisputeFlow (#10) is merged in PR #28 as `2cab47e`: V9,
private evidence, independent admin decisions, durable waits and simulated
refund/void/capture. All 60 backend scenarios and 39 Flutter tests passed; browser
customer/provider/admin checks and phone screenshots passed. All four API/mobile
CI jobs passed at `17f0e90`. Database hardening (#11) merged in PR #29 as
`41e564f`: V10 constraints and populated V8/V9 upgrade, invalid-data rollback and
empty-schema type tests. All four API/mobile CI jobs passed at `c95ea84`, including
63 backend scenarios and 39 Flutter tests. The restart test now waits for a
nonempty, matching workflow identity before taking its pre-crash snapshot.
PR #30 merged as `42e98ff` after all four API/mobile CI jobs passed at `3d17c9b`.
The timed walkthrough, release checklist and architecture/action documentation
are synchronized on `main`. Android now builds, installs and launches on a dedicated
API 36 emulator, with the AppAuth callback handler registered. Full sign-off (#12)
remains open for secondary native paths and iOS checks (#7).
PR #31 merged as `ba1699e` after all four API/mobile CI jobs passed at `4a19f98`.
Four reproduced native session ordering defects are fixed;
seven regressions (46 ordinary Flutter tests total) and three Android fixture
process phases pass. Real Keycloak browser checks remain distinct from synthetic
storage/refresh verification. The separate
Android UiAutomator driver now passes real browser PKCE, customer/provider recovery,
identity-provider logout and account switching. Removing MainActivity's empty
task affinity fixes the reproduced callback failure. A guarded PowerShell runner
reproduces the local check; Android app/driver compilation is added to CI.
PR #32 merged as `a892dd9` after all six API/mobile/Android jobs passed at `aae6927`.
Main CI also passed (run 36347290131).
Current checkpoint: `main`, synchronized through PR #38 merge `9c81af7`.
The real Android request-to-review run passed on September 28 in 375.264 seconds,
including two-way chat, actual schedule enforcement, simulated capture, process
recovery, saved review reopening and background/resume. Authentication recheck
passed in 48.438 seconds after extracting shared driver controls. The runner
rejects duplicate run IDs and documents safe phase-based diagnosis. All six
API/mobile/Android jobs passed at `1f0f9ac` before merge. The following Android secondary slice
now passes direct cancellation, a manual admin dispute and catalog outage/retry.
Secondary iOS cancellation/dispute/connection-recovery remains the next platform gate; the normal
iOS app build and three-process native storage/refresh/logout fixture now pass
on macOS CI. PR #36 adds the disposable simulator gate and required Keychain
entitlements. Later Flutter CLI attempts stalled during debug-service discovery
before tests began; the final runner uses Flutter's native XCTest bridge for the
same fixture. All three phases passed at `4da6f18` (run 37888748649), with all
six API/mobile/Android checks green before merge. Real iOS Keycloak browser
authentication now also passes. The request-to-review marketplace lifecycle
also passes against isolated local services on macOS; secondary paths are next. #7/#12 stay open;
physical-device coverage and signed distribution remain separate.

PR #37 adds a separate XCUITest target for the normal app and a disposable macOS
PostgreSQL/Keycloak/API stack. Real browser credentials/callback, stopped-process
recovery for both accounts, customer background/resume, logout and account
switching passed in 92.921 seconds at `5685e9e` (run 37945058779). All eight
checks passed before merge, including a separate synthetic native dispatch on
that exact head. See [iOS browser authentication](../demo/IOS_AUTH.md).
PR #35 merged after all six checks passed
at `c3556e3`, including 63 backend scenarios and 46 Flutter tests. The user-requested
branch rename removed codex/ from 14 remote and 14 local branches,
preserving commits and updating tracking. Use issue-linked branch names without
that prefix for future Bliss work.
Docker's ordinary restart limitation (#17) is mitigated by the
guarded Windows startup helper; existing container data is preserved.

PR #38 verifies native iOS requests, quotes, booking/chat, scheduled work, simulated
capture, saved reviews and process/background recovery. See [iOS marketplace evidence](../demo/IOS_MARKETPLACE.md).

Post-merge main CI is currently blocked before job execution: GitHub reports an
account payment or spending-limit problem. All nine pre-merge checks passed;
main has identical app/API/test/runner/workflow source. The account owner must
review GitHub Billing & plans, then rerun the required checks at the final release gate.

## October 10 continuation — independent operations work

The user moved the account blocker to the final gate so independent local work
can continue. Preserve draft PR #39 (secondary iOS) and #40 (database recovery);
neither is merged or native-signed-off. Branch `15-operations-readiness` starts
from main `f1421a3` in an isolated managed checkout. This slice adds the
[operations plan and bounded smoke](../operations/READINESS.md), including the
validation-error correlation fix exposed by the smoke. Full local Maven verify
passes 68 scenarios (60 ordinary + 8 migration/packaged restart), including the
booking restart test's persistence-based snapshot wait. #15 remains open for
managed operations, abuse/moderation, privacy lifecycle and representative load
validation. Required CI and native iOS execution still precede green merges and
final #7/#12 sign-off. Branch names continue without codex/.

The next slice, `15-management-access`, is stacked on draft PR #41 at `90d6abb`.
It gates packaged-production management/documentation with an independent OIDC
operator role, authenticates packaged restart probes and checks that the local
demo keeps its tools against an isolated database. Its test identity fixture uses
normal JWKS validation after the first negative case exposed an offline-key
subject-check limitation in the pinned Quarkus runtime. See
[management access](../operations/MANAGEMENT_ACCESS.md). Private ingress, TLS,
operator credentials/rotation, outage drills and final CI/native gates remain open.
Full local Java 21 verification passes 73 scenarios (61 ordinary and 12
migration/packaged cases), with no failures/errors/skips and clean formatting.

The database outage slice, `15-database-outage`, builds on draft PR #42 at
`3cf3c33`. It uses an owned, labelled disposable PostgreSQL container to test
production readiness DOWN/UP, continued liveness, the same API process, retained
provider data and a successful write after recovery. See the
[database outage drill](../operations/DATABASE_OUTAGE.md). Existing data and all
earlier drafts are preserved. Hosted/operator rehearsal, active-workflow outage
reconciliation, identity outages and the final CI/native gate remain open.
Full local Java 21 verification passes 74 scenarios (61 ordinary + 13
migration/packaged), zero failures/errors/skips, with Spotless clean.

The next slice, `15-request-diagnostics`, builds on draft PR #43 at `29d6424`.
It adds bounded matched-REST response events, removes throwable content from
mapped API failure logs and explicitly disables Flow's payload trace logger.
HTTP log-capture/concurrency tests and packaged JSON checks validate the boundary;
see [request diagnostics](../operations/REQUEST_DIAGNOSTICS.md). Universal library
log review, async/restart tracing, external collection/alerts and the final
CI/native gate remain open. Earlier drafts and existing data are preserved.
Full local Java 21 verification passes 78 scenarios (64 ordinary + 14
migration/packaged), zero failures/errors/skips, with Spotless clean.

Branch `15-authenticated-workload` builds on draft PR #44 at `a302532`.
It adds an isolated production-profile, four-client direct-booking workload:
eight customers retry concurrent writes, preserve eight waiting workflow IDs
through an API crash, retain ownership boundaries, and cancel/replay with exactly
one simulated AUTHORIZE/VOID and three history entries per booking. See the
[workload and measurement limits](../operations/AUTHENTICATED_WORKLOAD.md).
Broader endpoint mixes, long-duration/resource/saturation acceptance, hosted
operations and the final CI/native gate remain open. Earlier drafts are preserved.
Full local Java 21 verification passes 79 scenarios (64 ordinary + 15
migration/packaged), zero failures/errors/skips, with Spotless clean.

Branch `15-workflow-backup-restore` builds on draft PR #45 at `c8e20ea`.
It backs up one actual WAIT_PROVIDER booking after stopping the sole worker,
restores all public tables to a fresh owned database, verifies row equivalence,
and resumes the same workflow through idempotent simulated cancellation. See
the [paused workflow recovery drill](../operations/WORKFLOW_BACKUP_RESTORE.md).
The stopped source must remain unchanged. Broader workflow/external-effect
restore, managed backups, operator rehearsal and the final CI/native gate remain
open. Earlier drafts and existing project data are preserved.
Full local Java 21 verification passes 80 scenarios (64 ordinary + 16
migration/packaged), zero failures/errors/skips, with Spotless clean.

Branch `15-workflow-backlog-metrics` builds on draft PR #46 at `17da5eb`.
It adds cached four-queue inbox counts/oldest ages and snapshot freshness/failure
metrics at the existing operator-gated Prometheus endpoint. Collection is periodic
and read-only; scrapes never query the database. See the
[metric contract and isolated outage/drain drill](../operations/WORKFLOW_BACKLOG_METRICS.md).
Engine failure/healthy-wait observability, collection/dashboards/alerts and broader
production acceptance remain open. Earlier drafts and final CI/native gates are
preserved.
Full local Java 21 verification passes 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips, with Spotless clean.

Branch `15-dispute-failure-metrics` builds on draft PR #47 at `2823027`.
It extends the cached snapshot with two unlabeled gauges for unresolved failed
disputes and oldest saved failure age. The existing marker survives failed engine
checkpoint cleanup. Actual DisputeFlow retry exhaustion/rollback now verifies
positive Prometheus samples; packaged access/outage tests include the empty
failure gauges. See the [durable failure contract](../operations/WORKFLOW_BACKLOG_METRICS.md#durable-dispute-failure-signal).
No schema, listener, settlement or automatic recovery change. Broader
booking/engine/human-wait observability, alerts and the final CI/native gate remain
open; earlier drafts and existing project data are preserved.
Full local Java 21 verification passes 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips, with Spotless clean.

Branch `15-booking-wait-metrics` builds on draft PR #48 at `2e38d3b`.
It extends the cached snapshot with counts, oldest transition ages and
missing-history counts for saved provider/customer decision waits. Ages persist
across process crashes; missing history produces NaN rather than a partial or
invented oldest age. See [booking decision waits](../operations/WORKFLOW_BACKLOG_METRICS.md#booking-decision-waits).
The packaged quote/rejection and direct/completion cases cover positive waits,
reversible history fault injection, restart, terminal exclusion and single
simulated settlement. No schema/policy/workflow transition change. Dispute waits,
broader faults, engine health and the final CI/native gate remain open; existing
project data and prior drafts are preserved.
Full local Java 21 verification passes 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips, with Spotless clean.

Branch `15-dispute-wait-metrics` builds on draft PR #49 at `db9ba62`.
It adds evidence/admin-review case counts, latest-transition ages and
missing-history counts to the cached snapshot. Both dispute restart/settlement
outcomes verify positive stages, age continuity and resolved exclusion; an owned
fixture verifies missing-history NaN and exact-row restoration. Booking/dispute
tests share a test-only helper. See the [dispute wait contract](../operations/WORKFLOW_BACKLOG_METRICS.md#dispute-evidence-and-admin-review-waits).
No schema/policy/workflow transition or settlement change. Broader faults,
engine-health classification, alerts and the final CI/native gate remain open;
prior drafts and project data are preserved.
Full local Java 21 verification passes 82 scenarios (65 ordinary + 17
migration/packaged), zero failures/errors/skips, with Spotless clean.

Branch `15-booking-failure-handling` builds on draft PR #50 at `3088da5`.
It records canonical active booking failures in nullable V11 timestamps after
Flow checkpoint cleanup. Dispatch/ticks skip marked bookings; owner execution
returns ERROR, new ordinary actions are rejected, and prior receipts still replay.
Cached failure count/age adds two unlabeled samples. Actual exhausted AUTHORIZE/
VOID tests verify rollback/no reclaim, and a separate synthetic marker survives
a packaged process restart. Populated V10 migration preserves existing values
without historical failure inference. See [booking failures](../operations/BOOKING_FAILURES.md)
for investigation and the restriction on rollback to workers that ignore markers.
No automatic recovery, business transition, settlement-policy, dependency or
upstream change. Marker-write failures, compatible repair, external reconciliation,
broader engine health and final CI/native gates remain open.
Focused Java 21 verification passes 13 scenarios (eight ordinary + five
migration/packaged), zero failures/errors/skips, Spotless clean, in 2:03.
Full Java 21 verification passes 85 scenarios (67 ordinary + 18 migration/
packaged), zero failures/errors/skips, Spotless clean, in 6:27. Final terminal
execution precedence and start-acknowledgement assertions pass; existing crash,
restore, outage, ownership and single-settlement evidence remains green.

Branch `15-dispute-backup-restore` builds on draft PR #51 at `9f30ca3`.
It restores actual evidence/admin-review waits into fresh owned PostgreSQL
databases, including a real HTTP-admitted unprocessed decision. All public table
counts/digests match before execution. Case/instance identity, wait age, evidence,
ownership and receipt replay survive; only the restored worker proceeds through
one simulated CAPTURE/REFUND and the frozen source remains unchanged. Shared
test-only fixture/backup helpers also serve existing restart/cancellation drills.
See [dispute backup/restore](../operations/DISPUTE_BACKUP_RESTORE.md).
No runtime/schema/policy/settlement change. Managed/online/PITR, in-flight or
simultaneous settlement, external reconciliation and RPO/RTO acceptance remain
open, alongside the deferred current-head CI/native gate. Focused Java 21
verification passes ten scenarios (five ordinary + five packaged), zero
failures/errors/skips, Spotless clean, in 5:06.
Full Java 21 verification passes 87 scenarios (67 ordinary + 20 migration/
packaged), zero failures/errors/skips, Spotless clean, in 10:34. Both dispute
restores, both existing dispute/booking restart outcomes and the booking restore
pass with final shared helpers. Temporary archives are removed; fixtures stay
out of the application jar and existing project data is preserved.

Branch `15-runtime-database-roles` builds on draft PR #52 at `4d2ad30`.
It adds a guarded post-migration grants policy for a dedicated database and
separates migration ownership from runtime DML. Invalid database/owner/elevated/
member-role inputs fail before permissions change. Runtime cannot migrate,
rewrite history, change schema/roles, truncate or create temporary tables. Actual
packaged booking/dispute work and checkpoint restart pass after disabling the
migration login. See [database roles](../operations/DATABASE_ROLES.md).
Runtime defaults and existing project credentials/data are unchanged; selecting
this setup requires explicit startup options and policy application after each
migration. Hosted role adoption, migration runner, secret/session operations and
the deferred CI/native gate remain open. Focused Java 21 verification passes
seven scenarios (five ordinary + two packaged), zero failures/errors/skips,
Spotless clean, in 1:28.
Full Java 21 verification passes 89 scenarios (67 ordinary + 22 migration/
packaged), zero failures/errors/skips, Spotless clean, in 11:43. Both role cases
and all existing restart/restore, ownership, workload/outage, metrics and migration
checks pass. Test/admin artifacts stay out of the application jar; existing
project roles/data and prior drafts are preserved.


Branch `15-runtime-credential-rotation` builds on draft PR #53 at `3a7fb1b`.
It rehearses SCRAM password rotation, NOLOGIN and explicit runtime-session
termination in an owned database with the sole worker fenced. Existing sessions
and pooled readiness remain usable until termination; the old API then reports
readiness DOWN/liveness UP. A controlled restart uses only the new runtime
password and restores the same dispute/evidence/age through one simulated
CAPTURE with processed decision replay. All public records remain unchanged while
fenced. The extracted test-only role fixture also serves the prior permission
cases. See [credential rotation](../operations/CREDENTIAL_ROTATION.md).
No application/default/schema/business/settlement change. Managed secret delivery,
hosted ingress fencing, active-workflow outage reconciliation, rotation/rollback
operator acceptance and the deferred CI/native gate remain open.
Focused Java 21 verification passes eight scenarios (five ordinary + three
packaged), zero failures/errors/skips, Spotless clean, in 2:27. The rotation case
passes in 58.80 seconds; both existing role cases pass in 47.67 seconds.
Full Java 21 verification passes 90 scenarios (67 ordinary + 23 migration/
packaged), zero failures/errors/skips, Spotless clean, in 12:27. Rotation passes
again with final shared helpers; all prior role, restart/restore, metrics, outage,
workload and migration checks remain green. Test fixtures stay out of the jar;
existing project credentials/data and prior drafts remain preserved.


Branch `15-migration-command` builds on draft PR #54 at `c742f4b`.
The packaged fast-jar exposes a standalone Java migration entry point and a
PowerShell wrapper. Dedicated migration environment variables and an explicit
database guard are required; only bundled versioned SQL, validate and migrate
are allowed. Restricted schema ownership and role attributes/membership are
checked before Flyway; unsafe actions, pending validation, checksum drift and
unknown newer history are refused. Fresh V11 creation excludes demo data and
workflow bootstrap; populated V10 upgrade preserves a synthetic profile.
The runtime API starts/writes with restricted access after the migrator login
is disabled. See [migration command](../operations/DATABASE_MIGRATION_COMMAND.md).
The full run exposed an existing metrics fixture race between saved cancellation
and engine cleanup. A bounded pre-stop FINISHED/no-checkpoint wait now establishes
the terminal fixture; the post-restart assertion remains unchanged.
Existing API defaults, project credentials/data, schema and settlement policy
are unchanged. Hosted migration orchestration, worker/ingress fencing, secrets,
TLS/network adoption, rollback and operator acceptance remain open.
Corrected focused Java 21 verification passes seven scenarios (five ordinary +
two packaged), zero failures/errors/skips, Spotless clean, in 1:00. Both command
cases pass in 25.90 seconds, including PowerShell validation on Windows and exact
fixed-output checks across all refusals/successes.

After the cancellation/cleanup synchronization fix, focused verification passes
eight scenarios (five ordinary + three packaged), zero failures/errors/skips,
Spotless clean, in 1:56. Full Java 21 verification with this final source passes 92 scenarios
(67 ordinary + 25 migration/packaged), zero failures/errors/skips, Spotless clean,
in 12:23. The corrected metrics drill passes alongside the shipped command and
all prior recovery, role/rotation, ownership and migration checks. Test-only and
administrator artifacts stay out of the jar; existing project data is preserved.


Branch `15-maintenance-admission` builds on draft PR #55 at `daba8f9`.
An opt-in startup flag blocks matched REST writes with a correlated no-store 503
and reports admission readiness DOWN; authorized reads and liveness remain.
The default is false. Dispatch and auto-restore remain independent, explicit
controls; the restricted-role maintenance drill stops the old worker and disables
both in its inspection process. All public records remain unchanged across reads,
six rejected writes and two dispatch intervals. A normal replacement retains the
same dispute/evidence/age and admits the rejected keys through one simulated
CAPTURE. See [maintenance admission](../operations/MAINTENANCE_ADMISSION.md).
No schema, dependency, settlement policy or upstream change. Hosted ingress,
draining, in-flight safety, distributed fencing and operator acceptance remain
open, with required current-head CI/native acceptance at the deferred final gate.
Focused Java 21 verification passes eight scenarios (seven ordinary + one
packaged), zero failures/errors/skips, Spotless clean, in 1:48; the maintenance
case passes in 61.64 seconds. Full Java 21 verification passes 95 scenarios (69 ordinary + 26 migration/
packaged), zero failures/errors/skips, Spotless clean, in 14:08. The new maintenance
case and both ordinary admission cases pass; all prior workflow, backup/restart,
role/rotation, migration, metrics and ownership checks remain green. Test/admin
artifacts stay out of the jar and existing project data is preserved.


Branch `15-client-maintenance-recovery` builds on draft PR #56 at `ca2bc52`.
The Flutter session attempts the normal bootstrap first, then reads the owned
profile only after HTTP 503. Existing accounts retain read access during the
optional write pause; unavailable/new accounts can retry bootstrap without a new
interactive sign-in or sign out. HTTP 401 clears retry/session state. A fixed
503 message never includes the server body. A widget scenario verifies paused
evidence keeps its original body/key until an explicit retry after reopening,
with no automatic POST retry. Pending entries remain in memory only.
See [client maintenance recovery](../operations/CLIENT_MAINTENANCE.md).
Flutter 3.47.5 analysis and formatting pass; all 57 unit/widget tests pass without
failures or skips, including 11 added cases. Release web build and debug Android
APK compilation pass. Backend runtime/dependencies are unchanged; the parent
95-scenario backend result is retained, not rerun. Docker is stopped and is not
needed for these client checks. Real backend/native maintenance acceptance,
required exact-head CI, remaining iOS scenarios and production adoption stay open.

A later automatic push run exposes an existing empty-backlog scrape race: counts
can update before ages within one scrape. The metrics fixture now waits for
both before its unchanged assertions. Six focused Java scenarios pass locally
(five ordinary + one packaged), zero failures/errors/skips and Spotless clean,
in 1:31 after guarded Docker startup. Owned test containers close and the
unrelated database remains running. Final current-head CI/native results remain
the exit evidence in the draft PR and issue updates.

Native runner reproducibility now uses a shared installed-runtime selector matched
to the selected SDK release, with explicit missing-runtime refusal and compatible
new iPhone creation. Nine Python contract/CLI tests pass locally; Bash syntax and
CLI/import checks pass. Mobile CI runs those tests and helper changes trigger all
native workflows. This configuration policy does not prove the cause of prior
startup/restore stalls or waive final native acceptance.


### Native transport and session timing follow-up (2026-11-03)

Continuing draft PR #57 on `15-client-maintenance-recovery` from `791b915`.
At that recorded head, both full CI runs pass API/mobile/Android (95 backend,
57 Flutter and nine Python cases per run) and real browser auth passes all eight
identity checkpoints/screenshots. Session and marketplace remain failed.
The signed-out Dart phase actually reports all tests passed just before its
combined build/test command expires; it lacks the required final XCTest result.
Session now separates each phase's bounded 600-second compilation from the
unchanged 300-second test execution/result deadline, preserving the three
processes, same simulator data/Keychain, exact test and zero-failure assertions.
The marketplace Safari error precedes credentials/business writes. A native
simulator-side ephemeral URLSession now checks canonical localhost discovery
and readiness, plus diagnostic IPv4/IPv6 probes with fixed ten-second resource
limits. It records only fixed URLs and HTTP/error codes, and requires the issuer
and readiness UP. No browser cookies or credentials are injected or cleared.
IPv6 failure alone is diagnostic; the loopback-family hypothesis is unproven.
Local Python selector/CLI tests pass all nine, runner help/import and Bash syntax
pass, and diff checks pass. Mac-only compilation/execution awaits current-head
Actions; no failed run is counted as acceptance. Job/step limits are unchanged.
Refs #7, #12 and #15. All draft-stack, native/maintenance and release gates remain.


At follow-up head `8edd30dd4fdcd82c3d66000df564b39625783e4b`, both full
CI runs (41677075280 PR, 41677065061 push) pass: independently parsed logs verify
95 backend (69+26), 57 Flutter and nine Python tests per run, with clean analysis,
web/Android app and native-driver builds. Session run 41677065304 passes all three
separate Seed/Restore/SignedOut XTests, build and final markers on SDK/runtime
18.5; downloaded evidence confirms the exact commit. Splitting compilation from
execution resolves the previously observed combined-deadline failure.
Browser runs 41677065259 and 41677065018 both pass native transport preflight:
localhost and explicit IPv4 return 200 for discovery/readiness; IPv6 is refused
(-1004). Auth then shows Safari's server-response error before a credential form.
Marketplace actually signs in and reaches request creation; its immediate title
assertion reads a prefix, while the later UI counter reports the expected full
length (137/160 remaining). These two runs fail and are not acceptance.
The fixture now pins issuer/hostname, API OIDC validation, Flutter build defines,
JDBC and probes to the existing IPv4 listeners, removing hostname ambiguity.
Disposable JVM services now start after cold Xcode compilation and establish
fresh readiness before UI execution, avoiding competition during compilation.
This does not establish IPv6 as the sole Safari failure cause. Marketplace field
input waits up to ten seconds for the original exact full value, retains equality,
and never retypes or shortens the input. Application defaults, credentials,
consent, browser/business assertions and overall job deadlines are unchanged.
Nine Python tests, import/help, Bash syntax and documentation hygiene pass again
(285 local links/336 tracked files). These Mac-only adjustments require new-head
execution; first follow-up 7/9 does not imply all native gates are green.


At `7667a62f5b812e73f2d1c5bfb6432eb89515d261`, both full CI runs pass again
(41680757713 PR / 41680745120 push): 95 backend, 57 Flutter, nine Python tests
per run, clean analysis, web and Android app/native-driver builds. Session run
41680745156 again passes all three exact phases on SDK/runtime 18.5, independently
verified from downloaded artifacts. Seven of nine jobs pass; both browser jobs
fail despite successful transport probes and actual authenticated progress.
Auth run 41680745310 verifies login, refresh/process recovery and background/resume
(3/8 checkpoints), then fails its 30-second logout wait while Safari is loading.
Keycloak cookie handling occurs about 53 seconds after the logout tap; a delayed
OIDC response is an inference, not proof of callback completion. The bounded
logout wait now allows 90 seconds while still requiring enabled sign-in, real
consent/callback and subsequent fresh-account login. Overall deadlines remain.
Marketplace run 41680745305 verifies request, quote, acceptance/customer chat,
two-way chat and provider completion (5/9 checkpoints). The field synchronization
gets past the previous title failure. A later customer login hits the existing
optional-prompt race: XCTest re-resolves Not Now during its dismissal animation,
tries to scroll an AX frame at y=1371.6, then fails because it disappears.
The helper now uses main-window viewport bounds instead of the application's
transitioning union frame, rechecks the expected profile, and taps the observed
visible prompt coordinate without re-resolving/scrolling the alert element.
All profile, identity, role and business assertions remain. Disposable Keycloak
logs method/path/status only to identify completed responses without protocol
query strings, headers/cookies or bodies. Release checklist now records that
Actions execute again without asserting an owner billing change or main pass.
Nine Python tests, runner help/import, diff and documentation hygiene pass
(286 local links/336 tracked files). New-head Mac execution remains required;
no partial browser scenario is counted as acceptance. Refs #7/#12/#15, draft #57.


At `feaba9179211bb01407e53b7cb31c77a2ea97a64`, both full CI runs pass again
(41684290466 PR / 41684280120 push), with 95 backend (69+26), 57 Flutter and nine
Python cases per run, clean analysis/web/Android builds. Session run 41684280144
passes again. Both browser jobs fail: auth 41684280096 checks Sign In once after
password entry and misses it, while the later UI tree shows the real named form
button and correctly entered customer username. It now requires the complete
realm form up front and resolves the named submit control again across live
browser surfaces for up to 30 seconds after typing.
Marketplace 41684280027 successfully declines the optional password prompt by
window coordinate, without the previous stale-element failure. Its profile wait
expires at 30 seconds; access logs then show token 200 and profile bootstrap 200
after that window. Profile/capability recovery now allows a bounded 90 seconds,
retaining the same expected identity, roles and UI outcome. No new native pass is
claimed and all overall deadlines remain unchanged.
Observed access-log `%U` output includes protocol queries despite its documented
path description. The formatter now uses relative path `%R`, and independent
scrubbing removes access-log queries before artifacts are retained. Scrubbing
also removes XCTest secure-field keystroke prefixes, which full-password matching
alone cannot catch. Three regression tests verify full-password/JWT masking,
ASCII/Unicode truncated secure entries, protocol-query removal and retained
nonsecret username/path/status/checkpoint diagnostics. Normal mobile CI now runs
all twelve Python tests. All twelve pass locally, as do CLI/import, Bash syntax,
diff and documentation hygiene checks. New-head native execution remains required;
all partial/failed attempts and #7/#12/#15 release gates remain open.
