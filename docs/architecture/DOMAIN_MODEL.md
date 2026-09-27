# Domain model

Current Flyway schema:

- bliss_user: UUID, unique OIDC subject, display name, UTC creation time. No password.
- user_role: multiple capabilities per user.
- provider_profile: INDIVIDUAL or COMPANY, public description/city and version.
- provider_member: composite provider/user membership with OWNER or MEMBER.
- service_category: stable UUID and unique slug.
- service_offering: provider/category references, active flag, decimal price,
  explicit uppercase currency and optimistic version.
- service_request: own customer/category, city and scope, optional budget, currency,
  OPEN/ACCEPTED/CANCELLED status and version. Scope freezes once quoted.
- quote: request/provider, immutable scope and exact price, UTC expiry and
  SUBMITTED/ACCEPTED/DECLINED status. Unique provider/request and accepted request.
- booking: either unique request/quote or offering/version with a customer-scoped
  direct idempotency key; customer/provider, price and title/description/city snapshot,
  lifecycle status, durable workflow instance ID, schedule/decision deadline and version.
- booking_status_history: transactional history from CREATED through terminal state.
- audit_event: actor/resource/action metadata, nullable actor for system transitions.
- booking_workflow_start: durable dispatch intent, canonical claim and started marker.
- booking_event: persistent UUID-keyed action inbox with actor, payload and timestamps.
- payment/payment_transaction: simulated amount/currency/status and unique operation ledger.
- workflow_instance_entity/task_info_entity/cloud_event_entity: released Flow JPA schema.
- conversation: unique booking, creation time and serialized message sequence counter.
- conversation_participant: conversation/user key and CUSTOMER/PROVIDER role snapshot.
- conversation_message: participant sender, plain text, UTC timestamp, unique
  conversation sequence and sender-scoped UUID retry key.
- booking_review: unique booking reference, constrained integer rating 1–5,
  optional plain-text comment and UTC creation time. Customer/provider are derived
  from the immutable booking relationship; public reads omit those private IDs.

Public reads return DTOs with decimal strings so clients do not round money through
binary floating point. Lists have deterministic title/id ordering and bounded pages.
Reference categories are migrated globally; sample providers/offerings are local/test only.

Future slices add customer profiles, addresses, service areas, availability/blackouts,
request attachments, notifications, verification and
audit extensions. Booking acceptance is idempotent via its unique request/quote
constraints and serialized parent-request transaction. The durable start queue
is committed with booking creation so orchestration can recover after a crash.
V6 backfills accepted quote scope into existing bookings and enforces exactly one
booking source. Direct creation locks the customer for key serialization, then the
offering to serialize snapshot admission against edits/archive. A unique customer/key
index provides a second duplicate barrier. Both paths share BookingCreation for
initial history, audit and dispatch. Offering changes never rewrite booking scope.
Do not precreate empty feature tables merely to match a diagram.

V7 adds chat tables without changing existing bookings. A booking lock serializes
first open and snapshots its customer and current provider OWNER identities.
Every read/write also requires their current capability and booking access.
Conversation row locking allocates sequences and commits messages together, so
cursor polling cannot skip a message whose transaction committed late. Message
sender membership and retry/sequence uniqueness are also enforced by the database.

V8 adds reviews. The booking lock serializes review submissions, checks COMPLETED
status and the owning customer's current capability, and makes an identical retry
return the original review. Changed duplicates return 409. Database uniqueness
prevents a second review per booking. Provider averages/counts are calculated from
all their reviews, independently of the current page; no stale aggregate is stored.

V9 adds one dispute per booking, evidence, an immutable admin decision, case history
and durable workflow start intent. All admissions and settlement lock the booking
before the case; unique keys also prevent duplicate cases/evidence/decisions.
DISPUTED hands settlement ownership to DisputeFlow; REFUNDED is terminal.
Case deadlines and workflow identity survive restarts. A content-free failure
timestamp survives Flow's deletion of failed checkpoints. V9 changes allowed
booking states: do not roll back to an older binary while cases or these statuses
exist. Preserve evidence/history; plan a forward migration instead of dropping data.

V10 rejects numeric NaN at every money boundary, validates payment currency and
history state values, and enforces dispute deadline/resolution timestamp consistency.
It adds constraints without rewriting existing records. See the
[database verification matrix and upgrade preflight](DATABASE_INVARIANTS.md) for
populated V5/V8/V9 upgrades, direct constraint tests and application-only rules.
