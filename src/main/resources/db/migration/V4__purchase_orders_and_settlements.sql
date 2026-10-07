CREATE SEQUENCE IF NOT EXISTS purchase_order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    purchase_order_number VARCHAR(30) NOT NULL UNIQUE,
    buyer_organization_id UUID NOT NULL REFERENCES organizations(id),
    supplier_organization_id UUID NOT NULL REFERENCES organizations(id),
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    rejection_reason TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    reviewed_by UUID REFERENCES users(id),
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    fulfilled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_orders_status CHECK (
        status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'FULFILLED')
    ),
    CONSTRAINT ck_purchase_orders_positive_total CHECK (total_amount > 0),
    CONSTRAINT ck_purchase_orders_distinct_parties CHECK (
        buyer_organization_id <> supplier_organization_id
    )
);

CREATE INDEX idx_purchase_orders_buyer_created
    ON purchase_orders(buyer_organization_id, created_at DESC);
CREATE INDEX idx_purchase_orders_supplier_status
    ON purchase_orders(supplier_organization_id, status);

CREATE TABLE purchase_order_items (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    stock_item_id UUID NOT NULL REFERENCES stock_items(id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_order_item_stock UNIQUE (purchase_order_id, stock_item_id),
    CONSTRAINT ck_purchase_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_item_unit_price CHECK (unit_price > 0)
);

CREATE INDEX idx_purchase_order_items_order
    ON purchase_order_items(purchase_order_id);

CREATE TABLE purchase_order_settlements (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    provider_reference VARCHAR(100) UNIQUE,
    note TEXT,
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_settlement_status CHECK (
        status IN ('PENDING', 'SETTLED', 'FAILED', 'REVERSED')
    ),
    CONSTRAINT ck_purchase_settlement_positive_amount CHECK (amount > 0)
);

CREATE INDEX idx_purchase_settlements_order_created
    ON purchase_order_settlements(purchase_order_id, created_at DESC);
