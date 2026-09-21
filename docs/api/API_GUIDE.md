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

Lists of offerings return `{items, page, size, total}`. UUID identifiers; exact
decimal `price` strings; explicit currency. Inactive offerings are not discoverable.
All reads use DTOs. Unexpected server errors return a generic problem response;
`X-Request-ID` identifies the request in diagnosis. Bean Validation rejects invalid
pagination; malformed typed parameters are rejected by the REST layer.

`/q/openapi`, `/q/swagger-ui`, `/q/health/ready`, `/q/metrics` expose local developer
and operational information. Sign-in is hosted by Keycloak, not an API password
endpoint. Protected calls need `Authorization: Bearer <access token>`. Unknown
JSON properties are rejected. Provider version conflicts return 409; wrong role
returns 403; non-owned providers return 404. Booking endpoints are not implemented.

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

Future state transitions use explicit actions, never a generic client-selected
status PATCH. Authenticated resources derive identity from the validated token.
