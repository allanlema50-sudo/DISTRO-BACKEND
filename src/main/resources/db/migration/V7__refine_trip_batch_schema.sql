-- V2 creates the trip-stop model. This migration adds the products/tracking
-- invariants without recreating columns or tables already owned by V2.
ALTER TABLE trip_stops
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE trip_stops
    ADD CONSTRAINT ck_trip_stops_sequence_positive CHECK (sequence_num > 0) NOT VALID;

-- NOT VALID preserves upgrade availability for legacy rows. New writes are
-- checked immediately; validate this constraint after reviewing and repairing
-- any existing non-positive sequence_num values.

CREATE INDEX IF NOT EXISTS idx_trip_stops_stock_item_id
    ON trip_stops(stock_item_id);
