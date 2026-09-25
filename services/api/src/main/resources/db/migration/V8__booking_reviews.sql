CREATE TABLE booking_review (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL UNIQUE REFERENCES booking(id),
    rating smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment varchar(2000) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_booking_review_created ON booking_review(created_at DESC,id);
