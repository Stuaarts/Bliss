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
