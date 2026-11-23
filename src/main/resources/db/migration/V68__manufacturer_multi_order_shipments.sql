CREATE TABLE purchase_order_shipment_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_reference VARCHAR(40) NOT NULL UNIQUE,
    manufacturer_organization_id UUID NOT NULL REFERENCES organizations(id),
    origin_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED', 'TRIP_ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'RECEIVED', 'CANCELLED')),
    trip_id UUID REFERENCES trips(id),
    created_by UUID NOT NULL REFERENCES users(id),
    dispatched_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE purchase_order_shipment_group_items (
    shipment_group_id UUID NOT NULL REFERENCES purchase_order_shipment_groups(id) ON DELETE CASCADE,
    purchase_order_shipment_id UUID NOT NULL REFERENCES purchase_order_shipments(id),
    PRIMARY KEY (shipment_group_id, purchase_order_shipment_id),
    CONSTRAINT uk_purchase_order_shipment_group_consignment UNIQUE (purchase_order_shipment_id)
);

CREATE INDEX idx_po_shipment_group_org_status
    ON purchase_order_shipment_groups(manufacturer_organization_id, status, created_at DESC);

CREATE INDEX idx_po_shipment_group_items_group
    ON purchase_order_shipment_group_items(shipment_group_id);
