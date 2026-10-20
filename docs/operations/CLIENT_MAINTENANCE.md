# Flutter access during an API write pause

The Flutter client supports the optional [REST write pause](MAINTENANCE_ADMISSION.md)
without requiring an existing account to bootstrap successfully before reading.
Normal sign-in and restore still POST `/api/v1/me/bootstrap` first, preserving
normal account creation and capability synchronization. Only an HTTP 503 from
that request triggers GET `/api/v1/me` with the current access token.

A successful profile read opens the existing account and its ordinary authorized
screens. The server continues to enforce owner, participant and role boundaries;
the fallback grants no additional capability. The client does not infer that
maintenance caused every 503, nor does a successful profile read prove that all
other API services are available. Write buttons remain subject to the server's
admission policy rather than a cached client maintenance flag.

If bootstrap returns 503 and the profile does not exist (GET 404), the original
unavailability message remains. A new account must wait for write admission to
reopen. An unavailable profile offers **Retry account access** and **Sign out**.
Retry repeats bootstrap using the current token provider, including its normal
refresh behavior, without starting another interactive sign-in. There is no
automatic bootstrap polling. Bootstrap responses other than 503 do not trigger
the read fallback. HTTP 401 clears the local session/retry state and returns the
normal sign-in action; other authorization failures do not become read retries.

HTTP 503 displays the fixed message: “Bliss is temporarily unavailable. Please try
again later.” Raw server bodies, submitted evidence and infrastructure details
are not used as error messages. No reopening time or Retry-After is promised.

## Pending writes and reopening

The existing idempotent submission mechanism retains an uncertain/5xx submission's
body and key in the current `AuthSession`. A paused evidence submission shows the
fixed message and keeps its original details locked. **Retry original submission**
explicitly sends that same body and key after the operator reopens writes. It does
not generate a new key, change the evidence or automatically retry in the
background. A successful response clears the pending entry and reloads the case.
This behavior uses the existing pending-write implementation; the new slice adds
503 messaging and verifies its maintenance behavior.

Pending bodies and keys exist only in memory. Sign-out/session clearing removes
them; app termination or browser reload does not provide a durable offline queue.
Other forms retain their existing retry contracts. Operators must still establish
readiness, worker ownership and settlement state using the backend procedure.

## Verification and remaining acceptance

`auth_session_test.dart` covers the 503-only fallback, missing/unavailable profile,
manual retry without another sign-in, non-503 refusals and fallback 401.
`account_recovery_test.dart` exercises retry and sign-out at a 320-pixel phone
width. `dispute_test.dart` submits evidence, receives 503, waits without another
POST, then reopens and verifies an identical body/key on explicit retry.

These are unit/widget tests with synthetic tokens and HTTP responses. Release web
and debug Android compilation are separate checks; they do not establish real
Keycloak, native-device, iOS or hosted maintenance acceptance. The parent backend
maintenance drill remains the evidence for actual REST admission, database
preservation and workflow reopening; no backend source changes are needed here.
See [progress](../agent/PROGRESS.md) for executed checks and
[readiness](READINESS.md) for required CI/native/production gates.


On the first pushed head, both hosted iOS browser/marketplace jobs reached the
real API and then exceeded the runner's 30-second simulator inventory deadline,
before any UI test. Discovery now has a bounded 180-second allowance, retaining
its process-group termination and all existing boot/UI deadlines and assertions.
The PR records subsequent remote results; local Windows compilation does not
verify this macOS startup adjustment.

The next hosted browser run confirms inventory completes in about 52 seconds and
reaches XCTest. Its first Keycloak page is still at 10% loading when the original
30-second credential-form wait expires. That shared UI helper now permits a
bounded 90 seconds for the actual form; it still requires fresh credentials,
realm identity, authenticated profile, logout and account-switch assertions.
No silent SSO or sign-in bypass is accepted. The separate native session workflow
passes at the initial unchanged Flutter app source; exact-head follow-up results
remain recorded in the PR/backlog.
