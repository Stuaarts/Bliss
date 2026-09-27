ALTER TABLE booking DROP CONSTRAINT ck_booking_status;
ALTER TABLE booking ADD CONSTRAINT ck_booking_status CHECK (status IN ('CREATED','WAIT_PROVIDER','SCHEDULED','IN_PROGRESS','WAIT_CUSTOMER','COMPLETED','CANCELLED','EXPIRED','DISPUTED','REFUNDED'));
CREATE TABLE dispute (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL UNIQUE REFERENCES booking(id),
    opened_by uuid NOT NULL REFERENCES bliss_user(id),
    reason varchar(2000) NOT NULL CHECK (length(btrim(reason))>0),
    status varchar(40) NOT NULL CHECK (status IN ('COLLECTING_EVIDENCE','UNDER_REVIEW','RESOLVED_CUSTOMER','RESOLVED_PROVIDER')),
    created_at timestamptz NOT NULL,
    evidence_deadline timestamptz NOT NULL,
    resolved_at timestamptz,
    workflow_instance_id varchar(255) UNIQUE,
    workflow_failed_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_dispute_status ON dispute(status,created_at,id);
CREATE TABLE dispute_evidence (
    id uuid PRIMARY KEY,
    dispute_id uuid NOT NULL REFERENCES dispute(id),
    actor_user_id uuid NOT NULL REFERENCES bliss_user(id),
    actor_role varchar(20) NOT NULL CHECK (actor_role IN ('CUSTOMER','PROVIDER')),
    client_key uuid NOT NULL,
    body varchar(2000) NOT NULL CHECK (length(btrim(body))>0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(dispute_id,actor_user_id,client_key)
);
CREATE INDEX idx_dispute_evidence_page ON dispute_evidence(dispute_id,created_at,id);
CREATE TABLE dispute_decision (
    dispute_id uuid PRIMARY KEY REFERENCES dispute(id),
    actor_user_id uuid NOT NULL REFERENCES bliss_user(id),
    client_key uuid NOT NULL,
    outcome varchar(30) NOT NULL CHECK (outcome IN ('CUSTOMER_FAVORED','PROVIDER_FAVORED')),
    rationale varchar(2000) NOT NULL CHECK (length(btrim(rationale))>0),
    admitted_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz
);
CREATE TABLE dispute_history (
    id uuid PRIMARY KEY,
    dispute_id uuid NOT NULL REFERENCES dispute(id),
    status varchar(40) NOT NULL,
    actor_user_id uuid REFERENCES bliss_user(id),
    occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_dispute_history_page ON dispute_history(dispute_id,occurred_at,id);
CREATE TABLE dispute_workflow_start (
    dispute_id uuid PRIMARY KEY REFERENCES dispute(id),
    requested_at timestamptz NOT NULL DEFAULT now(),
    claimed_at timestamptz,
    started boolean NOT NULL DEFAULT false
);
