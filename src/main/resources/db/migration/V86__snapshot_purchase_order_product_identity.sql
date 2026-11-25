ALTER TABLE purchase_order_items
    ADD COLUMN product_sku_snapshot VARCHAR(50),
    ADD COLUMN product_name_snapshot VARCHAR(150);

UPDATE purchase_order_items poi
SET product_sku_snapshot = si.sku,
    product_name_snapshot = si.name
FROM stock_items si
WHERE si.id = poi.stock_item_id;
