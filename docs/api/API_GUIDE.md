# API guide

Base URL: `http://localhost:8080`. Version prefix: `/api/v1`.

| Method | Path | Current behavior |
|---|---|---|
| GET | /categories | Public reference categories |
| GET | /offerings | Active offerings, optional categoryId UUID; page 0–100000, size 1–100 (default 20) |
| GET | /offerings/{id} | Active offering or 404 |
| GET | /providers/{id} | Public profile or 404 |

Lists of offerings return `{items, page, size, total}`. UUID identifiers; exact
decimal `price` strings; explicit currency. Inactive offerings are not discoverable.
All reads use DTOs. Unexpected server errors return a generic problem response;
`X-Request-ID` identifies the request in diagnosis. Bean Validation rejects invalid
pagination; malformed typed parameters are rejected by the REST layer.

`/q/openapi`, `/q/swagger-ui`, `/q/health/ready`, `/q/metrics` expose local developer
and operational information. There are no booking/login/write endpoints yet.

Future state transitions use explicit actions, never a generic client-selected
status PATCH. Authenticated resources will derive identity from the validated token.
