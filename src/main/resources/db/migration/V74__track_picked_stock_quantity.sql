ALTER TABLE stock_items
    ADD COLUMN picked_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE stock_items
    ADD CONSTRAINT chk_stock_items_picked_quantity_nonnegative CHECK (picked_quantity >= 0);
