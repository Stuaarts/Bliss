# Domain model

Current Flyway schema:

- bliss_user: UUID, unique OIDC subject, display name, UTC creation time. No password.
- user_role: multiple capabilities per user.
- provider_profile: INDIVIDUAL or COMPANY, public description/city and version.
- provider_member: composite provider/user membership with OWNER or MEMBER.
- service_category: stable UUID and unique slug.
- service_offering: provider/category references, active flag, decimal price,
  explicit uppercase currency and optimistic version.

Public reads return DTOs with decimal strings so clients do not round money through
binary floating point. Lists have deterministic title/id ordering and bounded pages.
Reference categories are migrated globally; sample providers/offerings are local/test only.

Future slices add customer profiles, addresses, service areas, availability/blackouts,
requests/attachments, quotes, bookings/history, payments/transactions, conversations/
participants/messages, notifications, reviews, verification, disputes/evidence and
audit records. Add outbox/idempotency tables with the first reliable mutation use case.
Do not precreate empty feature tables merely to match a diagram.
