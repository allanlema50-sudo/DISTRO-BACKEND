ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS picked_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS packed_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS package_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_order_items
    ADD CONSTRAINT ck_purchase_order_item_fulfilment_quantities
    CHECK (packed_quantity >= 0 AND picked_quantity >= packed_quantity AND picked_quantity <= quantity)
    NOT VALID;

ALTER TABLE purchase_orders
    ADD CONSTRAINT ck_purchase_order_package_count_nonnegative
    CHECK (package_count >= 0) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_purchase_order_items_stock_item
    ON purchase_order_items(stock_item_id);
