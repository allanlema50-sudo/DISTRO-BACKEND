-- Prepare a controlled conversion for distributor stock rows created before
-- source products, offers, and warehouses were introduced.
-- Exact, unique manufacturer-SKU matches are safe to pre-populate. Ambiguous
-- or unmatched rows require an operator to add an explicit mapping before V10.

CREATE TABLE legacy_stock_source_mappings (
    legacy_stock_item_id UUID PRIMARY KEY REFERENCES stock_items(id) ON DELETE CASCADE,
    manufacturer_source_stock_item_id UUID NOT NULL REFERENCES stock_items(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_legacy_stock_source_mapping_source
    ON legacy_stock_source_mappings(manufacturer_source_stock_item_id);

INSERT INTO legacy_stock_source_mappings (
    legacy_stock_item_id,
    manufacturer_source_stock_item_id
)
SELECT distributor_item.id, MIN(manufacturer_item.id::text)::uuid
FROM stock_items distributor_item
JOIN organizations distributor_org
    ON distributor_org.id = distributor_item.organization_id
JOIN stock_items manufacturer_item
    ON LOWER(manufacturer_item.sku) = LOWER(distributor_item.sku)
JOIN organizations manufacturer_org
    ON manufacturer_org.id = manufacturer_item.organization_id
WHERE distributor_org.type = 'DISTRIBUTOR'
  AND distributor_item.source_stock_item_id IS NULL
  AND manufacturer_org.type = 'MANUFACTURER'
  AND manufacturer_item.source_stock_item_id IS NULL
  AND manufacturer_item.is_active = TRUE
GROUP BY distributor_item.id
HAVING COUNT(manufacturer_item.id) = 1
ON CONFLICT (legacy_stock_item_id) DO NOTHING;
