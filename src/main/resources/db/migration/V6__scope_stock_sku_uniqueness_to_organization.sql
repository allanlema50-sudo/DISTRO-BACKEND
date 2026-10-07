-- SKU values are unique within an organization, case-insensitively.
-- V3 already removed the legacy global constraint and added the ownership column.
ALTER TABLE stock_items DROP CONSTRAINT IF EXISTS uk_stock_items_org_sku;

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_items_organization_sku_ci
    ON stock_items (organization_id, upper(sku))
    WHERE organization_id IS NOT NULL;
