# ADR-004: Fake payments behind an external boundary

Implemented in #5. PaymentGateway accepts AUTHORIZE, CAPTURE, VOID and REFUND using
the booking's exact price/currency snapshot. The portfolio adapter persists fake
transactions with unique booking/operation keys. Its writes share the booking
transaction; tests inject transient failures after ledger writes to verify rollback
and retry. DisputeFlow (#10) settles an independent admin's recorded decision:
customer-favored captured payments are refunded, authorizations are voided, and
provider-favored authorizations are captured. Existing captures are retained.
There is no runtime switch
to a real payment provider.

No real money, credentials or card data. Real marketplace settlement and compliance
are beta decisions (#14), not an implementation detail to infer.
An external adapter must add an outbox, provider idempotency and reconciliation:
external money movement cannot share the local database transaction.
