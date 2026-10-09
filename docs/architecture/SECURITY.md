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

Native refresh tokens use platform secure storage. Session generations reject
refresh/authorization results and storage reads from before logout or account
replacement. Writes/deletes are serialized so a pending platform write cannot
finish after logout's deletion. Storage failures are reported, not treated as
successful persistence. Android fixture tests verify storage across process death,
native refresh/rotation and local logout. A separate real Keycloak Android test
verifies browser PKCE, customer/provider recovery after process death, identity-provider
logout and account switching. The separate [iOS native fixture](../demo/IOS.md)
and [real iOS browser test](../demo/IOS_AUTH.md) now verify simulator Keychain/
refresh behavior and customer/provider browser callbacks, process recovery,
logout and account switching. The [iOS marketplace scenario](../demo/IOS_MARKETPLACE.md)
also verifies request-to-review with real services. Secondary iOS paths and
physical-device validation remain open. See also [Android evidence](../demo/ANDROID.md).

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

Chat requires both persisted conversation membership and current booking access:
the customer with customer capability, or a snapshotted provider OWNER who still
has provider capability and OWNER membership. MEMBER and admin alone grant no
chat access; hidden/missing conversations return 404. New provider owners do not
inherit historical conversations. Participant transfer/invitation requires a future
explicit policy. Sender identity is derived from the authenticated subject.
Messages are bounded plain text (2000 UTF-16 code units), never interpreted as HTML.
Audit records contain IDs/actions, not message bodies. Messages are stored in
PostgreSQL; this is not end-to-end encryption. No delete/edit/retention policy is
implemented yet. UUID send keys are scoped to conversation and sender and bind
normalized text; duplicate retries never create another message or audit event.

Review creation requires the booking customer and current customer capability,
with the booking locked and COMPLETED. Provider/admin roles never bypass this.
Public review DTOs expose only review ID, rating, comment and creation time;
booking/customer IDs, display names and OIDC subjects are excluded. Comments are
plain text, bounded to 2000 UTF-16 code units, with NUL rejected; audit records omit
the text. Flutter explicitly explains public visibility before publishing. Reviews
are immutable in this slice. Moderation, reporting, retention and removal policies
remain product-beta work; do not present this as a production moderation system.

OpenAPI/health/metrics are local development endpoints. Scope management access,
rate limiting, retention, backup/recovery and abuse controls before product beta (#15).

Disputes are private to the current customer/provider owner and authenticated
admins. Identity and evidence role come from the token and database, never request
fields. Admins who are the booking customer or any provider member cannot decide
their own case. Case/evidence/decision text is bounded plain text; audit and Flow
input contain IDs/actions without that content. All participants can read the
shared evidence and admin rationale; these are PostgreSQL records, not end-to-end
encrypted messages. Current provider owners inherit case access, unlike chat's
participant snapshot. No attachment, appeal, edit/delete, moderation or retention
workflow exists yet (#15). Admin capability grants case reads, not global chat or
booking reads. UI clears private state on logout/access loss and rejects stale
responses. Final admin choices only affect simulated payments in the local demo.
