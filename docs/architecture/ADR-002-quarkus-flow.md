# ADR-002: Quarkus Flow for durable orchestration

Accepted direction; business workflow implementation pending #5/#10. Use Flow for
human waits, timeout, retry and compensation. CRUD stays in application services.
Pin released 1.2.0.CR1 against Quarkus core 3.39.0, matching the inspected local
checkout. This avoids coupling to its 2.0.0-SNAPSHOT. The RC is a documented risk.

JPA artifact is `io.quarkiverse.flow:quarkus-flow-jpa`. Add it with schema verified
against released entities when implementing persistence. Prove restoration with
actual PostgreSQL and process restart; do not rely on Dev UI visibility alone.
