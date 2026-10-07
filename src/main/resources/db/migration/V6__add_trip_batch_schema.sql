-- The trip-stop API stores batched delivery/restock stops. These columns and
-- tables are additive so existing trip rows remain migratable.
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS trip_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS origin_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS origin_address TEXT,
    ADD COLUMN IF NOT EXISTS scheduled_start_label VARCHAR(100),
    ADD COLUMN IF NOT EXISTS total_distance_km INTEGER;

UPDATE trips
SET trip_number = 'TRIP-' || upper(substr(replace(id::text, '-', ''), 1, 16))
WHERE trip_number IS NULL;

ALTER TABLE trips
    ALTER COLUMN trip_number SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_trips_trip_number
    ON trips (trip_number);

CREATE TABLE IF NOT EXISTS trip_stops (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    sequence_num INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    location_name VARCHAR(150),
    location_address TEXT,
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
    CONSTRAINT ck_trip_stops_sequence_positive CHECK (sequence_num > 0),
    CONSTRAINT ck_trip_stops_single_work_item CHECK (NOT (order_id IS NOT NULL AND stock_item_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_trip_stops_trip_sequence
    ON trip_stops (trip_id, sequence_num);

CREATE INDEX IF NOT EXISTS idx_trip_stops_order_id
    ON trip_stops (order_id);
