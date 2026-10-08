-- SKU values are unique within an organization, case-insensitively.
-- V3 already removed the legacy global constraint and added the ownership column.
-- Do not silently rename business identifiers when legacy collisions exist.
DO $$
BEGIN
    IF EXISTS (
        SELECT organization_id, upper(sku)
        FROM stock_items
        WHERE organization_id IS NOT NULL
        GROUP BY organization_id, upper(sku)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V6 found case-insensitive SKU collisions; resolve duplicate organization/SKU values before migration';
    END IF;
END $$;

ALTER TABLE stock_items DROP CONSTRAINT IF EXISTS uk_stock_items_org_sku;

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_items_organization_sku_ci
    ON stock_items (organization_id, upper(sku))
    WHERE organization_id IS NOT NULL;
