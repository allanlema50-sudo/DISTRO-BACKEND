-- Phase 2: credit terms. Outstanding credit is calculated from purchase orders
-- and settlements, never stored.

ALTER TABLE purchase_orders
    ADD COLUMN payment_terms VARCHAR(20) NOT NULL DEFAULT 'PAY_NOW',
    ADD COLUMN due_at TIMESTAMPTZ,
    ADD COLUMN overdue_at TIMESTAMPTZ,
    ADD COLUMN credit_terms_version BIGINT;

ALTER TABLE purchase_orders
    ADD CONSTRAINT ck_purchase_orders_payment_terms CHECK (payment_terms IN ('PAY_NOW', 'CREDIT')),
    ADD CONSTRAINT ck_purchase_orders_credit_due CHECK (payment_terms = 'CREDIT' OR due_at IS NULL);

CREATE INDEX idx_purchase_orders_credit_pair
    ON purchase_orders(supplier_organization_id, buyer_organization_id)
    WHERE payment_terms = 'CREDIT';
CREATE INDEX idx_purchase_orders_credit_due
    ON purchase_orders(due_at) WHERE payment_terms = 'CREDIT' AND overdue_at IS NULL;

CREATE TABLE distributor_credit_terms (
    id UUID PRIMARY KEY,
    manufacturer_organization_id UUID NOT NULL REFERENCES organizations(id),
    distributor_organization_id UUID NOT NULL REFERENCES organizations(id),
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    credit_limit NUMERIC(14,2) NOT NULL,
    term_days INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    set_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_distributor_credit_terms_pair
        UNIQUE (manufacturer_organization_id, distributor_organization_id),
    CONSTRAINT ck_distributor_credit_terms_limit CHECK (credit_limit >= 0),
    CONSTRAINT ck_distributor_credit_terms_days CHECK (term_days > 0 AND term_days <= 365),
    CONSTRAINT ck_distributor_credit_terms_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_distributor_credit_terms_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_distributor_credit_terms_parties
        CHECK (manufacturer_organization_id <> distributor_organization_id)
);

-- Shipped empty: with no default and no relationship row, credit is refused.
CREATE TABLE platform_credit_defaults (
    currency VARCHAR(3) PRIMARY KEY,
    default_limit NUMERIC(14,2) NOT NULL,
    default_term_days INTEGER NOT NULL,
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_platform_credit_defaults_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_platform_credit_defaults_limit CHECK (default_limit >= 0),
    CONSTRAINT ck_platform_credit_defaults_days CHECK (default_term_days > 0 AND default_term_days <= 365)
);
