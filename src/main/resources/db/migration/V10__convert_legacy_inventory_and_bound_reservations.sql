-- Convert legacy distributor inventory without guessing cross-tenant ownership.
-- V9 must be applied first so operators can review and complete mappings.

ALTER TABLE orders
    ADD COLUMN reservation_expires_at TIMESTAMPTZ;

-- Existing unpaid orders had no bounded reservation lifecycle. Expire them at
-- cutover so the cleanup job can release any legacy reservations safely.
UPDATE orders
SET reservation_expires_at = COALESCE(placed_at, CURRENT_TIMESTAMP)
WHERE status = 'PENDING'
  AND NOT EXISTS (
      SELECT 1
      FROM payments p
      WHERE p.order_id = orders.id
        AND p.status = 'PENDING'
        AND p.mpesa_checkout_request_id IS NOT NULL
  );

-- An accepted STK request remains eligible for a late provider callback. Do
-- not let the expiry job fail that order before the payment is reconciled.
UPDATE orders
SET reservation_expires_at = NULL
WHERE status = 'PENDING'
  AND EXISTS (
      SELECT 1
      FROM payments p
      WHERE p.order_id = orders.id
        AND p.status = 'PENDING'
        AND p.mpesa_checkout_request_id IS NOT NULL
  );

CREATE INDEX idx_orders_pending_reservation_expiry
    ON orders(status, reservation_expires_at);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM stock_items legacy_item
        JOIN organizations distributor_org
            ON distributor_org.id = legacy_item.organization_id
        LEFT JOIN legacy_stock_source_mappings mapping
            ON mapping.legacy_stock_item_id = legacy_item.id
        WHERE distributor_org.type = 'DISTRIBUTOR'
          AND legacy_item.source_stock_item_id IS NULL
          AND mapping.legacy_stock_item_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'V10 requires explicit manufacturer source mappings for every legacy distributor stock row; populate legacy_stock_source_mappings and retry';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM legacy_stock_source_mappings mapping
        JOIN stock_items legacy_item
            ON legacy_item.id = mapping.legacy_stock_item_id
        JOIN organizations legacy_org
            ON legacy_org.id = legacy_item.organization_id
        LEFT JOIN stock_items source_item
            ON source_item.id = mapping.manufacturer_source_stock_item_id
        LEFT JOIN organizations source_org
            ON source_org.id = source_item.organization_id
        WHERE legacy_org.type <> 'DISTRIBUTOR'
           OR source_item.id IS NULL
           OR source_org.type <> 'MANUFACTURER'
           OR source_item.is_active IS NOT TRUE
           OR source_item.source_stock_item_id IS NOT NULL
           OR legacy_item.id = source_item.id
    ) THEN
        RAISE EXCEPTION
            'V10 found an invalid legacy stock source mapping; verify distributor and manufacturer ownership before retrying';
    END IF;
END $$;

-- A legacy distributor row without a warehouse is assigned to a deterministic
-- tenant-owned warehouse. This preserves the distributor boundary and avoids
-- assigning stock to another organization.
INSERT INTO warehouses (
    id, organization_id, code, name, active, version, created_at, updated_at
)
SELECT md5('legacy-warehouse:' || distributor_org.id::text)::uuid,
       distributor_org.id,
       'LEGACY',
       'Legacy Warehouse',
       TRUE,
       0,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM organizations distributor_org
WHERE distributor_org.type = 'DISTRIBUTOR'
  AND EXISTS (
      SELECT 1
      FROM stock_items legacy_item
      WHERE legacy_item.organization_id = distributor_org.id
        AND legacy_item.warehouse_id IS NULL
  )
ON CONFLICT (organization_id, code) DO NOTHING;

UPDATE stock_items legacy_item
SET source_stock_item_id = mapping.manufacturer_source_stock_item_id
FROM legacy_stock_source_mappings mapping
WHERE legacy_item.id = mapping.legacy_stock_item_id
  AND legacy_item.source_stock_item_id IS NULL;

UPDATE stock_items legacy_item
SET warehouse_id = warehouse.id
FROM warehouses warehouse
JOIN organizations distributor_org
    ON distributor_org.id = warehouse.organization_id
WHERE legacy_item.organization_id = distributor_org.id
  AND distributor_org.type = 'DISTRIBUTOR'
  AND legacy_item.warehouse_id IS NULL
  AND warehouse.code = 'LEGACY';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM stock_items legacy_item
        JOIN organizations distributor_org
            ON distributor_org.id = legacy_item.organization_id
        WHERE distributor_org.type = 'DISTRIBUTOR'
          AND (legacy_item.source_stock_item_id IS NULL
               OR legacy_item.warehouse_id IS NULL)
    ) THEN
        RAISE EXCEPTION
            'V10 could not convert every distributor stock row into a source-linked warehouse offer';
    END IF;
END $$;
