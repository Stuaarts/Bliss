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

From the repository root, run `./services/api/mvnw.cmd -f services/api/pom.xml verify`
on Windows, or `./services/api/mvnw -f services/api/pom.xml verify` on macOS/Linux
(Java 21 and Docker). The workflow tests cover
accept/start/complete/confirm, provider rejection, timeout, customer cancellation,
duplicates, transient payment retry, compensation and ownership rejection.
Failsafe's `WorkflowRestartIT` starts a packaged API on a temporary loopback port
and an isolated PostgreSQL container. It forcibly kills the API during provider
and customer waits, restarts against the same database, checks the original
workflow ID and confirms exactly one simulated capture or void. Read the test
results in `services/api/target/failsafe-reports` and
`services/api/target/restart-check-server.log`.

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

## Available dispute demo

An eligible customer opens a case from the booking; its provider submits evidence.
Sign in as an independent admin to read evidence and record a reasoned outcome.
Watch the case resolve and reopen the booking to show simulated refund/capture.
See [dispute steps and recovery checks](../demo/DISPUTES.md).

## Timed web walkthrough

Allow 8–10 minutes **after** installing prerequisites and warming the local stack.
This is a presentation script assembled from verified browser flows and packaged
restart tests, not a claim that a narrated video or native-device run was recorded.

Before presenting, follow the root README's backend and frontend commands. Keep
the API in its own terminal so Ctrl+C stops only that process; leave PostgreSQL
and Keycloak running during the restart. Use the generated demo credentials in
private, and create a provider-owned business once if the database is fresh.
Use a new request title each run; existing bookings, reviews and cases are retained.
Do not reset the database to repeat the demo.

| Time | Demonstration | What to point out |
| --- | --- | --- |
| 0:00–0:45 | Open catalog, readiness, Swagger and Dev UI | Real PostgreSQL data, API contracts and both registered workflows |
| 0:45–2:00 | Customer creates “Demo garden help”; provider sends CAD 85.50 quote | Scope, city, currency and current provider ownership are explicit |
| 2:00–3:00 | Customer accepts; open booking, then provider accepts and schedules roughly two minutes ahead | Booking snapshot, pending action then persisted status, simulated authorization |
| 3:00–4:00 | Stop only the API with Ctrl+C; rerun the same backend command | Database stays running; reload the booking after readiness returns UP |
| 4:00–5:00 | Provider starts once the scheduled time arrives, then marks work complete | No premature start; workflow now waits for customer confirmation |
| 5:00–6:00 | Customer confirms, publishes a review; show public provider reviews | One capture and one review; public response excludes customer/booking identity |
| 6:00–7:00 | Open Messages as each party and exchange a clearly labeled demo message | Persisted participant-scoped text with safe retry and pagination |
| 7:00–9:00 | Customer opens Dispute / support; provider adds evidence; independent admin resolves in customer’s favor | Evidence closes, manual decision is admitted, then Flow refunds the captured fake payment |
| 9:00–10:00 | Customer reopens refunded booking/case; show CI and architecture | One AUTHORIZE/CAPTURE/REFUND, durable history, green gates and documented limits |

For exact instance-ID proof during the live restart, use Swagger's protected
`GET /api/v1/bookings/{id}/execution` with the booking customer's local bearer
session before and after stopping the API. Keep tokens off-screen and out of files
or presentation materials. The returned instance ID must match. Alternatively,
present the packaged `WorkflowRestartIT` and `DisputeRestartIT` results, which
force process crashes and assert the same identity and one settlement. A booking
list surviving a restart alone does not prove workflow checkpoint restoration.

Do not run Maven verify while dev mode uses the same API target directory. Run
quality gates before the presentation and show the saved/CI results. Dev UI's
Flow card identifies `com.bliss/booking-lifecycle/1.0.0` and
`com.bliss/dispute-resolution/1.0.0`; use the Mermaid diagrams in
[WORKFLOWS.md](../architecture/WORKFLOWS.md) if the installed Dev UI layout differs.

## Interview talking points

- A modular monolith keeps business transactions local while separating catalog,
  identity, marketplace, chat/reviews and disputes by capability.
- A booking spans human waits and restarts. Flow checkpoints orchestrate progress;
  domain rows remain authoritative for readable status, history and authorization.
- HTTP admission is distinct from settlement. Durable rows, transaction locks and
  idempotency constraints keep retries and competing decisions from duplicating effects.
- Fake payment effects share the database transaction. A real provider would need
  an outbox, provider idempotency and reconciliation before any live money moves.
- Actual process-crash tests exposed two pinned Flow JPA restoration issues and a
  test observation race; fixes are local, documented and protected by regressions.

See the [release checklist](RELEASE_CHECKLIST.md) for verified scope and open gates.
