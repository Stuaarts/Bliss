-- PostgreSQL numeric accepts NaN, and NaN passes ordinary nonnegative checks.
-- Reject it explicitly at every money boundary; never silently repair old values.
ALTER TABLE service_offering ADD CONSTRAINT ck_offering_price_finite CHECK (price <> 'NaN'::numeric);
ALTER TABLE service_request ADD CONSTRAINT ck_request_budget_finite CHECK (budget <> 'NaN'::numeric);
ALTER TABLE quote ADD CONSTRAINT ck_quote_price_finite CHECK (price <> 'NaN'::numeric);
ALTER TABLE booking ADD CONSTRAINT ck_booking_price_finite CHECK (price <> 'NaN'::numeric);
ALTER TABLE payment ADD CONSTRAINT ck_payment_amount_finite CHECK (amount <> 'NaN'::numeric);
ALTER TABLE payment ADD CONSTRAINT ck_payment_currency CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE booking_status_history ADD CONSTRAINT ck_booking_history_status CHECK (status IN ('CREATED','WAIT_PROVIDER','SCHEDULED','IN_PROGRESS','WAIT_CUSTOMER','COMPLETED','CANCELLED','EXPIRED','DISPUTED','REFUNDED'));
ALTER TABLE dispute_history ADD CONSTRAINT ck_dispute_history_status CHECK (status IN ('COLLECTING_EVIDENCE','UNDER_REVIEW','RESOLVED_CUSTOMER','RESOLVED_PROVIDER'));
ALTER TABLE dispute ADD CONSTRAINT ck_dispute_deadline CHECK (evidence_deadline >= created_at);
ALTER TABLE dispute ADD CONSTRAINT ck_dispute_resolution CHECK (
    (status IN ('COLLECTING_EVIDENCE','UNDER_REVIEW') AND resolved_at IS NULL)
    OR (status IN ('RESOLVED_CUSTOMER','RESOLVED_PROVIDER') AND resolved_at IS NOT NULL AND resolved_at >= created_at)
);
