-- Expand phase for the tenant migration.
-- Existing rows remain valid because ownership columns are nullable until V3.

CREATE SEQUENCE IF NOT EXISTS order_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE stock_items ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE stock_items ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

ALTER TABLE trips ADD COLUMN IF NOT EXISTS trip_number VARCHAR(36);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS origin_name VARCHAR(255);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS origin_address VARCHAR(255);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS scheduled_start_label VARCHAR(100);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS total_distance_km INTEGER;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE trips
SET trip_number = 'TRIP-' || SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 24)
WHERE trip_number IS NULL;

UPDATE trips
SET status = CASE status
    WHEN 'UNASSIGNED' THEN 'SCHEDULED'
    WHEN 'ASSIGNED' THEN 'SCHEDULED'
    WHEN 'IN_PROGRESS' THEN 'ONGOING'
    WHEN 'FAILED' THEN 'CANCELLED'
    ELSE status
END
WHERE status IN ('UNASSIGNED', 'ASSIGNED', 'IN_PROGRESS', 'FAILED');

CREATE TABLE IF NOT EXISTS trip_stops (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    sequence_num INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    location_name VARCHAR(255),
    location_address VARCHAR(255),
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    eta_label VARCHAR(100),
    distance_from_prev_km INTEGER,
    contact_name VARCHAR(150),
    contact_phone VARCHAR(30),
    order_id UUID REFERENCES orders(id),
    stock_item_id UUID REFERENCES stock_items(id),
    restock_quantity INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_trip_stops_sequence UNIQUE (trip_id, sequence_num),
    CONSTRAINT ck_trip_stops_one_subject CHECK (
        NOT (order_id IS NOT NULL AND stock_item_id IS NOT NULL)
    ),
    CONSTRAINT ck_trip_stops_restock_quantity CHECK (
        restock_quantity IS NULL OR restock_quantity > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_trip_stops_trip_sequence
    ON trip_stops(trip_id, sequence_num);
CREATE INDEX IF NOT EXISTS idx_trip_stops_order_id
    ON trip_stops(order_id);
CREATE INDEX IF NOT EXISTS idx_trip_stops_stock_item_id
    ON trip_stops(stock_item_id);
