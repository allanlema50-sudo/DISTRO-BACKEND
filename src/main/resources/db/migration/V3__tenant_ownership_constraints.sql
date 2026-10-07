-- Contract phase for tenant ownership.
-- V2 must be applied first. Operators can backfill unresolved ownership between
-- these migrations without modifying an applied migration.

-- Derive order ownership where the existing customer has an organization.
UPDATE orders o
SET organization_id = u.organization_id
FROM users u
WHERE o.organization_id IS NULL
  AND o.customer_id = u.id
  AND u.organization_id IS NOT NULL;

-- Derive stock ownership only when all historical usages agree on one tenant.
UPDATE stock_items si
SET organization_id = derived.organization_id
FROM (
    SELECT oi.stock_item_id, MIN(o.organization_id) AS organization_id
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    WHERE o.organization_id IS NOT NULL
    GROUP BY oi.stock_item_id
    HAVING COUNT(DISTINCT o.organization_id) = 1
) derived
WHERE si.id = derived.stock_item_id
  AND si.organization_id IS NULL;

-- Derive trip ownership from its existing order or stock reference.
UPDATE trips t
SET organization_id = o.organization_id
FROM orders o
WHERE t.organization_id IS NULL
  AND t.order_id = o.id
  AND o.organization_id IS NOT NULL;

UPDATE trips t
SET organization_id = si.organization_id
FROM stock_items si
WHERE t.organization_id IS NULL
  AND t.stock_item_id = si.id
  AND si.organization_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM orders WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'V3 requires order ownership backfill before tenant constraints';
    END IF;
    IF EXISTS (SELECT 1 FROM stock_items WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'V3 requires stock ownership backfill before tenant constraints';
    END IF;
    IF EXISTS (SELECT 1 FROM trips WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'V3 requires trip ownership backfill before tenant constraints';
    END IF;
END $$;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE orders ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_organization_status
    ON orders(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_order_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_org ON orders(organization_id);
CREATE INDEX IF NOT EXISTS idx_order_status ON orders(status);

SELECT setval(
    'order_number_seq',
    COALESCE(MAX(CAST(SUBSTRING(order_number FROM 5) AS BIGINT)), 0) + 1,
    false
)
FROM orders
WHERE order_number ~ '^ORD-[0-9]+$';

ALTER TABLE stock_items
    ADD CONSTRAINT fk_stock_items_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE stock_items ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE stock_items DROP CONSTRAINT IF EXISTS stock_items_sku_key;
ALTER TABLE stock_items
    ADD CONSTRAINT uk_stock_items_org_sku UNIQUE (organization_id, sku);
CREATE INDEX IF NOT EXISTS idx_stock_items_organization
    ON stock_items(organization_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT order_id FROM payments GROUP BY order_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V3 requires at most one payment record per order before migration';
    END IF;
END $$;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_order_id UNIQUE (order_id);
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key);
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_amount_positive CHECK (amount > 0);

ALTER TABLE trips
    ADD CONSTRAINT fk_trips_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE trips ALTER COLUMN trip_number SET NOT NULL;
ALTER TABLE trips ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE trips ADD CONSTRAINT uk_trips_trip_number UNIQUE (trip_number);
CREATE INDEX IF NOT EXISTS idx_trips_organization_status
    ON trips(organization_id, status);
