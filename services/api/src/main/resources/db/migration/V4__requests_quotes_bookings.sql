CREATE TABLE service_request (
    id uuid PRIMARY KEY,
    customer_id uuid NOT NULL REFERENCES bliss_user(id),
    category_id uuid NOT NULL REFERENCES service_category(id),
    title varchar(160) NOT NULL,
    description varchar(2000) NOT NULL,
    city varchar(120) NOT NULL,
    budget numeric(12,2) CHECK (budget >= 0),
    currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status varchar(20) NOT NULL CHECK (status IN ('OPEN','ACCEPTED','CANCELLED')),
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_request_customer ON service_request(customer_id, created_at, id);
CREATE INDEX idx_request_opportunity ON service_request(status, category_id, created_at, id);

CREATE TABLE quote (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES service_request(id),
    provider_id uuid NOT NULL REFERENCES provider_profile(id),
    description varchar(2000) NOT NULL,
    price numeric(12,2) NOT NULL CHECK (price >= 0),
    currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status varchar(20) NOT NULL CHECK (status IN ('SUBMITTED','ACCEPTED','DECLINED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE(request_id, provider_id)
);
CREATE INDEX idx_quote_provider ON quote(provider_id, created_at, id);
CREATE UNIQUE INDEX uq_quote_accepted_request ON quote(request_id) WHERE status='ACCEPTED';

CREATE TABLE booking (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL UNIQUE REFERENCES service_request(id),
    quote_id uuid NOT NULL UNIQUE REFERENCES quote(id),
    customer_id uuid NOT NULL REFERENCES bliss_user(id),
    provider_id uuid NOT NULL REFERENCES provider_profile(id),
    title varchar(160) NOT NULL,
    city varchar(120) NOT NULL,
    price numeric(12,2) NOT NULL CHECK (price >= 0),
    currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status varchar(40) NOT NULL,
    workflow_instance_id varchar(255),
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_booking_customer ON booking(customer_id, created_at, id);
CREATE INDEX idx_booking_provider ON booking(provider_id, created_at, id);
CREATE TABLE booking_status_history (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES booking(id),
    status varchar(40) NOT NULL,
    actor_user_id uuid REFERENCES bliss_user(id),
    occurred_at timestamptz NOT NULL
);
CREATE INDEX idx_booking_history ON booking_status_history(booking_id, occurred_at, id);
