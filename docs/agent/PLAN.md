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
Current branch: `codex/7-native-marketplace`, based on main checkpoint `5227c12`.
The real Android request-to-review run passed on September 28 in 375.264 seconds,
including two-way chat, actual schedule enforcement, simulated capture, process
recovery, saved review reopening and background/resume. Authentication recheck
passed in 48.438 seconds after extracting shared driver controls. The runner
rejects duplicate run IDs and documents safe phase-based diagnosis. CI/merge for
this slice remain pending; iOS and secondary native paths remain next.
Docker's ordinary restart limitation (#17) is mitigated by the
guarded Windows startup helper; existing container data is preserved.
