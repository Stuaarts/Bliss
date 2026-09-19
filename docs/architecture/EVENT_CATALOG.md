# Event catalog

No domain events are emitted by the read-only foundation. These are proposed
versioned contracts to finalize with booking implementation, not implemented APIs.

| Proposed type | Producer | Consumer | Correlation |
|---|---|---|---|
| com.bliss.booking.created.v1 | Quote/direct-booking transaction | BookingLifecycleFlow | booking UUID |
| com.bliss.booking.provider-decided.v1 | Authorized provider action | BookingLifecycleFlow | booking UUID |
| com.bliss.booking.started.v1 | Assigned provider | BookingLifecycleFlow | booking UUID |
| com.bliss.booking.work-completed.v1 | Assigned provider | BookingLifecycleFlow | booking UUID |
| com.bliss.booking.customer-decided.v1 | Owning customer | BookingLifecycleFlow | booking UUID |
| com.bliss.dispute.resolved.v1 | Authorized admin | DisputeFlow | dispute UUID |

Envelope: event UUID, versioned type, source, aggregate subject, UTC timestamp,
correlation ID, causation ID and minimal non-sensitive payload. Deduplicate by event
ID and make domain transitions independently idempotent. Specify transactional
publication/recovery before claiming reliable delivery. No Kafka requirement yet.
