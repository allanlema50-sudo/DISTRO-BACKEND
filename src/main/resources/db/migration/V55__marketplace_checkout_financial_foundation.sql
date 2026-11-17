-- Marketplace checkout persistence foundation. Existing order and Daraja records remain intact.
CREATE TABLE checkouts (
 id UUID PRIMARY KEY,
 checkout_reference VARCHAR(40) NOT NULL UNIQUE,
 idempotency_key VARCHAR(120),
 buyer_type VARCHAR(32) NOT NULL,
 buyer_organization_id UUID REFERENCES organizations(id),
 buyer_user_id UUID REFERENCES users(id),
 created_by UUID NOT NULL REFERENCES users(id),
 status VARCHAR(24) NOT NULL DEFAULT 'PAYMENT_PENDING',
 total_amount NUMERIC(14,2) NOT NULL,
 currency VARCHAR(3) NOT NULL DEFAULT 'KES',
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_checkouts_buyer_key UNIQUE (buyer_organization_id, idempotency_key),
 CONSTRAINT ck_checkouts_buyer CHECK ((buyer_organization_id IS NOT NULL) <> (buyer_user_id IS NOT NULL)),
 CONSTRAINT ck_checkouts_status CHECK (status IN ('PAYMENT_PENDING','PAID','PAYMENT_FAILED','CANCELLED','UNKNOWN')),
 CONSTRAINT ck_checkouts_total CHECK (total_amount > 0)
);
CREATE INDEX idx_checkouts_buyer_created ON checkouts(buyer_organization_id, created_at DESC);
ALTER TABLE purchase_orders ADD COLUMN checkout_id UUID REFERENCES checkouts(id);
ALTER TABLE purchase_orders ADD COLUMN destination_warehouse_id UUID REFERENCES warehouses(id);
CREATE INDEX idx_purchase_orders_checkout ON purchase_orders(checkout_id);
CREATE TABLE checkout_payments (
 id UUID PRIMARY KEY,
 checkout_id UUID NOT NULL UNIQUE REFERENCES checkouts(id),
 method VARCHAR(20) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 demo BOOLEAN NOT NULL DEFAULT FALSE,
 demo_label VARCHAR(80),
 amount NUMERIC(14,2) NOT NULL,
 currency VARCHAR(3) NOT NULL DEFAULT 'KES',
 provider_reference VARCHAR(120) UNIQUE,
 idempotency_key VARCHAR(120) UNIQUE,
 mpesa_checkout_request_id VARCHAR(100) UNIQUE,
 mpesa_merchant_request_id VARCHAR(100),
 failure_reason TEXT,
 provider_payload JSONB,
 initiated_at TIMESTAMPTZ NOT NULL,
 confirmed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_checkout_payment_status CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','TIMED_OUT','UNKNOWN')),
 CONSTRAINT ck_checkout_payment_positive CHECK (amount > 0)
);
CREATE TABLE checkout_items (
 id UUID PRIMARY KEY,
 checkout_id UUID NOT NULL REFERENCES checkouts(id),
 purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
 supplier_organization_id UUID NOT NULL REFERENCES organizations(id),
 product_id UUID NOT NULL REFERENCES stock_items(id),
 supplier_name_snapshot VARCHAR(150) NOT NULL,
 product_name_snapshot VARCHAR(200) NOT NULL,
 quantity INTEGER NOT NULL,
 unit_price NUMERIC(14,2) NOT NULL,
 line_total NUMERIC(14,2) NOT NULL,
 currency VARCHAR(3) NOT NULL DEFAULT 'KES',
 destination_warehouse_id UUID REFERENCES warehouses(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_checkout_items_quantity CHECK (quantity > 0),
 CONSTRAINT ck_checkout_items_amounts CHECK (unit_price > 0 AND line_total = unit_price * quantity)
);
CREATE INDEX idx_checkout_items_checkout ON checkout_items(checkout_id);
CREATE TABLE payment_allocations (
 id UUID PRIMARY KEY,
 payment_id UUID NOT NULL REFERENCES checkout_payments(id),
 purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
 supplier_organization_id UUID NOT NULL REFERENCES organizations(id),
 gross_amount NUMERIC(14,2) NOT NULL,
 provider_fee NUMERIC(14,2) NOT NULL DEFAULT 0,
 commission NUMERIC(14,2) NOT NULL DEFAULT 0,
 supplier_amount NUMERIC(14,2) NOT NULL,
 demo BOOLEAN NOT NULL DEFAULT FALSE,
 currency VARCHAR(3) NOT NULL DEFAULT 'KES',
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_payment_allocation_payment_order UNIQUE(payment_id, purchase_order_id),
 CONSTRAINT ck_payment_allocation_amounts CHECK (gross_amount > 0 AND provider_fee >= 0 AND commission >= 0 AND supplier_amount >= 0 AND gross_amount = provider_fee + commission + supplier_amount)
);
CREATE TABLE supplier_settlements (
 id UUID PRIMARY KEY,
 payment_allocation_id UUID NOT NULL UNIQUE REFERENCES payment_allocations(id),
 supplier_organization_id UUID NOT NULL REFERENCES organizations(id),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 amount NUMERIC(14,2) NOT NULL,
 currency VARCHAR(3) NOT NULL DEFAULT 'KES',
 payout_reference VARCHAR(120) UNIQUE,
 demo BOOLEAN NOT NULL DEFAULT FALSE,
 note TEXT,
 settled_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_supplier_settlement_status CHECK (status IN ('PENDING','PROCESSING','SETTLED','FAILED','REVERSED')),
 CONSTRAINT ck_supplier_settlement_amount CHECK (amount > 0)
);
CREATE INDEX idx_supplier_settlement_supplier_status ON supplier_settlements(supplier_organization_id,status);
