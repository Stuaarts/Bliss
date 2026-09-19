# ADR-004: Fake payments behind an external boundary

Accepted direction; implementation pending #5. PaymentGateway exposes authorize,
capture, cancelAuthorization and refund using exact decimals and explicit currency.
The portfolio uses deterministic persisted fake transactions with idempotency keys
and configurable transient failures. Store outcomes and reconcile workflow retries.

No real money, credentials or card data. Real marketplace settlement and compliance
are beta decisions (#14), not an implementation detail to infer.
