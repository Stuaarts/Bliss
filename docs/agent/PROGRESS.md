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
Do not claim booking/authentication/workflow persistence completion or advance past
the unvalidated local foundation gate. No destructive Docker reset was authorized.
