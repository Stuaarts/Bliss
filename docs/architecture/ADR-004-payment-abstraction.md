# ADR-004: Fake payments behind an external boundary

Implemented in #5. PaymentGateway accepts AUTHORIZE, CAPTURE, VOID and REFUND using
the booking's exact price/currency snapshot. The portfolio adapter persists fake
transactions with unique booking/operation keys. Its writes share the booking
transaction; tests inject transient failures after ledger writes to verify rollback
and retry. Public refund/dispute actions remain #10. There is no runtime switch
to a real payment provider.

No real money, credentials or card data. Real marketplace settlement and compliance
are beta decisions (#14), not an implementation detail to infer.
An external adapter must add an outbox, provider idempotency and reconciliation:
external money movement cannot share the local database transaction.
