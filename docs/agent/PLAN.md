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
