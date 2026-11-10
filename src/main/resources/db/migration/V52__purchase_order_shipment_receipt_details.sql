CREATE TABLE purchase_order_shipment_receipt_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES purchase_order_shipments(id) ON DELETE CASCADE,
    purchase_order_item_id UUID NOT NULL REFERENCES purchase_order_items(id),
    expected_quantity INTEGER NOT NULL CHECK (expected_quantity > 0),
    accepted_quantity INTEGER NOT NULL CHECK (accepted_quantity >= 0),
    damaged_quantity INTEGER NOT NULL CHECK (damaged_quantity >= 0),
    missing_quantity INTEGER NOT NULL CHECK (missing_quantity >= 0),
    CONSTRAINT uk_shipment_receipt_purchase_order_item UNIQUE (shipment_id, purchase_order_item_id),
    CONSTRAINT ck_shipment_receipt_quantity_breakdown CHECK (
        accepted_quantity + damaged_quantity + missing_quantity = expected_quantity)
);

CREATE INDEX idx_shipment_receipt_lines_shipment ON purchase_order_shipment_receipt_lines(shipment_id);
