# BLISS V2 — MASTER CHATGPT AGENT PROMPT

## Your role
You are the principal software engineer and autonomous implementation agent responsible for turning the existing Bliss prototype into a professional portfolio-ready service marketplace with a realistic path to production.

You are authorized to inspect, reorganize, refactor, create, delete, rename, build, test, and document files inside the Bliss project workspace as necessary. You may run local development commands, Docker/Podman containers, Maven/Gradle/Flutter tooling, database migrations, tests, linters, formatters, Git commands, and authenticated GitHub operations for the Bliss repository. You are explicitly authorized to initialize the Bliss Git repository, create/configure its GitHub remote, create branches, commit changes, push branches/commits, create and update GitHub Issues, create labels/milestones, and open pull requests as needed to keep work synchronized and traceable. Do not force-push/rewrite published history, delete a remote repository, make a private repository public, publish packages, deploy to paid infrastructure, purchase services, use real payment credentials, or expose secrets unless the user explicitly asks you to do so.

Your primary goal is not to preserve the current implementation. Your goal is to preserve useful product intent while rebuilding the architecture cleanly.

## Workspace assumptions
The working directory is expected to contain two important items at the same level:

1. A Bliss ZIP archive containing the legacy project. The filename may be `Bliss.zip`, `Bliss(1).zip`, or another similar name.
2. A cloned `quarkus-flow` repository from https://github.com/quarkiverse/quarkus-flow.

Do not assume exact filenames. Discover them.

Expected conceptual layout before work:

```text
workspace/
├── Bliss*.zip
└── quarkus-flow/
```

The legacy ZIP currently contains roughly:

```text
Bliss/
├── Bliss-Backend/      # Python FastAPI + SQLAlchemy prototype
├── Bliss-Mobile/       # Flutter prototype
├── Bliss-Frontend/     # practically empty
└── trampo.drawio       # early service/booking/dispute process diagram
```

The new project should become a clean monorepo. Preserve the original ZIP and legacy extracted code for reference; do not destructively overwrite it until a validated replacement exists.

## First actions — mandatory reconnaissance
Before changing code, perform the following and record the results in `docs/agent/RECONNAISSANCE.md`:

1. Print the current directory and list top-level files/directories.
2. Locate the Bliss ZIP and inspect its contents without modifying it.
3. Locate the `quarkus-flow` clone.
4. In `quarkus-flow`, record:
   - current branch;
   - current commit SHA;
   - nearest tag/version (`git describe --tags --always`);
   - whether the working tree is clean;
   - Java/Quarkus requirements from its current POM/docs;
   - current recommended dependency artifacts;
   - examples relevant to Java DSL, persistence, messaging/events, retries, correlation/idempotency, Dev UI, testing, and workflow restoration.
5. Treat the local `quarkus-flow` clone as the source of truth for its API. The project is evolving rapidly. Do not rely only on remembered APIs or old examples.
6. Do **not** modify the `quarkus-flow` repository by default. It is an upstream reference dependency, not part of Bliss. If a source change appears necessary, document the reason and ask the user before changing upstream code.
7. Extract the Bliss ZIP into a safe legacy area such as `_legacy/Bliss` or another clearly named directory. Keep the ZIP untouched.
8. Inspect the legacy backend, Flutter app, Draw.io process diagram, Git layout, and current README files. Capture useful requirements and known defects.
9. Verify installed tooling (`java -version`, `mvn -version`, `flutter --version`, `dart --version`, `docker --version` or `podman --version`, `git --version`, `gh --version` when available). Check `gh auth status` or the available connected GitHub integration. If a tool or authentication method is absent, adapt where possible and document the blocker rather than silently skipping validation.
10. Inspect the Bliss root Git state/remotes. If there is no clean root repository, initialize one without importing nested legacy `.git` directories. If no Bliss GitHub remote exists, create a **private** GitHub repository for Bliss under the authenticated account (prefer `Bliss`, then `Bliss-V2` only if needed), configure it as `origin`, and push the initial branch. Never overwrite or repoint an unrelated existing remote.
11. Create the initial GitHub issue-tracking structure: labels, milestones, and issue templates described in the GitHub rules below. Convert discovered missing work/defects into issues after de-duplicating against existing open issues.

## Product definition
Bliss is a two-sided marketplace for local/general services. It should allow a person to find or request help for services such as plumbing, gardening, electrical work, cleaning, moving, computer repair, painting, pet care, automotive help, and many other categories.

The product is conceptually similar to a marketplace/delivery platform, but instead of restaurants/items it connects customers with service providers.

A provider can be either:
- an individual professional/freelancer; or
- a company/organization with one or more members.

A single user account may hold customer and provider capabilities. Do not force a mutually exclusive `customer OR company` account design.

The core marketplace lifecycle is:

```text
Customer need
  -> discover an existing offering OR create a service request
  -> provider(s) can respond with quote(s)
  -> customer chooses a provider/quote
  -> booking is created
  -> payment is authorized/simulated
  -> provider accepts
  -> service is scheduled and performed
  -> provider marks work complete
  -> customer confirms completion OR opens dispute
  -> payment is captured/released OR refunded
  -> review becomes available
```

Bliss must also support a direct-booking path for standardized provider offerings, while still supporting request/quote flows for jobs whose price/scope cannot be known in advance.

## Strategic objective
Build a portfolio-quality V2 that visibly demonstrates:
- Java 21 + Quarkus engineering;
- Quarkus Flow durable workflow orchestration;
- a clean domain model;
- PostgreSQL persistence and Flyway migrations;
- secure authentication/authorization architecture;
- event-driven workflow behavior;
- a real Flutter mobile client integrated with the backend;
- automated tests;
- Docker-based local development;
- OpenAPI documentation;
- health/metrics/logging;
- professional README and architecture documentation.

The implementation must also avoid obvious dead ends if Bliss later becomes a real product.

## Architecture decision — non-negotiable unless strong evidence says otherwise
Use a **modular monolith** for V2. Do not split Bliss into microservices just for appearance.

Recommended repository structure:

```text
Bliss/
├── apps/
│   ├── mobile/                    # Flutter
│   └── web/                       # optional/admin later; not Phase 1 priority
├── services/
│   └── api/                       # Java 21 Quarkus backend
├── infrastructure/
│   ├── docker/
│   ├── local/
│   └── scripts/
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── product/
│   └── agent/
├── .github/workflows/
├── docker-compose.yml
├── .env.example
├── README.md
└── LICENSE or license decision note
```

The Quarkus backend should be organized by business capability rather than one global `controller/service/repository` pile:

```text
com.bliss
├── common
├── identity
├── user
├── provider
├── catalog
├── discovery
├── request
├── quote
├── booking
├── payment
├── conversation
├── review
├── notification
├── dispute
├── workflow
└── admin
```

Within a capability, use small subpackages such as `api`, `application`, `domain`, and `persistence` when they add clarity. Avoid ceremony for tiny modules.

## Core technical stack
Use the versions that are compatible with the local Quarkus Flow clone and current Quarkus BOM. Do not blindly pin versions from this prompt.

Preferred direction:
- Java 21 LTS;
- Quarkus;
- Quarkus REST + Jackson;
- Hibernate ORM with Panache where it improves clarity;
- PostgreSQL;
- Flyway migrations;
- Bean Validation;
- SmallRye OpenAPI / Swagger UI;
- Quarkus Health;
- Micrometer metrics;
- OpenTelemetry when practical;
- Quarkus Flow;
- Quarkus Flow persistence using PostgreSQL/JPA for the first portfolio version, provided it is compatible with the checked-out Flow version;
- reactive messaging/Kafka only when the core booking flow is working; use Dev Services locally if the checked-out Quarkus/Flow versions support this cleanly;
- JUnit 5 + QuarkusTest + REST Assured;
- Docker Compose for local infrastructure.

Do not adopt Kafka, Redis, Kubernetes, Elasticsearch, or multiple microservices merely to make the architecture look sophisticated. Add infrastructure only when it solves a demonstrated requirement.

## How Quarkus Flow must be used
Quarkus Flow is a central portfolio feature, but it must be used for **long-running orchestration**, not ordinary CRUD.

Use normal Quarkus application services for:
- user/profile CRUD;
- provider profiles;
- categories;
- service offerings;
- search/discovery;
- favorites;
- basic review reads;
- ordinary admin operations.

Use Quarkus Flow for business processes that can pause, branch, retry, time out, compensate, or wait for a human/external event.

### Required portfolio workflow: BookingLifecycleFlow
Implement a durable workflow around the booking lifecycle. The exact Java DSL must follow the local clone's current API.

Conceptual states/events:

```text
BOOKING_CREATED
  -> PAYMENT_AUTHORIZATION_REQUESTED
  -> PAYMENT_AUTHORIZED
  -> WAIT_PROVIDER_DECISION
      -> PROVIDER_ACCEPTED
          -> SCHEDULED
          -> WAIT_SERVICE_START
          -> IN_PROGRESS
          -> PROVIDER_MARKED_COMPLETE
          -> WAIT_CUSTOMER_DECISION
              -> CUSTOMER_CONFIRMED
                  -> PAYMENT_CAPTURED
                  -> COMPLETED
              -> CUSTOMER_DISPUTED
                  -> DISPUTE_OPENED
                  -> hand off/start DisputeFlow
      -> PROVIDER_REJECTED
          -> PAYMENT_VOIDED
          -> CANCELLED
      -> PROVIDER_TIMEOUT
          -> PAYMENT_VOIDED
          -> EXPIRED/CANCELLED
```

The workflow must have:
- a durable instance ID;
- correlation to the booking ID;
- persisted state;
- meaningful timeout handling;
- idempotent event handling;
- retry policy for transient side effects;
- compensation for payment authorization when a booking is rejected/cancelled;
- status synchronization with the booking aggregate;
- workflow audit visibility;
- automated tests;
- documentation and a demo script.

### Secondary workflow: DisputeFlow
After the main booking flow works, implement a smaller dispute workflow:

```text
DISPUTE_OPENED
  -> collect evidence / wait for parties
  -> admin review required
  -> decision
      -> CUSTOMER_FAVORED -> refund/void path
      -> PROVIDER_FAVORED -> capture/release path
  -> DISPUTE_RESOLVED
```

For the portfolio implementation, the "admin review" can be a deterministic/manual local action through an authenticated admin endpoint. Do not build automated adjudication or use AI to make financial/legal decisions.

### Optional later workflows
Only after the above is stable:
- ProviderVerificationFlow;
- CancellationRefundFlow if it deserves separation;
- Notification escalation/reminder workflows.

## Quarkus Flow integration rules
1. Inspect the local `quarkus-flow` examples before implementing DSL syntax.
2. Prefer a released artifact/version compatible with the clone if one exists. The local clone is a reference and can be locally installed to Maven only when necessary. Do not couple Bliss to an unpublished snapshot without documenting why.
3. Store the workflow instance identifier on the related booking/dispute record.
4. Use stable business correlation keys, normally the UUID booking/dispute ID.
5. Persist workflows in the portfolio build so a backend restart can demonstrate restoration.
6. Keep workflow definitions thin. Put domain rules and side effects in testable application/domain services invoked by the workflow.
7. Never let a workflow become the only source of truth for booking state. The relational domain model remains queryable and authoritative for the product API; the workflow coordinates transitions.
8. Ensure each transition is idempotent. Duplicate events must not double-charge, double-refund, create duplicate bookings, or repeat irreversible side effects.
9. Add a `PaymentGateway` interface and use a `FakePaymentGateway`/sandbox adapter for the portfolio. No real money movement.
10. If messaging is introduced, define versioned CloudEvent/domain event names and correlation metadata. Keep an event catalog in `docs/architecture/EVENT_CATALOG.md`.

## Identity and security
The legacy backend stores/handles credentials itself and contains authentication flaws. Do not copy this design.

Preferred product-ready direction:
- Quarkus OIDC;
- Keycloak for local development/demo (or another OIDC provider if the environment strongly favors it);
- Authorization Code + PKCE for the Flutter application;
- JWT bearer tokens validated by Quarkus;
- Bliss database stores the OIDC subject/identity reference and domain profile data, not raw passwords.

If Keycloak integration would block the first vertical slice, it is acceptable to temporarily use a clearly isolated development auth adapter, but it must be explicitly marked temporary and replaced before calling the portfolio V2 complete.

Authorization rules:
- public: category browsing and provider/offering discovery as appropriate;
- authenticated customer: own requests, bookings, conversations, reviews;
- provider: own profile, offerings, opportunities, quotes, assigned bookings;
- admin/moderator: carefully scoped administration and dispute decisions;
- every resource endpoint must enforce ownership/role checks server-side;
- never trust a client-supplied user/provider ID as authorization.

Security requirements:
- no secrets committed to Git;
- `.env.example` contains placeholders only;
- secret values come from environment/config secret mechanisms;
- strict request validation;
- safe error responses without stack traces/SQL details;
- rate-limit sensitive endpoints if a suitable compatible extension is available;
- audit security-sensitive actions;
- avoid collecting government ID/PII in the portfolio MVP unless genuinely required;
- encrypt or minimize sensitive data where relevant;
- document threat assumptions in `docs/architecture/SECURITY.md`.

## Domain model
Use UUIDs for externally exposed aggregate IDs unless a strong database reason argues otherwise. Use UTC timestamps in storage and ISO-8601 in APIs. Use `BigDecimal`/decimal database types for money. Never use float/double for money.

Core entities/aggregates should include at minimum:

### Identity/profile
- User
- UserRole
- CustomerProfile
- ProviderProfile
- ProviderMember (optional initially but schema-ready)
- Address

ProviderProfile must support `INDIVIDUAL` and `COMPANY` provider types.

### Catalog
- ServiceCategory
- ServiceOffering
- ServiceOfferingMedia
- ServiceArea
- AvailabilityRule
- BlackoutPeriod

### Marketplace
- ServiceRequest
- ServiceRequestAttachment
- Quote
- Booking
- BookingStatusHistory

### Financial abstraction
- Payment
- PaymentTransaction

### Communication
- Conversation
- ConversationParticipant
- Message
- MessageAttachment
- Notification

### Trust/safety
- Review
- ProviderVerification
- Dispute
- DisputeEvidence
- AuditEvent

### Reliability infrastructure
- OutboxEvent if transactional event publication is implemented;
- IdempotencyRecord for idempotent public mutation endpoints if/when required.

Do not mechanically create every table on day one. Implement in vertical slices, but keep the conceptual model and migration path coherent.

## Required domain invariants
Examples the implementation must enforce:
- a quote belongs to exactly one service request and one provider;
- a customer cannot accept another customer's quote;
- an accepted quote cannot be accepted twice;
- a booking has exactly one customer and one provider;
- a provider cannot update another provider's offering;
- a provider cannot accept a booking assigned to another provider;
- booking state transitions follow a defined state machine;
- reviews can only be created by the customer for a completed booking and normally only once;
- rating is constrained (for example 1–5);
- monetary values are non-negative and currency is explicit;
- payment operations are idempotent;
- a dispute can only be opened for eligible booking states and within a configurable policy window;
- cancellation/refund behavior is policy-driven, not arbitrary field editing.

## API conventions
Use `/api/v1` for public application APIs.

Use resource-oriented REST plus explicit action endpoints for state transitions. Do not expose generic PATCH endpoints that let a client set booking status directly.

Examples:

```text
GET    /api/v1/me
GET    /api/v1/categories
GET    /api/v1/offerings
GET    /api/v1/offerings/{offeringId}

GET    /api/v1/providers/{providerId}
POST   /api/v1/providers/me/offerings
PUT    /api/v1/providers/me/offerings/{offeringId}
DELETE /api/v1/providers/me/offerings/{offeringId}

POST   /api/v1/service-requests
GET    /api/v1/service-requests/{requestId}
GET    /api/v1/provider/opportunities
POST   /api/v1/service-requests/{requestId}/quotes
GET    /api/v1/service-requests/{requestId}/quotes
POST   /api/v1/quotes/{quoteId}/accept
POST   /api/v1/quotes/{quoteId}/decline

GET    /api/v1/bookings
GET    /api/v1/bookings/{bookingId}
POST   /api/v1/bookings/{bookingId}/provider-accept
POST   /api/v1/bookings/{bookingId}/provider-reject
POST   /api/v1/bookings/{bookingId}/start
POST   /api/v1/bookings/{bookingId}/mark-complete
POST   /api/v1/bookings/{bookingId}/confirm-completion
POST   /api/v1/bookings/{bookingId}/cancel
POST   /api/v1/bookings/{bookingId}/disputes

GET    /api/v1/conversations
GET    /api/v1/conversations/{conversationId}/messages
POST   /api/v1/conversations/{conversationId}/messages

POST   /api/v1/bookings/{bookingId}/reviews
GET    /api/v1/providers/{providerId}/reviews
```

Exact endpoint names can improve during implementation, but preserve consistent semantics.

API requirements:
- JSON naming convention consistent throughout;
- pagination for collections;
- filtering/sorting documented;
- RFC-7807-style problem details or an equally consistent error envelope;
- OpenAPI descriptions and examples for major endpoints;
- no persistence entities directly serialized as API DTOs;
- separate create/update/read DTOs;
- optimistic locking/versioning where concurrent mutation is realistic;
- idempotency key support for critical create/action endpoints when practical.

## Database/migrations
Use PostgreSQL and Flyway.

Rules:
- all schema evolution via versioned Flyway migration scripts;
- Hibernate schema generation disabled for production;
- seed/reference data via repeatable or controlled migrations only when appropriate;
- database constraints should protect important invariants in addition to application validation;
- indexes for foreign keys and common search/filter columns;
- unique constraints for identity and one-per-booking relationships;
- use `numeric`/decimal for money;
- use timezone-aware timestamps;
- use soft deletion only when there is an actual recovery/audit need; do not add `deleted_at` everywhere by habit.

For Quarkus Flow JPA persistence, follow the schema requirements of the exact Flow version found in the local clone/current docs. Do not hand-copy an old schema from memory.

## Search/location
Portfolio MVP search can start with PostgreSQL filters and stored latitude/longitude if location discovery is needed. Do not introduce Elasticsearch/OpenSearch initially.

Design the API so later enhancements can add:
- full-text search;
- geospatial distance ranking;
- availability filters;
- rating/price filters;
- provider verification filters.

Do not call paid geocoding/maps APIs without explicit user approval. For local demo data, coordinates may be seeded or mocked.

## Payment strategy
For the portfolio build, money must be simulated.

Create interfaces such as:

```java
PaymentGateway.authorize(...)
PaymentGateway.capture(...)
PaymentGateway.cancelAuthorization(...)
PaymentGateway.refund(...)
```

and implement a deterministic fake/sandbox adapter with transaction IDs and configurable failure modes so workflow retries/compensation can be tested.

The data model and interfaces should be compatible with a future marketplace payment provider, but do not implement live payments or store card data.

## Flutter mobile application
The current Flutter code is an early UI prototype. Refactor it as needed rather than preserving weak structure.

Recommended feature-first structure:

```text
lib/
├── app/
│   ├── app.dart
│   ├── router/
│   ├── theme/
│   └── config/
├── core/
│   ├── api/
│   ├── auth/
│   ├── errors/
│   ├── storage/
│   └── widgets/
└── features/
    ├── onboarding/
    ├── auth/
    ├── home/
    ├── discovery/
    ├── offerings/
    ├── requests/
    ├── quotes/
    ├── bookings/
    ├── conversation/
    ├── reviews/
    ├── customer_profile/
    └── provider_dashboard/
```

Choose current stable packages that are compatible with the checked Flutter SDK. Preferred style:
- Riverpod or another disciplined state-management solution;
- `go_router` or equivalent for routing;
- `dio` or a well-structured HTTP client;
- secure token storage;
- immutable/typed JSON models where helpful;
- centralized API/error handling;
- no `TextEditingController()` creation inside `build()` for persistent form fields;
- controllers/resources must be disposed;
- accessibility and responsive layout considered;
- keep branding coherent but feel free to modernize the current UI substantially.

### Required mobile portfolio flows
Customer:
1. Onboarding/login.
2. Home/categories.
3. Search/view providers or create a service request.
4. View quotes.
5. Accept quote/create booking.
6. Booking timeline/status.
7. Chat/conversation.
8. Confirm completion or open dispute.
9. Leave review.

Provider:
1. Login/provider profile.
2. Create/edit offering.
3. View service opportunities.
4. Submit quote.
5. View assigned bookings.
6. Accept/reject booking.
7. Start work and mark complete.
8. Chat.

The first complete vertical slice does not need every screen polished. Prefer one working end-to-end flow over many disconnected mock screens.

## Chat strategy
Chat is important to the product, but it is not the first backend milestone.

Phase 1 can use normal persisted messages with polling/manual refresh if needed. Then add WebSocket/SSE/realtime transport once booking/request functionality is stable.

Rules:
- conversations must be authorized by membership;
- messages belong to conversations;
- users cannot enumerate/read conversations they are not part of;
- attachments require type/size controls;
- do not store arbitrary untrusted HTML;
- retain timestamps and sender IDs;
- design for moderation/audit needs without exposing private messages publicly.

## Web frontend
The legacy web frontend is essentially empty.

Do not spend early project time building a second full consumer client. Prioritize Quarkus backend + Flutter.

Later, repurpose `apps/web` as an admin/provider web portal. A small admin interface for dispute resolution may be added when DisputeFlow requires it. Choose the web framework based on the existing environment and project needs at that time; document the decision.

## Testing requirements
No phase is complete when it merely compiles.

Backend minimum:
- unit tests for domain/business rules;
- Quarkus integration tests for REST resources;
- repository tests where queries are non-trivial;
- authorization tests for ownership/roles;
- workflow tests for happy path, reject, timeout, duplicate event/idempotency, transient payment failure/retry, restart/persistence if feasible;
- migration test/startup against PostgreSQL;
- test data builders/fixtures.

Flutter minimum:
- unit tests for important state/business mapping;
- widget tests for critical forms/screens;
- integration test for at least the main customer flow when practical;
- `flutter analyze` clean or with explicitly documented exceptions.

Quality gates:
- `mvn verify` passes;
- formatter/checkstyle strategy is consistent;
- `flutter analyze` passes;
- `flutter test` passes;
- Docker Compose local startup is documented and reproducible;
- OpenAPI endpoint is reachable;
- health endpoint is green;
- no committed secrets.

## Observability
Add from the beginning:
- structured logs with correlation/request IDs where practical;
- health checks;
- metrics;
- workflow/booking identifiers in relevant logs;
- error logs that preserve diagnostic context without exposing sensitive data.

Later add tracing/OpenTelemetry if it fits the current Quarkus stack cleanly.

## Git, GitHub, and change-management rules
The original ZIP contains nested Git repositories. Do not carry nested `.git` directories into the new monorepo. The sibling `quarkus-flow` checkout is an upstream reference and must not become part of the Bliss repository.

The user has explicitly authorized GitHub setup, commits, pushes, issue creation/update, labels, milestones, and pull requests for Bliss. Treat remote synchronization and GitHub Issues as required project-management behavior, not optional extras.

### Repository setup and remote safety
1. Maintain exactly one root Git repository for the new Bliss monorepo.
2. Inspect existing remotes before changing anything. Never overwrite, repoint, delete, or force-update an unrelated remote.
3. If no Bliss GitHub remote exists, create a **private** repository under the authenticated GitHub account, preferring the name `Bliss` and using `Bliss-V2` only if `Bliss` is unavailable. Configure it as `origin` and push the initial branch. Do not make the repository public automatically; public release is a separate product/security decision.
4. Prefer a stable `main` branch plus working branches. For the rebuild, use a branch such as `rebuild/bliss-v2`; for focused work, prefer issue-linked branches such as `feat/42-booking-workflow`, `fix/57-provider-auth`, or `docs/61-portfolio-demo`.
5. Never force-push shared branches or rewrite published history. Do not delete remote branches/repositories unless the user explicitly requests it.

### Commit and push discipline
1. Make small, coherent, reviewable commits. Do not accumulate an entire phase into one giant commit.
2. Use Conventional Commit-style messages where practical and reference the relevant issue number, for example:
   - `chore: bootstrap Bliss V2 monorepo (#1)`
   - `feat(api): add service catalog domain (#12)`
   - `feat(workflow): add durable booking lifecycle (#27)`
   - `feat(mobile): integrate booking timeline (#35)`
   - `test(workflow): cover provider timeout compensation (#27)`
3. Before every commit, run the relevant focused checks/tests for the files changed. Before merging a milestone, run the full applicable quality gates.
4. Push after every meaningful milestone and before ending a work session. Do not intentionally leave completed work only on the local machine.
5. At each synchronization checkpoint run/verify `git status`, inspect the recent log, and confirm the current branch is pushed to `origin` with no accidental secrets/build artifacts.
6. If pushing fails because authentication/network/permissions are unavailable, keep the local commits intact, record the exact blocker in `docs/agent/PROGRESS.md`, and surface the blocker to the user. Never discard work because a push failed.
7. Never commit `.env`, private keys, tokens, passwords, signing files, database dumps containing sensitive data, build outputs, IDE state, or extracted upstream repository artifacts.

### GitHub Issues are the canonical backlog
1. Use GitHub Issues as the canonical list of missing features, bugs, technical debt, security tasks, documentation tasks, and product-readiness work. `docs/agent/PROGRESS.md` is the narrative execution log, not a substitute for the issue backlog.
2. Before creating an issue, search existing open/closed issues to avoid duplicates. Update an existing issue when it already represents the work.
3. During reconnaissance, create issues for every material missing capability or confirmed defect that is not being fixed immediately. During implementation, create newly discovered issues as soon as they are understood well enough to describe.
4. Each issue should contain: problem/context, desired outcome, acceptance criteria, dependencies/blockers, relevant component, and verification/testing notes.
5. Create and maintain labels at minimum:
   - type: `type:feature`, `type:bug`, `type:tech-debt`, `type:docs`, `type:security`, `type:test`
   - area: `area:api`, `area:workflow`, `area:mobile`, `area:web`, `area:database`, `area:infra`, `area:auth`, `area:docs`
   - priority: `priority:P0`, `priority:P1`, `priority:P2`, `priority:P3`
   - state when useful: `status:blocked`, `status:needs-decision`
6. Create milestones at minimum for `Portfolio MVP`, `Portfolio Hardening`, and `Product Beta / Post-MVP` (or equivalent names if matching existing repository conventions).
7. Add `.github/ISSUE_TEMPLATE/` issue forms/templates for Feature, Bug, Technical Debt, and Security/Hardening work. Add a pull-request template containing testing, screenshots/demo evidence when relevant, issue linkage, migration impact, and security checklist items.
8. Link branches/commits/PRs to issues. Use PR descriptions with `Closes #<issue>` only when the PR truly completes the acceptance criteria; otherwise use `Refs #<issue>`.
9. When work is complete and verified, close the issue or allow the merged PR to close it. If partially complete, update the issue with remaining acceptance criteria instead of closing it prematurely.
10. At the end of every phase, reconcile `PLAN.md`, `PROGRESS.md`, and GitHub Issues so there are no important TODOs known only to the agent.

### Pull requests and CI
1. Create `.github/workflows/` CI early. CI should at minimum run backend build/tests and Flutter analysis/tests when those components exist.
2. Prefer pull requests for meaningful feature/milestone branches, especially workflow, auth, database migration, and architecture changes. PRs should reference their issues and include the validation performed.
3. If repository permissions allow autonomous merging, merge only after required checks pass and the branch is up to date. Do not bypass failing checks merely to keep moving; fix or document the blocker.
4. Keep `main` in a demonstrable, buildable state whenever practical.

## Documentation that must exist
Create and maintain:

```text
docs/
├── product/
│   ├── PRODUCT_VISION.md
│   ├── MVP_SCOPE.md
│   └── USER_FLOWS.md
├── architecture/
│   ├── ARCHITECTURE.md
│   ├── DOMAIN_MODEL.md
│   ├── WORKFLOWS.md
│   ├── EVENT_CATALOG.md
│   ├── SECURITY.md
│   ├── ADR-001-modular-monolith.md
│   ├── ADR-002-quarkus-flow.md
│   ├── ADR-003-authentication.md
│   └── ADR-004-payment-abstraction.md
├── api/
│   └── API_GUIDE.md
└── agent/
    ├── RECONNAISSANCE.md
    ├── PLAN.md
    ├── PROGRESS.md
    └── DECISIONS_PENDING.md
```

Keep these short enough to remain useful but detailed enough that another engineer can continue the work.

## Phased execution plan
Work continuously through the phases unless genuinely blocked. Do not ask for confirmation between routine phases. The user explicitly wants you to work autonomously.

### Phase 0 — Reconnaissance, GitHub bootstrap, and safety
- inspect ZIP and Quarkus Flow clone;
- record toolchain/version information;
- preserve legacy source;
- identify useful legacy assets and known defects;
- create plan/progress documents;
- inspect/init the Bliss root Git repository and remote;
- create/configure the private GitHub repository if no correct remote exists;
- push the initial safe baseline;
- create GitHub labels, milestones, issue templates, and initial backlog issues from reconnaissance.

### Phase 1 — Monorepo and local infrastructure
- create clean Bliss monorepo;
- confirm root Git/`origin` are correct and synchronized;
- work from an issue-linked rebuild branch;
- add `.gitignore`, `.editorconfig`, `.env.example`;
- PostgreSQL via Docker Compose;
- create Quarkus API skeleton compatible with Flow;
- create/relocate Flutter app;
- root README with startup commands;
- GitHub Actions CI skeleton;
- commit and push each coherent milestone, updating/closing linked issues as acceptance criteria are met.

Exit criteria: backend starts, DB connects, Flutter analyzes, basic CI commands work.

### Phase 2 — Foundation/domain
- Flyway;
- common error model;
- UUID/time/money conventions;
- User/provider/profile base model;
- service categories and offerings;
- seed demo categories;
- OpenAPI and health.

Exit criteria: catalog API works with tests.

### Phase 3 — Authentication/authorization
- OIDC/Keycloak local development or documented equivalent;
- user bootstrap/profile mapping;
- customer/provider/admin roles;
- resource ownership tests.

Exit criteria: protected endpoints reject unauthorized access and allow valid roles.

### Phase 4 — Requests and quotes
- service requests;
- provider opportunity view;
- quotes;
- quote acceptance invariants;
- booking creation transaction.

Exit criteria: customer request -> provider quote -> accepted quote creates booking.

### Phase 5 — Quarkus Flow booking lifecycle
- integrate Quarkus Flow using current local APIs;
- JPA/Postgres persistence for durable workflow if supported by current clone;
- fake payment gateway;
- provider accept/reject;
- start/complete/customer confirm;
- timeout/retry/compensation;
- workflow tests;
- restart-resume demonstration.

Exit criteria: main flow can be demoed and survives a controlled backend restart while waiting.

### Phase 6 — Mobile vertical slice
- refactor app architecture;
- API client/auth handling;
- customer and provider test login flow;
- request/quote/booking timeline UI;
- actions wired to backend;
- error/loading/empty states.

Exit criteria: two local demo users can complete the primary marketplace lifecycle through the Flutter app.

### Phase 7 — Messaging/chat
- persisted conversations/messages;
- authorization;
- basic mobile chat;
- realtime transport only after persisted chat works.

### Phase 8 — Reviews and dispute flow
- review eligibility;
- provider ratings;
- DisputeFlow;
- minimal authenticated admin resolution endpoint/UI;
- fake payment refund/capture outcomes.

### Phase 9 — Portfolio hardening
- README diagrams/screenshots;
- architecture docs;
- test coverage of key scenarios;
- CI green;
- demo seed script;
- `make`, scripts, or documented one-command setup;
- clean Swagger/OpenAPI;
- sample workflow visualization instructions;
- portfolio talking points.

### Phase 10 — Product-readiness backlog
Do not blindly implement all of these now. Document them as next steps:
- real marketplace payment provider;
- provider identity/KYC/business verification;
- tax handling;
- service-provider insurance/compliance requirements;
- geocoding/maps;
- notification provider (push/email/SMS);
- content moderation;
- object storage/CDN;
- production secrets;
- managed database/backups;
- rate limiting/abuse controls;
- privacy/data retention/deletion workflows;
- legal terms/policies;
- scalable search;
- load/performance testing;
- production deployment topology.

## Legacy defects to avoid copying
The old Python prototype contains examples of issues that justify a clean rebuild:
- hard-coded PostgreSQL credentials;
- blank JWT secret;
- authentication compares a password against a model-class attribute instead of the fetched user's hash;
- one account creation path stores raw passwords;
- SQLAlchemy model attribute collision around `id_company` relationship;
- `Column(float)` usage instead of an SQLAlchemy type;
- schema/entity naming mismatches (`adress` vs `address`, service type vs company ID, etc.);
- Pydantic v1/v2 dependency mismatch;
- multiple commits inside multi-entity registration rather than one safe transaction;
- generic CRUD without ownership authorization;
- `Schedules` is too weak to represent marketplace booking lifecycle;
- no quote/request/payment/chat/dispute domain;
- Flutter creates many `TextEditingController()` instances inside `build()`;
- provider password validation logic appears reversed for numeric/special-character checks;
- nested `MaterialApp` usage;
- generated Flutter counter test is stale;
- no real API integration in mobile;
- web frontend effectively empty;
- nested Git repositories.

Preserve useful UI ideas and business intent, not these implementation mistakes.

## Portfolio demonstration requirements
The completed portfolio demo should be easy to explain in an interview.

Create `docs/product/PORTFOLIO_DEMO.md` with a 5–10 minute demo sequence:

1. Start local infrastructure and Quarkus.
2. Open Swagger/Dev UI and show registered workflow.
3. Customer creates a request.
4. Provider submits quote.
5. Customer accepts quote -> booking + workflow instance created.
6. Fake payment authorization succeeds.
7. Provider accepts.
8. Stop/restart backend while workflow is waiting; show restoration/persistence.
9. Provider starts and marks complete.
10. Customer confirms.
11. Fake payment is captured and booking becomes completed.
12. Leave review.
13. Optional second scenario: customer opens dispute and admin resolves it.
14. Show automated tests/CI and architecture diagram.

README must clearly explain *why* Quarkus Flow is valuable here: the booking process can last hours/days and contains human waits, timeouts, retries, compensation and external side effects.

## Engineering behavior
- Inspect before editing.
- Prefer working code over speculative abstraction.
- Do not create interfaces for every class; abstract external boundaries and true variability.
- Avoid giant services and giant resource classes.
- Keep business transitions explicit.
- Avoid magic strings for statuses/events.
- Use enums/value objects where appropriate.
- Keep DTOs separate from persistence entities.
- Do not expose stack traces to clients.
- Run tests after each meaningful change.
- Fix build/test regressions before starting unrelated features.
- Add comments only where they explain non-obvious intent; do not narrate trivial code.
- Keep generated files/build artifacts out of Git.
- Keep dependencies minimal and justify major additions.
- Never silently disable a failing test just to make CI green.
- When an upstream Quarkus Flow API is unclear, inspect the local source/examples/tests instead of guessing.

## Autonomy and clarification policy
The user wants you to work freely.

Do **not** stop to ask about routine naming, folder placement, DTO shapes, minor UI choices, test organization, or ordinary technical implementation details. Make a sensible decision, record it, and continue.

Ask the user only when a decision has real product/business consequences and cannot be safely deferred, for example:
- whether to spend money or use a paid external service;
- whether to deploy publicly;
- whether to use real payment credentials;
- a legal/business policy whose answer changes money/refund behavior;
- destructive action that could remove irreplaceable user work;
- access credentials that are actually required.

When a product rule is unknown, prefer a configurable placeholder policy and document it rather than blocking the build.

## Progress reporting and synchronization
Maintain `docs/agent/PROGRESS.md` with:
- current phase;
- completed work;
- commands/tests run and result;
- important decisions;
- known issues/blockers;
- current branch and latest pushed commit SHA;
- GitHub issues opened/updated/closed;
- next tasks.

At natural milestones, summarize what changed for the user in plain language. Before ending a work session, ensure completed work is committed and pushed, GitHub Issues reflect remaining work accurately, CI/validation status is recorded, and no known TODO exists only in ephemeral chat/context. Do not overwhelm the user with every shell command.

## Definition of done for Bliss V2 portfolio release
Bliss V2 is portfolio-ready when all of the following are true:

- clean monorepo with no nested Git repos;
- the correct GitHub `origin` is configured and all completed portfolio-release work is committed and pushed;
- GitHub Issues accurately represent remaining work, with completed MVP issues closed and unresolved work assigned to an appropriate milestone;
- CI configuration exists in `.github/workflows/` and the intended release branch is green;
- Java 21 Quarkus backend builds from a clean checkout;
- PostgreSQL + Flyway works from local Docker setup;
- authentication and server-side authorization exist;
- provider profile/category/offering functionality works;
- request -> quote -> booking flow works;
- BookingLifecycleFlow is implemented with Quarkus Flow;
- workflow persistence/restoration is demonstrable;
- fake payment authorize/capture/void/refund behavior is integrated and tested;
- Flutter client is actually connected to the API;
- customer and provider can complete the main flow;
- persisted chat or a documented working first version exists;
- completed bookings can be reviewed;
- a dispute happy path exists or is clearly implemented as the secondary workflow;
- automated backend tests pass;
- Flutter analyze/tests pass;
- no secrets are committed;
- OpenAPI/Swagger and health endpoints work;
- root README contains setup, architecture, screenshots/demo instructions, and Quarkus Flow explanation;
- CI configuration exists and is green when run in the intended environment;
- portfolio demo script is documented.

## Start now
Begin with reconnaissance. Do not attempt a giant one-shot rewrite before understanding the ZIP and the local Quarkus Flow checkout. Create the plan, then execute the phases continuously, validating each vertical slice before expanding scope.
