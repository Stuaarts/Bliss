# Architecture

A modular monolith is the V2 deployment unit. Current capabilities: catalog,
OIDC identity, provider management, requests/quotes, durable bookings and simulated
payments, persisted conversations, public reviews and private disputes with manual
admin resolution. REST DTOs do not expose ORM entities.
The Flutter application uses a configurable HTTP boundary and typed responses.

```mermaid
flowchart LR
  Flutter --> REST[Quarkus REST /api/v1]
  REST --> Catalog[Catalog application service]
  REST --> Marketplace[Authenticated marketplace services]
  Catalog --> ORM[Hibernate / PostgreSQL]
  Marketplace --> ORM
  Marketplace --> Inbox[Durable start queue and action inbox]
  Inbox --> Flow[BookingLifecycleFlow / DisputeFlow]
  Flow --> Domain[Booking/dispute rules and fake payments]
  Marketplace --> Social[Conversation / review / evidence services]
  Social --> ORM
  Domain --> ORM
  Flow --> Checkpoints[JPA checkpoints]
  Checkpoints --> ORM
  Flyway --> ORM
```

Authenticated application services own domain transactions. Quarkus Flow
coordinates long-running transitions; it never replaces queryable booking records.
OIDC validates tokens and membership checks enforce resource ownership. External
payment effects sit behind a PaymentGateway; the portfolio adapter is fake.

No broker or cache is needed for the local portfolio. Adopt transactional event
publication only with a demonstrated reliability requirement and tests.

Quarkus core 3.39.0 / Flow 1.2.0.CR1 are pinned to the locally inspected reference.
Upstream source remains external and excluded. JPA workflow schema validation and
forced-process restoration are tested. The local deployment runs one API worker;
see [workflow durability and compatibility limits](WORKFLOWS.md).

BookingLifecycleFlow stops when a dispute takes settlement ownership. DisputeFlow
waits for evidence and a human admin decision; a timeout opens review without
choosing a winner. Domain rows support API reads even when no engine checkpoint
remains. Failed dispute execution retains a separate content-free marker.
See the [action catalog](EVENT_CATALOG.md), [database invariants](DATABASE_INVARIANTS.md)
and [security boundaries](SECURITY.md).
