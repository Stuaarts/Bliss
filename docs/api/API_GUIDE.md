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
| PUT | /providers/me/{id} | Provider role + OWNER membership; displayName, description, city, version |

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

Future state transitions use explicit actions, never a generic client-selected
status PATCH. Authenticated resources derive identity from the validated token.
