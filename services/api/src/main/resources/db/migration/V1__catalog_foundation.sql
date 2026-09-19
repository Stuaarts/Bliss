CREATE TABLE bliss_user (
    id uuid PRIMARY KEY,
    oidc_subject varchar(255) NOT NULL UNIQUE,
    display_name varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_role (
    user_id uuid NOT NULL REFERENCES bliss_user(id),
    role varchar(20) NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE provider_profile (
    id uuid PRIMARY KEY,
    display_name varchar(120) NOT NULL,
    provider_type varchar(20) NOT NULL CHECK (provider_type IN ('INDIVIDUAL', 'COMPANY')),
    description varchar(2000) NOT NULL,
    city varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE provider_member (
    provider_id uuid NOT NULL REFERENCES provider_profile(id),
    user_id uuid NOT NULL REFERENCES bliss_user(id),
    member_role varchar(20) NOT NULL CHECK (member_role IN ('OWNER', 'MEMBER')),
    PRIMARY KEY (provider_id, user_id)
);
CREATE INDEX idx_provider_member_user ON provider_member(user_id);

CREATE TABLE service_category (
    id uuid PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE,
    name varchar(120) NOT NULL
);

CREATE TABLE service_offering (
    id uuid PRIMARY KEY,
    provider_id uuid NOT NULL REFERENCES provider_profile(id),
    category_id uuid NOT NULL REFERENCES service_category(id),
    title varchar(160) NOT NULL,
    description varchar(2000) NOT NULL,
    price numeric(12,2) NOT NULL CHECK (price >= 0),
    currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_offering_provider ON service_offering(provider_id);
CREATE INDEX idx_offering_category_active ON service_offering(category_id, active, title, id);
CREATE INDEX idx_offering_active_title ON service_offering(active, title, id);
