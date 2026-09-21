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
  initial CREATED status, nullable future workflow instance ID and version.
- booking_status_history: transactional history, beginning with CREATED.
- audit_event: actor/resource/action metadata, no submitted text or tokens.

Public reads return DTOs with decimal strings so clients do not round money through
binary floating point. Lists have deterministic title/id ordering and bounded pages.
Reference categories are migrated globally; sample providers/offerings are local/test only.

Future slices add customer profiles, addresses, service areas, availability/blackouts,
request attachments, booking execution, payments/transactions, conversations/
participants/messages, notifications, reviews, verification, disputes/evidence and
audit extensions. Booking acceptance is idempotent via its unique request/quote
constraints and serialized parent-request transaction. Add an outbox with workflow
dispatch so committing a booking never loses its later orchestration start.
Do not precreate empty feature tables merely to match a diagram.
