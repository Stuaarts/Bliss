ALTER TABLE booking ALTER COLUMN request_id DROP NOT NULL;
ALTER TABLE booking ALTER COLUMN quote_id DROP NOT NULL;
ALTER TABLE booking ADD COLUMN offering_id uuid REFERENCES service_offering(id);
ALTER TABLE booking ADD COLUMN offering_version bigint;
ALTER TABLE booking ADD COLUMN direct_key uuid;
ALTER TABLE booking ADD COLUMN description varchar(2000);
UPDATE booking b SET description=q.description FROM quote q WHERE b.quote_id=q.id;
ALTER TABLE booking ALTER COLUMN description SET NOT NULL;
ALTER TABLE booking ADD CONSTRAINT ck_booking_source CHECK (
    (request_id IS NOT NULL AND quote_id IS NOT NULL AND offering_id IS NULL AND offering_version IS NULL AND direct_key IS NULL)
    OR
    (request_id IS NULL AND quote_id IS NULL AND offering_id IS NOT NULL AND offering_version IS NOT NULL AND offering_version>=0 AND direct_key IS NOT NULL)
);
CREATE UNIQUE INDEX uq_booking_direct_key ON booking(customer_id,direct_key) WHERE direct_key IS NOT NULL;
CREATE INDEX idx_booking_offering ON booking(offering_id) WHERE offering_id IS NOT NULL;
