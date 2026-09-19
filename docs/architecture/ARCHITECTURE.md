# Architecture

A modular monolith is the V2 deployment unit. Current capabilities: catalog,
provider (public read), common API errors. REST DTOs do not expose ORM entities.
The Flutter application uses a configurable HTTP boundary and typed responses.

```mermaid
flowchart LR
  Flutter --> REST[Quarkus REST /api/v1]
  REST --> Catalog[Catalog application service]
  Catalog --> ORM[Hibernate / PostgreSQL]
  Flyway --> ORM
```

Future authenticated application services own domain transactions. Quarkus Flow
coordinates long-running transitions; it never replaces queryable booking records.
OIDC validates tokens and membership checks enforce resource ownership. External
payment effects sit behind a PaymentGateway; the portfolio adapter is fake.

No broker or cache is needed for the first slice. Adopt transactional event
publication only with a demonstrated reliability requirement and tests.

Quarkus core 3.39.0 / Flow 1.2.0.CR1 are pinned to the locally inspected reference.
Upstream source remains external and excluded. JPA workflow schema and restoration
must be validated when implemented; the current catalog schema does not claim that.
