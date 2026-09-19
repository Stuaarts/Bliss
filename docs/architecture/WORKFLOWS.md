# Workflow design — planned

No business workflow is registered in the foundation. Catalog reads are ordinary
application-service calls. Flow 1.2.0.CR1 is a verified-resolvable dependency.

BookingLifecycleFlow (#5): booking created → authorize fake payment → wait for
provider → accepted/scheduled → start → provider complete → wait for customer →
confirmed/capture/completed or disputed/DisputeFlow. Rejection and timeout void
authorization and finish as cancelled/expired. Cancellation is policy-driven.

Requirements: stable booking correlation UUID; stored engine instance ID; JPA
checkpoints; history updates synchronized with aggregate transitions; persistent
idempotency; bounded transient retry; compensation; auditable timeouts. Restart
tests must prove a waiting instance restores and a duplicate event cannot repeat
settlement. A successful hello-world workflow is not evidence for those guarantees.

DisputeFlow (#10): wait for evidence → admin review → customer-favored refund/void
or provider-favored capture/release → resolved. No automated legal adjudication.

Source map and exact upstream revision: ../agent/RECONNAISSANCE.md.
