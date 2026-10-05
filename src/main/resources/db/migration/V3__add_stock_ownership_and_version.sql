ALTER TABLE stock_items
    ADD COLUMN organization_id UUID REFERENCES organizations(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_stock_items_organization_active
    ON stock_items(organization_id, is_active);
