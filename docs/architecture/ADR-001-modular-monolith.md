# ADR-001: Modular monolith

Accepted. One Quarkus backend, one PostgreSQL database, feature-oriented packages.
The domain benefits from transactional quote/booking/payment state without remote
coordination overhead. Module boundaries follow business capability. Separate a
service only after measured operational or ownership needs justify it.
