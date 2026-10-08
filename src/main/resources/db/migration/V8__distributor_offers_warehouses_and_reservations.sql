CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    address TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_warehouses_organization_code UNIQUE (organization_id, code)
);

CREATE INDEX idx_warehouses_organization_active
    ON warehouses(organization_id, active);

ALTER TABLE stock_items
    ADD COLUMN source_stock_item_id UUID REFERENCES stock_items(id),
    ADD COLUMN warehouse_id UUID REFERENCES warehouses(id),
    ADD COLUMN reserved_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE stock_items
    ADD CONSTRAINT ck_stock_items_reserved_nonnegative
        CHECK (reserved_quantity >= 0) NOT VALID,
    ADD CONSTRAINT ck_stock_items_reserved_within_quantity
        CHECK (reserved_quantity <= quantity_on_hand) NOT VALID,
    ADD CONSTRAINT ck_stock_items_source_not_self
        CHECK (source_stock_item_id IS NULL OR source_stock_item_id <> id);

CREATE INDEX idx_stock_items_source_stock_item
    ON stock_items(source_stock_item_id);

CREATE INDEX idx_stock_items_warehouse_active
    ON stock_items(warehouse_id, is_active);

CREATE TABLE organization_notifications (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_organization_notifications_org_created
    ON organization_notifications(organization_id, created_at DESC);
