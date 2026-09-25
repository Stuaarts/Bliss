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

## Booking API and restart demo

Run `services/api/mvnw verify` (Java 21 and Docker). The workflow tests cover
accept/start/complete/confirm, provider rejection, timeout, customer cancellation,
duplicates, transient payment retry, compensation and ownership rejection.
Failsafe's `WorkflowRestartIT` starts a packaged API on a temporary loopback port
and an isolated PostgreSQL container. It forcibly kills the API during provider
and customer waits, restarts against the same database, checks the original
workflow ID and confirms exactly one simulated capture or void. Read the test
results in `target/failsafe-reports` and `target/restart-check-server.log`.

For an interactive API demo, use customer/provider bearer tokens from the local
Keycloak session with the [API guide](../api/API_GUIDE.md): create a request, quote
and accept; poll the resulting booking until WAIT_PROVIDER. Show `/history`,
`/payment` and `/execution`. Accept as the assigned provider with a future schedule
and a fresh UUID Idempotency-Key. After that time, start, mark complete and confirm
as the customer. Repeat confirmation with the same key; payment operations still
contain exactly one AUTHORIZE and one CAPTURE. All payments are simulations.
The same flow is now available through Flutter's **Your activity** screens;
see [the verified browser demo and screenshots](../demo/MARKETPLACE.md). Native
device validation remains #7.

For the direct path, get an active offering from discovery, retain its `version`,
then submit `/api/v1/offerings/{id}/bookings` as a customer with that offeringVersion,
city and a fresh UUID Idempotency-Key. Show the original price/scope on the resulting
booking. Repeat the same request: the original ID is returned with no second booking.
Edit the offering and attempt a new booking with the old version: 409 requires a
fresh review. Continue the first booking through the same provider/customer actions.
Automated migration tests also verify upgrading an existing quote booking from V5;
packaged restart tests cover both quote and direct booking sources.

## Available chat demo

Open an owned booking's Messages as the customer, send a demo message, sign out,
then sign in as its provider and reply through Your activity → Messages. Restart
the API without resetting PostgreSQL and reopen as the customer to show both
messages. See [verified steps and screenshots](../demo/CHAT.md).

## Available reviews demo

Open a completed booking as its customer → Booking review → choose 1–5 stars and
optionally describe the service → Publish review. Reopening shows the saved review.
Sign out, open an offering from that provider → Provider reviews, and show its
average/count and comment. No booking/customer identity is included in public
responses. See [verification and limits](../demo/REVIEWS.md).

## Target 5–10 minute full release demo (remaining dispute work)

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
