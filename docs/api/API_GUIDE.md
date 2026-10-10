# API guide

Base URL: `http://localhost:8088`. Version prefix: `/api/v1`.

| Method | Path | Current behavior |
|---|---|---|
| GET | /categories | Public reference categories |
| GET | /offerings | Active offerings, optional categoryId UUID; page 0–100000, size 1–100 (default 20) |
| GET | /offerings/{id} | Active offering or 404 |
| POST | /offerings/{id}/bookings | Customer; direct booking with offeringVersion, city and UUID Idempotency-Key |
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
All reads use DTOs. Endpoint constraint violations return a generic correlated
400 problem; invalid endpoint return contracts remain generic 500 problems.
Validation paths/values are not included in these responses. Unexpected server
errors return a generic problem response;
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

## Direct offering booking

Read `/offerings/{id}` and retain its `version` alongside the displayed price/scope.
Submit `POST /offerings/{id}/bookings` with `Idempotency-Key: <UUID>` and
`{"offeringVersion":0,"city":"Toronto"}`. The customer role and initialized
profile are required. The server supplies the provider, title, description, amount
and currency from the active offering; client identity/price/status fields are rejected.
Only the service city is collected here; coverage and appointment availability are
not guaranteed. The provider subsequently accepts and supplies a future schedule.

A new booking returns 201 with Location; an exact retry returns 200 and the original
booking, even after the offering is changed/archived or the booking is cancelled.
Keys are scoped to the authenticated customer. Reusing a key for a different offering,
version or city returns 409. City whitespace is trimmed before comparison. Use a
new key only for a new intentional booking. Different customers can use the same
UUID without gaining access to each other's booking.

Stale offering versions return 409: reload and review the new price/scope before
submitting a new request. Inactive/missing offerings return 404 for new bookings.
An account belonging to the offering's provider (OWNER or MEMBER) cannot book it;
that returns 409 even when the account also has customer capability. Missing key,
version, blank/overlong city or unknown fields return 400. Concurrent exact retries
create one booking, initial history/audit event and durable workflow start intent.

Direct booking DTOs have `offeringId` and `offeringVersion`, with null `requestId`
and `quoteId`. Quote bookings have the inverse source fields. Both include the
immutable `description` snapshot and use the same booking actions, authorization,
payment simulation and restart recovery.

## Booking conversations

All paths below use `/api/v1` and require an authenticated, initialized profile.

| Method/path | Behavior |
| --- | --- |
| `POST /bookings/{id}/conversation` | Open or return the booking's unique conversation; 200, no body/key needed. |
| `GET /conversations?page=0&size=20` | Owned participant conversations, newest-created first; standard page envelope. |
| `GET /conversations/{id}` | Conversation ID, booking ID/title, createdAt and lastSequence. |
| `GET /conversations/{id}/messages?size=50` | Latest messages, returned in ascending sequence order. |
| `POST /conversations/{id}/messages` | Send `{"body":"Hello"}` with UUID `Idempotency-Key`; 200 message receipt. |

Conversation pages accept page 0–100000 and size 1–100. Message reads accept size
1–100 (default 50), returning `{items,hasMore}`. Add `before=<sequence>` to load
older messages or `after=<sequence>` to poll newer messages, never both. Cursors
are exclusive; before must be at least 1, after at least 0. Each response is
ascending. With after, continue from the last returned sequence while hasMore is
true. With before, continue from the first returned sequence. Without a cursor,
hasMore indicates earlier history. Sequence allocation serializes commits per
conversation; timestamps are not pagination cursors.

A message includes id, sequence, body, senderName, senderRole, mine and UTC
createdAt. Sender identity/role cannot be supplied by clients. Bodies are trimmed,
nonblank plain text, at most 2000 UTF-16 code units, with no NUL characters. Unknown
fields, invalid UUID keys/cursors and malformed text return 400. An exact retry
with the same conversation/sender/key returns the original message; changed text
with that key returns 409. Different senders may reuse a key independently.

First open atomically snapshots the booking customer and current provider OWNERs.
All reads/sends require this membership plus the current matching capability and
booking ownership. MEMBER, outsider and admin-only callers cannot access private
chat. Revoked owners lose access; later owners do not inherit history. Missing or
inaccessible conversations return 404, anonymous requests 401. Conversations can
be opened/read after completion or cancellation. There are no edit/delete,
attachment, pre-booking chat or participant-management endpoints in this slice.

V7 only adds conversation tables/indexes; existing bookings remain unchanged.
Back up before upgrading a non-disposable database. An application rollback can
leave these additive tables in place; do not drop them once messages exist.

## Completed-booking reviews

`GET /bookings/{id}/reviews` requires booking access and returns
`{eligible,review}`. Only its customer with current customer capability is eligible,
and only when status is COMPLETED and no review exists. The assigned provider owner
can read the existing review but is never eligible to submit for that customer.

`POST /bookings/{id}/reviews` accepts `{"rating":5,"comment":"Great work"}`.
Rating is a whole number 1–5. Comment is optional (null/absent becomes empty), trimmed
plain text, at most 2000 UTF-16 code units, with no NUL. Unknown fields return 400.
The server derives the customer and provider from the booking. An initial submission
returns 201; an identical normalized submission returns 200 with the original review.
No retry key is required: the unique booking is the idempotency scope. A changed
duplicate or non-COMPLETED booking returns 409. Missing/unowned bookings and callers
without customer capability return 404; anonymous calls return 401.

`GET /providers/{id}/reviews?page=0&size=20` is public. It returns
`{items,page,size,total,averageRating}` sorted by creation time descending then ID.
Page is 0–100000 and size 1–100. Rating average is a decimal string rounded half-up
to two places over **all** provider reviews, not only the page; no reviews means
`total:0,averageRating:null`. A missing provider returns 404. Public items contain
only `id,rating,comment,createdAt`; no booking/customer identity is exposed. These
are reviews of completed services, not identity-verification endorsements.

Reviews cannot be edited or deleted through this API. V8 adds only the review table
and index, preserving existing bookings/chat. Keep these tables on application
rollback; do not discard submitted reviews as a rollback shortcut.

## Disputes and manual decisions (#10)

All routes below are under `/api/v1` and require bearer authentication. Reads
are limited to the booking customer with customer capability, a current assigned
provider OWNER with provider capability, or an admin. Other users receive 404.
Unlike chat, current provider owners inherit case access. Admin case access does
not bypass booking/chat access. Lists accept `page=0..100000,size=1..100`.

| Route | Behavior |
| --- | --- |
| `GET /bookings/{id}/disputes` | Booking-scoped `{eligible,filingDeadline,dispute}`; existing case or null |
| `POST /bookings/{id}/disputes` | Customer `{reason}`; 201 new case, 200 exact normalized retry, 409 changed/existing or ineligible |
| `GET /disputes` | Scoped `{items,page,size,total}`, newest cases first |
| `GET /disputes/{id}` | Private case, permissions, saved deadlines, amount and optional decision |
| `GET /disputes/{id}/evidence` | Oldest-first paged `{id,role,body,createdAt,mine}` |
| `POST /disputes/{id}/evidence` | Participant `{body}` with UUID `Idempotency-Key`; 200 new or identical retry |
| `GET /disputes/{id}/history` | Paged `{status,occurredAt,system}` oldest first |
| `GET /disputes/{id}/execution` | Instance ID, status and checkpoint presence; no stack trace |
| `POST /disputes/{id}/decision` | Independent admin `{outcome,rationale}` with UUID `Idempotency-Key`; 202 admitted/replayed |

Text is required, stripped, nonblank, NUL-free and at most 2000 UTF-16 code units.
Unknown JSON properties and malformed/missing UUID keys are rejected with 400.
Evidence keys are bound to case, actor and normalized body; decision keys bind
admin, case, outcome and rationale. Changed retries return 409. The case itself
is the open-request idempotency boundary (one case per booking). Flutter retains
uncertain submissions for an exact retry for the current authenticated session.

Only WAIT_CUSTOMER or COMPLETED bookings can be disputed, within seven days of
the latest WAIT_CUSTOMER history entry. A pending booking action conflicts (409).
Defaults are `bliss.dispute.filing-seconds=604800` and
`bliss.dispute.evidence-seconds=86400`. The saved evidence deadline is fixed at
opening. Initial reason counts as customer evidence; both parties submitting or
deadline expiry advances COLLECTING_EVIDENCE to UNDER_REVIEW and closes evidence.
There is no default winner and no automatic payment on timeout.

Admins cannot add evidence solely by having admin capability. A deciding admin
must not be the booking customer or any member of the provider (including MEMBER).
`outcome` is CUSTOMER_FAVORED or PROVIDER_FAVORED. Only one decision is accepted
in UNDER_REVIEW. The response contains `outcome,rationale,admittedAt,processed`;
202 with `processed:false` is not settlement success. Poll case state/history.
Resolution atomically refunds/voids for the customer or captures/retains capture
for the provider, using only the fake adapter. Exact decision retries remain 202
with their current processed value after resolution; competing decisions return 409.

Exhausted transient retries leave the payment unchanged and the decision unprocessed.
The execution API reports FAULTED from a durable marker even though Flow deletes
the failed checkpoint. New writes stop; support/operator recovery is not yet a
self-service feature. Evidence, rationale and history have no edit/delete/appeal
API. Existing published reviews remain visible if a later dispute refunds the
booking; a new review still requires COMPLETED. V9 rollback constraints are in
[the domain model](../architecture/DOMAIN_MODEL.md).
