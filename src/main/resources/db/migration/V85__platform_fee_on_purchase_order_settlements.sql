-- Phase 2: platform fee on settled purchase orders. The fee is a receivable
-- from the paying organization; it is never deducted from the manufacturer's
-- payable. It ships at zero.

CREATE TABLE platform_fee_settings (
    id UUID PRIMARY KEY,
    scope VARCHAR(30) NOT NULL,
    fee_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    fee_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    fee_payer VARCHAR(10) NOT NULL DEFAULT 'SUPPLIER',
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_platform_fee_settings_scope UNIQUE (scope),
    CONSTRAINT ck_platform_fee_settings_scope CHECK (scope IN ('PURCHASE_ORDER')),
    CONSTRAINT ck_platform_fee_settings_type CHECK (fee_type IN ('PERCENT', 'FLAT')),
    CONSTRAINT ck_platform_fee_settings_payer CHECK (fee_payer IN ('SUPPLIER', 'BUYER')),
    CONSTRAINT ck_platform_fee_settings_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_platform_fee_settings_value CHECK (
        fee_value >= 0 AND (fee_type <> 'PERCENT' OR fee_value <= 100)
    )
);

INSERT INTO platform_fee_settings (id, scope, fee_type, fee_value, currency, fee_payer)
VALUES (gen_random_uuid(), 'PURCHASE_ORDER', 'PERCENT', 0, 'KES', 'SUPPLIER');

ALTER TABLE purchase_order_settlements
    ADD COLUMN platform_fee_amount NUMERIC(12,2),
    ADD COLUMN platform_fee_payer VARCHAR(10);

ALTER TABLE purchase_order_settlements
    ADD CONSTRAINT ck_purchase_settlement_fee CHECK (platform_fee_amount IS NULL OR platform_fee_amount >= 0),
    ADD CONSTRAINT ck_purchase_settlement_fee_payer
        CHECK (platform_fee_payer IS NULL OR platform_fee_payer IN ('SUPPLIER', 'BUYER'));

ALTER TABLE ledger_accounts DROP CONSTRAINT ck_ledger_account_type;
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_account_type CHECK (
    account_type IN (
        'PROVIDER_CLEARING',
        'ORGANIZATION_PAYABLE',
        'PLATFORM_REVENUE',
        'PAYMENT_PROCESSING_PAYABLE',
        'PAYOUT_CLEARING',
        'PLATFORM_FEE_RECEIVABLE'
    )
);
