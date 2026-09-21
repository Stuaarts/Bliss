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
- booking: unique request/quote, customer/provider, price and title/city snapshot,
  lifecycle status, durable workflow instance ID, schedule/decision deadline and version.
- booking_status_history: transactional history from CREATED through terminal state.
- audit_event: actor/resource/action metadata, nullable actor for system transitions.
- booking_workflow_start: durable dispatch intent, canonical claim and started marker.
- booking_event: persistent UUID-keyed action inbox with actor, payload and timestamps.
- payment/payment_transaction: simulated amount/currency/status and unique operation ledger.
- workflow_instance_entity/task_info_entity/cloud_event_entity: released Flow JPA schema.

Public reads return DTOs with decimal strings so clients do not round money through
binary floating point. Lists have deterministic title/id ordering and bounded pages.
Reference categories are migrated globally; sample providers/offerings are local/test only.

Future slices add customer profiles, addresses, service areas, availability/blackouts,
request attachments, conversations/
participants/messages, notifications, reviews, verification, disputes/evidence and
audit extensions. Booking acceptance is idempotent via its unique request/quote
constraints and serialized parent-request transaction. The durable start queue
is committed with booking creation so orchestration can recover after a crash.
Do not precreate empty feature tables merely to match a diagram.
