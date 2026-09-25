CREATE TABLE conversation (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL UNIQUE REFERENCES booking(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    last_sequence bigint NOT NULL DEFAULT 0 CHECK (last_sequence >= 0)
);
CREATE TABLE conversation_participant (
    conversation_id uuid NOT NULL REFERENCES conversation(id),
    user_id uuid NOT NULL REFERENCES bliss_user(id),
    participant_role varchar(20) NOT NULL CHECK (participant_role IN ('CUSTOMER','PROVIDER')),
    PRIMARY KEY (conversation_id,user_id)
);
CREATE INDEX idx_conversation_participant_user ON conversation_participant(user_id,conversation_id);
CREATE TABLE conversation_message (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversation(id),
    sender_id uuid NOT NULL,
    client_key uuid NOT NULL,
    sequence bigint NOT NULL CHECK (sequence > 0),
    body varchar(2000) NOT NULL CHECK (length(btrim(body)) > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (conversation_id,sender_id) REFERENCES conversation_participant(conversation_id,user_id),
    UNIQUE (conversation_id,sender_id,client_key),
    UNIQUE (conversation_id,sequence)
);
