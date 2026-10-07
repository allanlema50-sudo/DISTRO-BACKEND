-- Reconcile the Orders/Payments/Trips entities merged after V1.
-- This migration intentionally fails when existing rows cannot be assigned to
-- an organization. Production operators must backfill ownership explicitly;
-- silently assigning live data to a default tenant would be unsafe.

CREATE SEQUENCE IF NOT EXISTS order_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM orders WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'V2 requires organization_id for every existing order before migration';
    END IF;
END $$;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE orders ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_organization_status
    ON orders(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_order_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_org ON orders(organization_id);
CREATE INDEX IF NOT EXISTS idx_order_status ON orders(status);

-- Keep order numbers collision-free when upgrading a database that already
-- contains ORD-<number> values.
SELECT setval(
    'order_number_seq',
    COALESCE(MAX(CAST(SUBSTRING(order_number FROM 5) AS BIGINT)), 0) + 1,
    false
)
FROM orders
WHERE order_number ~ '^ORD-[0-9]+$';

ALTER TABLE stock_items ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE stock_items ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM stock_items WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'V2 requires organization_id for every existing stock item before migration';
    END IF;
END $$;

ALTER TABLE stock_items
    ADD CONSTRAINT fk_stock_items_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE stock_items ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE stock_items DROP CONSTRAINT IF EXISTS stock_items_sku_key;
ALTER TABLE stock_items
    ADD CONSTRAINT uk_stock_items_org_sku UNIQUE (organization_id, sku);
CREATE INDEX IF NOT EXISTS idx_stock_items_organization
    ON stock_items(organization_id);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
DO $$
BEGIN
    IF EXISTS (
        SELECT order_id FROM payments GROUP BY order_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V2 requires at most one payment record per order before migration';
    END IF;
END $$;
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_order_id UNIQUE (order_id);
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key);
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_amount_positive CHECK (amount > 0);

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

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM trips WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'V2 requires organization_id for every existing trip before migration';
    END IF;
END $$;

ALTER TABLE trips
    ADD CONSTRAINT fk_trips_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE trips ALTER COLUMN trip_number SET NOT NULL;
ALTER TABLE trips ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE trips ADD CONSTRAINT uk_trips_trip_number UNIQUE (trip_number);
CREATE INDEX IF NOT EXISTS idx_trips_organization_status
    ON trips(organization_id, status);

CREATE TABLE trip_stops (
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

CREATE INDEX idx_trip_stops_trip_sequence ON trip_stops(trip_id, sequence_num);
CREATE INDEX idx_trip_stops_order_id ON trip_stops(order_id);
CREATE INDEX idx_trip_stops_stock_item_id ON trip_stops(stock_item_id);
