# Reconnaissance — 2026-09-19

## Workspace and preservation

Working directory: `C:\Users\Lucas\Bliss`. Initial contents: `.git`,
`Bliss-Backend`, `Bliss-Mobile`, `Bliss-Frontend`, `quarkus-flow`,
`Bliss_ChatGPT_Agent_Master_Prompt.md`, `Bliss_V2_Agent_Engineering_Blueprint.docx`,
and `trampo.drawio`. No applicable AGENTS.md found in the workspace or ancestors.

No Bliss ZIP found in the workspace, its parent, or Downloads. Consequently no ZIP
could be inventoried/extracted. The three existing extracted legacy folders and
diagram are preserved in place as the safe reference area. Their nested `.git`
directories are untouched and excluded from the new root repository. Original
inputs will not be overwritten. This is an explicit adaptation of the ZIP step.

The root repository already has unborn `main` and origin
`https://github.com/Stuaarts/Bliss.git`. GitHub reports this repository private and
empty. Authenticated account: Stuaarts; gh authentication and repo/workflow access
are available. There are no existing issues or milestones. No remote repointing
or repository creation is needed.

## Quarkus Flow source of truth

- Local reference: `quarkus-flow/`; branch `main`.
- HEAD: `fcdd9cd0d975abdfd68d6307fbd9113a1382fc01`.
- `git describe --tags --always`: `1.2.0.CR1-3-gfcdd9cd0`.
- POM: `2.0.0-SNAPSHOT`, Java release 17, Quarkus 3.39.0, workflow SDK 7.32.1.Final.
- README supports JDK 17/21/25. Bliss targets Java 21.
- Working tree is **not clean**. With line endings normalized for inspection,
  six files differ (4,069 insertions, 509 deletions): HttpClientConfig.java and
  five generated configuration documentation files. Default Git status also
  reports widespread line-ending changes. These changes predate this work.
- Upstream remains read-only. Never run format/install tasks there that mutate it.
- Maven Central metadata and the published 1.2.0.CR1 parent POM were inspected.
  This released candidate matches Quarkus 3.39.0 / SDK 7.32.1.Final. Use that
  version, not the unpublished local 2.0.0-SNAPSHOT, and explicitly document RC risk.
- Import `io.quarkiverse.flow:quarkus-flow-bom`; core `quarkus-flow`; actual JPA
  artifact is **`quarkus-flow-jpa`**, confirmed in persistence/jpa/runtime/pom.xml.
  The README's `quarkus-flow-persistence-jpa` spelling is not the artifact to use.

Reference map (all relative to the local clone):

| Concern | Source inspected / selected for implementation |
|---|---|
| Java DSL | docs/modules/ROOT/pages/dsl-cheatsheet.adoc; core/runtime/.../dsl/FlowDSL.java and FlowWorkflowBuilder.java |
| Persistence / restore | docs/modules/ROOT/pages/persistence.adoc; persistence/common/runtime/.../FlowPersistenceRestore.java |
| PostgreSQL schema | docs/modules/ROOT/examples/persistence/V1__create_tables_pg.sql; verify against released entities before adopting |
| Events | docs/modules/ROOT/pages/messaging.adoc; examples/suspend-resume-abort |
| Retry / compensation | examples/order-fulfillment-compensation; core/deployment/.../faulttolerance/FaultToleranceRetryTest.java |
| Correlation / idempotency | docs/modules/ROOT/pages/idempotency-correlation.adoc; persistence/jpa/integration-tests/.../QuarkusFlowAllCorrelationJpaIT.java |
| Dev UI | core/deployment/.../test/devui; README quick start |
| Tests | docs/modules/ROOT/pages/test-debug.adoc; docs/modules/ROOT/examples/test/ListenWorkflowTest.java |

## Legacy findings

Backend: Python FastAPI, SQLAlchemy, Pydantic 1.x. Confirmed source defects:

- database.py embeds a credential-bearing database URL (value deliberately omitted).
- encryption/auth.py has an empty JWT key.
- routers/login.py compares against Accounts.password, not the fetched user's hash.
- routers/accounts.py constructs the account directly from raw input, including password.
- models.py overwrites Services.id_company column with a relationship.
- Schedules uses Column(float), and has no durable marketplace lifecycle.
- schema address/service names disagree with entity names.
- customer/company registration performs several commits instead of one transaction.
- ordinary CRUD lacks ownership enforcement.
- request, quote, payment, chat, dispute and workflow capabilities are missing.

Mobile: README records Flutter 3.27.3 / Dart 3.6.1. Prototype has login, role
choice, registration and address screens, but no HTTP integration. Confirmed:
controllers allocated in build, nested MaterialApp, reversed provider password
checks, and a counter widget test unrelated to the actual app. Keep product
branding/intent, rebuild feature structure. Web contains only a placeholder README.

Draw.io is uncompressed XML and was read. It captures customer need -> discovery
-> provider price/availability -> refundable payment -> work -> customer confirmation
or audit -> payout/refund. It includes automatic confirmation after a waiting
period and provider penalties; these are **unapproved product policies**, not
defaults to silently implement. Use manual confirmation and fake payments initially.

## Tooling at discovery

| Tool | Result |
|---|---|
| Java on PATH | Temurin 17.0.16 |
| Java 21 located | C:\Program Files\Java\jdk-21, 21.0.8; use per-process JAVA_HOME |
| Maven | Not on PATH; add project Maven wrapper / ignored local bootstrap |
| Flutter / Dart | Not on PATH; SDK still to provision or locate |
| Docker | CLI 28.3.3; Linux engine pipe absent; desktop start requested, not yet usable |
| Podman | Not installed |
| Git | 2.51.0.windows.1 |
| gh | 2.98.0, authenticated |

No legacy app was started with its embedded credentials. Backend and mobile
validation results are recorded as execution proceeds in PROGRESS.md.

Follow-up: provisioned ignored local Maven bootstrap (SHA-512 verified), generated
Maven wrapper 3.9.11, and Flutter stable 3.47.5 / Dart 3.13.4 (SHA-256 verified).
Docker Desktop's host log identifies a dockerInference socket-access failure;
it exits before the Linux engine is usable. GitHub CI successfully runs PostgreSQL
and the foundation tests independently of this host. See issue #17.
