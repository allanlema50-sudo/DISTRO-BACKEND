CREATE TABLE purchase_order_shipments (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL UNIQUE REFERENCES purchase_orders(id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    status VARCHAR(30) NOT NULL DEFAULT 'READY_FOR_DISPATCH',
    tracking_number VARCHAR(100),
    carrier VARCHAR(100),
    dispatch_note TEXT,
    receipt_note TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    last_updated_by UUID REFERENCES users(id),
    dispatched_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_order_shipment_status CHECK (
        status IN ('READY_FOR_DISPATCH', 'IN_TRANSIT', 'DELIVERED', 'RECEIVED', 'CANCELLED')
    )
);

CREATE INDEX idx_purchase_order_shipments_destination_status
    ON purchase_order_shipments(destination_warehouse_id, status);

ALTER TABLE purchase_order_items
    ADD COLUMN distributor_offer_id UUID REFERENCES stock_items(id);

CREATE INDEX idx_purchase_order_items_distributor_offer
    ON purchase_order_items(distributor_offer_id);

CREATE TABLE stock_audits (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    stock_item_id UUID NOT NULL REFERENCES stock_items(id),
    system_quantity INTEGER NOT NULL,
    counted_quantity INTEGER NOT NULL,
    variance INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECORDED',
    note TEXT,
    recorded_by UUID NOT NULL REFERENCES users(id),
    applied_by UUID REFERENCES users(id),
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_stock_audit_quantities CHECK (
        system_quantity >= 0 AND counted_quantity >= 0
    ),
    CONSTRAINT ck_stock_audit_variance CHECK (
        variance = counted_quantity - system_quantity
    ),
    CONSTRAINT ck_stock_audit_status CHECK (status IN ('RECORDED', 'APPLIED'))
);

CREATE INDEX idx_stock_audits_organization_created
    ON stock_audits(organization_id, created_at DESC);
CREATE INDEX idx_stock_audits_warehouse_created
    ON stock_audits(warehouse_id, created_at DESC);
