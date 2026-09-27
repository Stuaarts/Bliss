# Durable action catalog (API v1)

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

## Dispute admissions and effects

These are durable PostgreSQL records, not broker events. Correlation uses the
dispute UUID and its booking UUID; `dispute.workflow_instance_id` identifies the
canonical engine instance. Evidence and rationale are private application data
and do not appear in workflow input or audit metadata.

| Admission / transition | Actor and durable record | Effect |
| --- | --- | --- |
| Open dispute | Eligible booking customer; case, initial evidence and start intent | Booking becomes DISPUTED; ordinary booking flow relinquishes settlement |
| Add evidence | Current customer/provider OWNER; case/actor/UUID-keyed evidence | Exact retry returns original; collection closes after both parties respond or the saved deadline |
| Begin manual review | DisputeFlow; history and audit | UNDER_REVIEW, no automatic payment or default winner |
| Admit decision | Independent admin; one immutable decision per case | 202 response with processed=false until settlement commits |
| Apply customer-favored decision | DisputeFlow; payment/history/audit and processed timestamp | REFUND captured payment or VOID authorization; case RESOLVED_CUSTOMER |
| Apply provider-favored decision | DisputeFlow; same atomic transaction | CAPTURE authorization or retain capture; case RESOLVED_PROVIDER |
| Exhaust retries | Workflow failure listener; case failure timestamp | FAULTED execution signal; unresolved decision and unchanged payment require operator attention |

Opening is idempotent on the booking and normalized reason. Evidence keys bind
case/actor/body; decision keys bind case/admin/outcome/rationale. Changed duplicate
payloads return 409. Exact retries remain readable after collection/resolution;
they never create another ledger effect. An admin who is the customer or any
member of the provider cannot decide. Current roles/ownership are rechecked on
requests. Filing/evidence deadlines and single-worker limits are documented in
[the workflow design](WORKFLOWS.md) and [API contracts](../api/API_GUIDE.md).
