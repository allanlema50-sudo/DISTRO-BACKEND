ALTER TABLE shipments
    ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE shipments
    DROP CONSTRAINT IF EXISTS shipments_order_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_shipments_order_id
    ON shipments(order_id)
    WHERE order_id IS NOT NULL;

ALTER TABLE shipments
    ADD COLUMN cargo_description VARCHAR(500),
    ADD COLUMN cargo_category VARCHAR(80),
    ADD COLUMN weight_kg NUMERIC(12, 2),
    ADD COLUMN volume_m3 NUMERIC(12, 3),
    ADD COLUMN handling_requirements JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN delivery_location VARCHAR(300),
    ADD COLUMN delivery_window_start TIMESTAMP,
    ADD COLUMN delivery_window_end TIMESTAMP,
    ADD COLUMN allow_shared_loads BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN special_instructions TEXT;

UPDATE shipments s
SET cargo_description = COALESCE(o.order_number, s.shipment_number),
    cargo_category = 'ORDER',
    weight_kg = 0.01,
    volume_m3 = 0.001,
    delivery_location = LEFT(COALESCE(NULLIF(o.delivery_address, ''), 'Unspecified location'), 300),
    delivery_window_start = COALESCE(s.created_at, CURRENT_TIMESTAMP),
    delivery_window_end = COALESCE(s.created_at, CURRENT_TIMESTAMP) + INTERVAL '1 day'
FROM orders o
WHERE s.order_id = o.id;

UPDATE shipments
SET cargo_description = COALESCE(cargo_description, shipment_number),
    cargo_category = COALESCE(cargo_category, 'GENERAL'),
    weight_kg = COALESCE(weight_kg, 0.01),
    volume_m3 = COALESCE(volume_m3, 0.001),
    delivery_location = COALESCE(delivery_location, 'Unspecified location'),
    delivery_window_start = COALESCE(delivery_window_start, created_at),
    delivery_window_end = COALESCE(delivery_window_end, created_at + INTERVAL '1 day');

ALTER TABLE shipments
    ALTER COLUMN cargo_description SET NOT NULL,
    ALTER COLUMN cargo_category SET NOT NULL,
    ALTER COLUMN weight_kg SET NOT NULL,
    ALTER COLUMN volume_m3 SET NOT NULL,
    ALTER COLUMN delivery_location SET NOT NULL,
    ALTER COLUMN delivery_window_start SET NOT NULL,
    ALTER COLUMN delivery_window_end SET NOT NULL,
    ADD CONSTRAINT chk_shipments_delivery_window
        CHECK (delivery_window_end > delivery_window_start);
