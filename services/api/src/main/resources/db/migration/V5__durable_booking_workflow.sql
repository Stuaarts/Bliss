CREATE TABLE cloud_event_entity
(
    id                VARCHAR(255) NOT NULL,
    reg_id            VARCHAR(255) NOT NULL,
    source            VARCHAR(255) NOT NULL,
    type              VARCHAR(255) NOT NULL,
    subject           VARCHAR(255),
    data_content_type VARCHAR(255),
    data_schema       VARCHAR(255),
    time              TIMESTAMP(6) WITH TIME ZONE,
    data              BYTEA,
    extensions        BYTEA,
    processed_flag    BOOLEAN DEFAULT FALSE,
    version           SMALLINT     NOT NULL CHECK (version BETWEEN 0 AND 1),
    PRIMARY KEY (id)
);

CREATE TABLE workflow_instance_entity
(
    application_id     VARCHAR(255)                NOT NULL,
    instance_id        VARCHAR(255)                NOT NULL,
    workflow_name      VARCHAR(255)                NOT NULL,
    workflow_namespace VARCHAR(255)                NOT NULL,
    workflow_version   VARCHAR(255)                NOT NULL,
    started_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status             SMALLINT CHECK (status BETWEEN 0 AND 6),
    input              BYTEA,
    PRIMARY KEY (application_id, instance_id)
);

CREATE TABLE task_info_entity
(
    application_id      VARCHAR(255) NOT NULL,
    workflow_instance_id VARCHAR(255) NOT NULL,
    json_pointer        VARCHAR(255) NOT NULL,
    iteration           INTEGER      NOT NULL,
    task_type           INTEGER      NOT NULL CHECK (task_type IN (1, 2)),
    is_end_node         BOOLEAN,
    retry_attempt       INTEGER,
    instant             TIMESTAMP(6) WITH TIME ZONE,
    next_position       VARCHAR(255),
    context             BYTEA,
    model               BYTEA,
    PRIMARY KEY (iteration, application_id, json_pointer, workflow_instance_id),
    CHECK (task_type <> 1 OR (is_end_node IS NOT NULL)),
    CHECK (task_type <> 2 OR (retry_attempt IS NOT NULL)),
    CONSTRAINT fk_task_workflow_instance
        FOREIGN KEY (application_id, workflow_instance_id)
            REFERENCES workflow_instance_entity (application_id, instance_id)
);
ALTER TABLE booking ADD COLUMN scheduled_at timestamptz;
ALTER TABLE booking ADD COLUMN decision_deadline timestamptz;
ALTER TABLE booking ADD CONSTRAINT ck_booking_status CHECK (status IN ('CREATED','WAIT_PROVIDER','SCHEDULED','IN_PROGRESS','WAIT_CUSTOMER','COMPLETED','CANCELLED','EXPIRED'));
CREATE UNIQUE INDEX uq_booking_workflow ON booking(workflow_instance_id) WHERE workflow_instance_id IS NOT NULL;
ALTER TABLE audit_event ALTER COLUMN actor_user_id DROP NOT NULL;
CREATE TABLE booking_workflow_start (
    booking_id uuid PRIMARY KEY REFERENCES booking(id),
    requested_at timestamptz NOT NULL DEFAULT now(),
    claimed_at timestamptz,
    started boolean NOT NULL DEFAULT false
);
INSERT INTO booking_workflow_start(booking_id) SELECT id FROM booking WHERE status='CREATED';
CREATE TABLE booking_event (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES booking(id),
    actor_user_id uuid NOT NULL REFERENCES bliss_user(id),
    event_type varchar(40) NOT NULL CHECK (event_type IN ('PROVIDER_ACCEPT','PROVIDER_REJECT','START','MARK_COMPLETE','CUSTOMER_CONFIRM','CANCEL')),
    scheduled_at timestamptz,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    UNIQUE(booking_id, actor_user_id, id)
);
CREATE UNIQUE INDEX uq_booking_pending_event ON booking_event(booking_id) WHERE processed_at IS NULL;
CREATE TABLE payment (
    booking_id uuid PRIMARY KEY REFERENCES booking(id),
    amount numeric(12,2) NOT NULL CHECK (amount>=0),
    currency varchar(3) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('AUTHORIZED','CAPTURED','VOIDED','REFUNDED')),
    simulated boolean NOT NULL DEFAULT true CHECK (simulated)
);
CREATE TABLE payment_transaction (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES payment(booking_id),
    operation varchar(20) NOT NULL CHECK (operation IN ('AUTHORIZE','CAPTURE','VOID','REFUND')),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(booking_id, operation)
);
