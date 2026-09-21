# API guide

Base URL: `http://localhost:8088`. Version prefix: `/api/v1`.

| Method | Path | Current behavior |
|---|---|---|
| GET | /categories | Public reference categories |
| GET | /offerings | Active offerings, optional categoryId UUID; page 0–100000, size 1–100 (default 20) |
| GET | /offerings/{id} | Active offering or 404 |
| GET | /providers/{id} | Public profile or 404 |
| POST | /me/bootstrap | Authenticated; idempotent profile creation from token subject |
| GET | /me | Authenticated caller's profile and signed capabilities |
| PUT | /me | Update own displayName (1–120 nonblank characters) |
| GET | /providers/me | Provider role; own memberships only |
| POST | /providers/me | Provider role; create individual/company profile and OWNER membership atomically |
| GET | /providers/me/{id} | Provider role + OWNER; private profile including current version |
| PUT | /providers/me/{id} | Provider role + OWNER membership; displayName, description, city, version |
| GET | /providers/me/{id}/offerings | OWNER; own active and archived services; bounded page/size |
| POST | /providers/me/{id}/offerings | OWNER; publish an offering (201 + Location) |
| GET | /providers/me/{id}/offerings/{offeringId} | OWNER; managed service including active/version |
| PUT | /providers/me/{id}/offerings/{offeringId} | OWNER; update active offering with expected version |
| DELETE | /providers/me/{id}/offerings/{offeringId}?version=N | OWNER; archive, retaining history (204) |
| GET / POST | /service-requests | Customer's paged requests / create own request |
| GET / PUT / DELETE | /service-requests/{id} | Own request; update with expected version or cancel with ?version=N |
| GET | /provider/opportunities | Provider role; paged OPEN requests excluding caller's own requests; optional categoryId |
| GET | /provider/opportunities/{id} | Open opportunity without customer identity/contact fields |
| POST | /service-requests/{id}/quotes | Provider role + OWNER; submit an expiring quote |
| GET | /service-requests/{id}/quotes | Request owner; paged quotes |
| GET | /providers/me/{id}/quotes | Provider OWNER; own paged quotes |
| GET | /quotes/{id} | Request customer or quoting provider OWNER |
| POST | /quotes/{id}/accept | Request owner; atomically create booking, repeats return same booking |
| POST | /quotes/{id}/decline | Request owner; decline submitted quote |
| GET | /bookings | Own customer/provider bookings, paged |
| GET | /bookings/{id} | Booking customer or assigned provider OWNER |
| GET | /bookings/{id}/history | Same ownership; ordered status timeline with bounded page/size |
| GET | /bookings/{id}/payment | Same ownership; simulated status, amount, currency and operations |
| GET | /bookings/{id}/execution | Same ownership; engine instance ID, status and checkpoint presence |
| POST | /bookings/{id}/provider-accept | Assigned provider OWNER; future scheduledAt JSON body |
| POST | /bookings/{id}/provider-reject | Assigned provider OWNER; reject before deadline |
| POST | /bookings/{id}/start | Assigned provider OWNER; start after scheduled time |
| POST | /bookings/{id}/mark-complete | Assigned provider OWNER; mark IN_PROGRESS work complete |
| POST | /bookings/{id}/confirm-completion | Booking customer; confirm WAIT_CUSTOMER work and simulate capture |
| POST | /bookings/{id}/cancel | Booking customer; cancel CREATED/WAIT_PROVIDER/SCHEDULED |

Lists of offerings return `{items, page, size, total}`. UUID identifiers; exact
decimal `price` strings; explicit currency. Inactive offerings are not discoverable.
All reads use DTOs. Unexpected server errors return a generic problem response;
`X-Request-ID` identifies the request in diagnosis. Bean Validation rejects invalid
pagination; malformed typed parameters are rejected by the REST layer.

`/q/openapi`, `/q/swagger-ui`, `/q/health/ready`, `/q/metrics` expose local developer
and operational information. Sign-in is hosted by Keycloak, not an API password
endpoint. Protected calls need `Authorization: Bearer <access token>`. Unknown
JSON properties are rejected. Provider version conflicts return 409; wrong role
returns 403; non-owned providers return 404.

Provider creation accepts `displayName`, `type` (`INDIVIDUAL` or `COMPANY`),
`description` and `city`. It never grants an identity-server role. A single account
may own multiple provider profiles; existing MEMBER memberships are not editors.

Offering creation accepts `title`, `description`, `categoryId`, `price`, `currency`.
Updates wrap these fields in `offering`, alongside `version`. Price is a decimal
string with up to ten integer and two fractional digits, non-negative and never
rounded. Currency must be a recognized ISO currency with two minor-unit digits;
other currency scales are outside this portfolio schema. Unknown categories and
extra JSON properties return 400. Archive hides discovery/detail reads and prevents
further edits. Repeating archive at its current version is a no-op; stale versions
return 409. All successful mutations record audit metadata in the same transaction.

Requests accept `title`, `description`, `city`, `categoryId`, optional decimal-string
`budget`, and `currency`. Updates wrap this input in `request` alongside `version`.
Scope/currency become immutable once any quote exists; cancellation retains history
and declines outstanding quotes. Accepted requests cannot be edited or cancelled
through the request endpoint. Use the separate booking cancellation action.

Quotes accept `providerId`, `description`, decimal-string `price`, matching request
`currency`, and future UTC `expiresAt`. The default maximum validity is 30 days,
configurable with `bliss.quotes.max-validity-seconds`. This is a local demo policy.
There is one quote per provider/request; revisions/withdrawal are deferred.

Acceptance locks the parent request, verifies customer ownership and quote expiry,
then accepts one quote, declines alternatives, creates the booking with an exact
price snapshot and initial history, and records audit events in one transaction.
Unique constraints enforce one booking and one accepted quote per request.
Same-quote retries return the same booking; competing quote acceptance returns 409.
The initial booking is `CREATED`; a start request committed with it dispatches
BookingLifecycleFlow asynchronously, simulating authorization and WAIT_PROVIDER.

State transitions use explicit actions, never a generic client-selected
status PATCH. Authenticated resources derive identity from the validated token.

Booking actions require a UUID `Idempotency-Key` header and return 202 with
`{eventId, bookingId, type, processed}` after durable admission. Poll the booking
for processing (normally within two seconds). Reuse the same key and payload when
retrying an uncertain response. A different payload on the same key, a pending
competing action, or an invalid transition returns 409; a missing key returns 400.
Provider acceptance requires `{"scheduledAt":"2026-12-01T15:00:00Z"}` with a future
UTC time and no finer than microsecond precision. Other actions need no body.

Booking DTOs include `workflowInstanceId`, `scheduledAt` and `decisionDeadline`.
Payment reads always declare `simulated: true`; NOT_AUTHORIZED means no ledger
exists yet (including cancellation before dispatch). There is no public refund
endpoint yet. See [workflow policy and recovery](../architecture/WORKFLOWS.md).
