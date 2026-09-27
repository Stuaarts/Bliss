# Database invariants and upgrades

The V2 schema is created exclusively by Flyway migrations in the API; it does not
import the legacy database. Hibernate validates the schema on application startup.
Released migration files stay immutable; V10 adds constraints to V1–V9 with no
data rewrite, table replacement or destructive reset.

## Enforcement and evidence

| Boundary | Database enforcement | Verification |
| --- | --- | --- |
| Identity and aggregate keys | UUID primary/foreign keys; unique OIDC subject | ORM startup validation, identity tests, direct foreign-key rejection |
| Money | numeric(12,2), nonnegative, finite; optional request budget remains nullable | Direct SQL rejects negative/NaN at all five money columns; exact 85.50 survives upgrade |
| Currency | Three uppercase letters, including payment | API validation and direct SQL rejection; this format check is not an ISO currency registry |
| Time | timestamptz for domain timestamps | Catalog metadata check and UTC microsecond round-trip after changing session timezone; Flyway's own installed_on metadata is excluded |
| Booking source | Exactly quote/request or offering/version/key; unique accepted request and direct retry key | Populated V5 migration plus concurrent quote/direct-booking admission tests |
| Payment ledger | One payment per booking, fake-only flag and unique booking/operation | Direct constraint rejection, payment rollback/retry and real restart settlement tests |
| Conversations | Unique booking; composite sender-membership FK; positive unique sequence and sender retry key | Concurrent open/send tests, foreign-key/sequence/blank-body rejection, populated upgrade preservation |
| Reviews | One per booking, 1–5 rating | Concurrent submission/privacy/eligibility tests and direct duplicate/rating rejection |
| Disputes | One per booking; actor retry key; one decision; known history states; consistent resolution timestamp; deadline not before creation | Concurrent case/decision tests, direct invalid-write rejection, populated upgrade preservation and real restart tests |
| Optimistic edits | Version columns | Profile/offering/request stale-version tests |

`DatabaseInvariantsIT` installs an empty latest schema, upgrades populated V8 to V9
and populated V9 to latest, and compares complete row snapshots across 19 tables.
It includes booking/payment/ledger/history, chat/membership/messages, reviews,
disputes/evidence/decision/start intent and audit data. After rejected writes, it
compares the snapshots again to verify the existing records were not changed.
`DirectBookingMigrationIT` separately preserves a populated V5 quote booking.
Packaged API restart tests validate ORM mapping and actual workflow restoration.

V10 explicitly rejects PostgreSQL numeric NaN because an ordinary `>= 0` check
accepts it. A regression inserts NaN into a V9 fixture, verifies migration failure
rolls back every V10 change without repairing data, then corrects only the fixture
and verifies successful migration. The application APIs already parse money as
BigDecimal; the database constraint protects against direct writes/import mistakes.

## Upgrade preflight and recovery

Back up the target database before deploying schema changes. Run the checks below
on the target database as an operator before applying V10. Each must return zero.
They expose counts only, without private text. Investigate any invalid rows and
their source; never silently replace a monetary value or fabricate case history.

```sql
SELECT 'offering' AS source, count(*) FROM service_offering WHERE price='NaN'::numeric
UNION ALL SELECT 'request', count(*) FROM service_request WHERE budget='NaN'::numeric
UNION ALL SELECT 'quote', count(*) FROM quote WHERE price='NaN'::numeric
UNION ALL SELECT 'booking', count(*) FROM booking WHERE price='NaN'::numeric
UNION ALL SELECT 'payment', count(*) FROM payment
 WHERE amount='NaN'::numeric OR currency !~ '^[A-Z]{3}$';

SELECT count(*) FROM booking_status_history WHERE status NOT IN
 ('CREATED','WAIT_PROVIDER','SCHEDULED','IN_PROGRESS','WAIT_CUSTOMER',
  'COMPLETED','CANCELLED','EXPIRED','DISPUTED','REFUNDED');
SELECT count(*) FROM dispute_history WHERE status NOT IN
 ('COLLECTING_EVIDENCE','UNDER_REVIEW','RESOLVED_CUSTOMER','RESOLVED_PROVIDER');
SELECT count(*) FROM dispute WHERE evidence_deadline < created_at
 OR (status IN ('COLLECTING_EVIDENCE','UNDER_REVIEW') AND resolved_at IS NOT NULL)
 OR (status IN ('RESOLVED_CUSTOMER','RESOLVED_PROVIDER')
     AND (resolved_at IS NULL OR resolved_at < created_at));
```

If validation fails, PostgreSQL transactional DDL rolls back V10 and startup fails
closed. Keep the existing database, inspect the failure, reconcile any invalid data
with an auditable operator decision, and rerun the migration. Do not use Flyway
repair to hide a constraint violation or edit checksums. V10 only adds constraints;
an older binary should normally keep them. The separate V9 DISPUTED/REFUNDED
compatibility limit still applies, so rollback requires a forward compatibility
plan while those records or workflow instances exist.

## Application-only rules

The database enforces structural constraints, not a user's current JWT capability,
independent-admin policy, legal transition ordering, review eligibility or immutable
snapshot relationships across aggregates. These remain application transactions
with explicit authorization and locks, verified by negative/concurrent API tests.
PostgreSQL checks do not validate that an entire historical timeline is authentic.
Multi-worker workflow ownership and a live payment provider would need additional
coordination/outbox/reconciliation; this remains a single-worker simulated portfolio.
