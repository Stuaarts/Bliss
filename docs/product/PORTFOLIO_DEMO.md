# Portfolio demo

Provider management: sign in as the local provider, open **Your account → Manage
your business**, then add an individual or company profile. Publish a service with
a category and exact CAD price; discover it from the public catalog. Edit it, then
archive it and show that public discovery no longer includes it while provider
history retains it. Customer-only accounts cannot access these mutations.

## Available foundation demo

Prerequisite: a working PostgreSQL setup. Start Compose and API with the root README,
then Flutter web. Browse categories, inspect an offering, show the Swagger schema,
readiness and metrics, and run catalog/Flutter tests. This is not the full release demo.

Open **Your account**, sign in as the generated local `provider` user, show both
capabilities, edit the display name and sign out. Credentials are in ignored
`.work/demo-accounts.json`. This flow has been verified against live Keycloak and
PostgreSQL. Android/iOS device validation is still pending.

## Target 5–10 minute release demo (not implemented yet)

1. Start PostgreSQL, Keycloak and Quarkus; show Swagger and registered workflow.
2. Customer logs in and creates a request; provider submits a quote.
3. Customer accepts: one booking and durable instance; fake authorization recorded.
4. Provider accepts; show a human wait with queryable status/history.
5. Stop/restart the backend; prove restoration of the same instance.
6. Provider starts and marks complete; customer confirms.
7. Show idempotent fake capture, completed state and eligible review.
8. Optional second booking: dispute, manual admin resolution and fake refund/capture.
9. Show passing CI, duplicate/timeout/retry tests and architecture rationale.

Do not represent the target sequence as a working demo until all steps have actual
evidence. Record screenshots and restart/test results with #12.
