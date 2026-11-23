ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(150),
    ADD COLUMN IF NOT EXISTS location_quantity_delta INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_movement_item_idempotency
    ON stock_movements(stock_item_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE stock_movements
    ADD CONSTRAINT ck_stock_movement_location_delta_nonzero
    CHECK (location_quantity_delta IS NULL OR location_quantity_delta <> 0) NOT VALID;
