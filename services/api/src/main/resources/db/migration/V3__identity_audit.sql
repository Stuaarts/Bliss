CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES bliss_user(id),
    action VARCHAR(80) NOT NULL,
    resource_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_actor_time ON audit_event(actor_user_id, occurred_at);
