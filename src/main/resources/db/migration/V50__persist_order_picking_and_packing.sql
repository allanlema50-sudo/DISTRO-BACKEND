ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS package_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS picked_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS packed_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_items_fulfillment_quantities
    CHECK (picked_quantity >= 0 AND packed_quantity >= 0
        AND packed_quantity <= picked_quantity AND picked_quantity <= quantity);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_package_count_nonnegative CHECK (package_count >= 0);
