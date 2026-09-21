# Booking action catalog (API v1)

Implemented delivery is a transactional PostgreSQL start queue and action inbox.
No Kafka or CloudEvent publication is claimed. Correlation is the booking UUID;
`booking.workflow_instance_id` identifies the canonical durable engine instance.

| Inbox type | Authorized actor | Result |
|---|---|---|
| PROVIDER_ACCEPT | Assigned provider OWNER | WAIT_PROVIDER → SCHEDULED; scheduledAt required |
| PROVIDER_REJECT | Assigned provider OWNER | WAIT_PROVIDER → CANCELLED; void authorization |
| START | Assigned provider OWNER | SCHEDULED → IN_PROGRESS, after schedule |
| MARK_COMPLETE | Assigned provider OWNER | IN_PROGRESS → WAIT_CUSTOMER |
| CUSTOMER_CONFIRM | Booking customer | WAIT_CUSTOMER → COMPLETED; capture |
| CANCEL | Booking customer | CREATED/WAIT_PROVIDER/SCHEDULED → CANCELLED; void if authorized |

Each action stores the `Idempotency-Key` UUID, booking UUID, authenticated actor,
type, optional scheduledAt, UTC admission time and processing time. Exact retries
return the original receipt, including after completion. Reusing a key with a
different actor, booking, type or payload returns 409. Only one unprocessed action
per booking is admitted; a competing new action returns 409 and must be reconsidered
after reading the updated booking. Events and the resulting history are durable.
Read the [workflow design](WORKFLOWS.md) for timeout and transaction semantics.

If broker publication is introduced later, versioned CloudEvent types will map
these actions to `com.bliss.booking.<action>.v1`, with event ID, source, booking
subject, time and causation metadata. That change requires a transactional outbox,
retention and delivery recovery; it is not part of the current adapter.
