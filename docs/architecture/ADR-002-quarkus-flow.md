# ADR-002: Quarkus Flow for durable orchestration

Implemented for BookingLifecycleFlow; DisputeFlow remains #10. Use Flow for
human waits, timeout, retry and compensation. CRUD stays in application services.
Pin released 1.2.0.CR1 against Quarkus core 3.39.0, matching the inspected local
checkout. This avoids coupling to its 2.0.0-SNAPSHOT. The RC is a documented risk.

JPA artifact is `io.quarkiverse.flow:quarkus-flow-jpa`. V5 uses the released schema,
validated by Hibernate. Packaged-process tests prove restoration with PostgreSQL.
The [workflow design](WORKFLOWS.md) documents the local inbox adapter, single-worker
limit and two release-specific JPA fixes kept inside Bliss. #22 tracks retiring
that compatibility adapter after upstream verification.
