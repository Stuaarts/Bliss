# Security assumptions

This is a local portfolio foundation with public catalog reads and authenticated
profile/provider/marketplace mutations. It is not approved for public deployment
or private customer data.

The API binds loopback; Compose publishes PostgreSQL on loopback. `.env` is ignored;
configuration contains placeholders. Demo data has no real identities. Random
local demo credentials are ignored and must never be reused outside this demo.
CI database credentials are disposable and valid only inside each test runner.
Legacy files containing credentials are excluded, preserved and never started.

Identity uses OIDC/Keycloak and Flutter Authorization Code + PKCE. Bliss
stores subject references, not passwords. Every mutation must enforce subject,
role and membership/ownership server-side, including negative tests. Do not infer
authorization from submitted user/provider UUIDs. No government IDs or payment cards.

Foundation controls: bounded page parameters, UUID conversion, prepared ORM
parameters, nonnegative decimal DB constraint, DTO boundaries, generic server
error responses and generated request correlation IDs. Detailed server errors
stay in logs. Android HTTP is debug-only; real deployments require HTTPS.
OIDC provider warning messages that can include rejected token material are
suppressed. Audit events retain actor/resource IDs and action names, not tokens
or submitted profile text. No access/refresh token is stored in web localStorage.

Opportunity reads expose only job scope, category, city and budget to providers;
no customer identity/contact fields. Quote submission requires provider OWNER
membership and excludes the request's customer identity. Acceptance/decline require
the request customer; booking reads require its customer or assigned provider owner.
Parent-request locks serialize competing actions. Database uniqueness and the
transactional history/audit prevent duplicate booking creation on acceptance retry.

Booking actions independently require the customer or assigned provider OWNER
and the appropriate signed capability. UUID action keys are bound to actor,
booking, action and payload; exact retries return the same receipt. The durable
inbox, transition history, audit and fake ledger share transaction boundaries.
History/payment/execution reads enforce the same booking access checks. Engine
status is visible without exposing raw exception details. Payment simulation is
enforced in the schema; no real payment adapter or credentials are configured.

Direct bookings derive the customer from the token and all scope/price/provider
fields from the locked offering. Expected offering versions prevent accepting a
price changed since discovery. Customer-scoped idempotency keys bind the offering,
version and normalized city; concurrent retries create a single booking/start intent.
Archived offerings reject new bookings while original receipts remain retrievable
by their customer. Provider OWNER and MEMBER identities cannot self-book that provider.

OpenAPI/health/metrics are local development endpoints. Scope management access,
rate limiting, retention, backup/recovery and abuse controls before product beta (#15).
