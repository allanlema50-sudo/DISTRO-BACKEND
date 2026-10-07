CREATE TABLE restock_requests (
    id UUID PRIMARY KEY,
    distributor_organization_id UUID NOT NULL REFERENCES organizations(id),
    manufacturer_organization_id UUID NOT NULL REFERENCES organizations(id),
    stock_item_id UUID NOT NULL REFERENCES stock_items(id),
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by UUID NOT NULL REFERENCES users(id),
    note TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_restock_requests_distributor_created
    ON restock_requests(distributor_organization_id, created_at DESC);

CREATE INDEX idx_restock_requests_manufacturer_created
    ON restock_requests(manufacturer_organization_id, created_at DESC);
