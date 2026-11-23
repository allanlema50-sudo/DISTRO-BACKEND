ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS fulfilment_status VARCHAR(40) NOT NULL DEFAULT 'PAYMENT_PENDING';

ALTER TABLE purchase_order_shipments
    ADD COLUMN IF NOT EXISTS origin_warehouse_id UUID REFERENCES warehouses(id);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier_fulfilment
    ON purchase_orders(supplier_organization_id, fulfilment_status, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_purchase_order_shipments_origin
    ON purchase_order_shipments(origin_warehouse_id);
