ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS reservation_quantity_delta INTEGER;

CREATE INDEX IF NOT EXISTS idx_stock_movements_reference
    ON stock_movements(reference_type, reference_id, created_at DESC);
