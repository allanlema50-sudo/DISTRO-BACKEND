-- Stock SKUs are identifiers within an organization, not a platform-wide namespace.
-- The partial predicate preserves legacy catalog rows that have no owner yet.
ALTER TABLE stock_items
    DROP CONSTRAINT IF EXISTS stock_items_sku_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_items_organization_sku_ci
    ON stock_items (organization_id, upper(sku))
    WHERE organization_id IS NOT NULL;
