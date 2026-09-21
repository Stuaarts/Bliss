# Security assumptions

This is a local portfolio foundation with public catalog reads and authenticated
profile mutations. It is not approved for public deployment or private customer data.

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

OpenAPI/health/metrics are local development endpoints. Scope management access,
rate limiting, retention, backup/recovery and abuse controls before product beta (#15).
