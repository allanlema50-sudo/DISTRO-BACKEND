ALTER TABLE ledger_accounts DROP CONSTRAINT ck_ledger_account_type;
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_account_type CHECK (
    account_type IN (
        'PROVIDER_CLEARING',
        'ORGANIZATION_PAYABLE',
        'PLATFORM_REVENUE',
        'PAYMENT_PROCESSING_PAYABLE',
        'PAYOUT_CLEARING'
    )
);

CREATE TABLE organization_payouts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    amount NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    bank_reference VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_organization_payouts_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_organization_payout_amount CHECK (amount > 0),
    CONSTRAINT ck_organization_payout_currency CHECK (currency = upper(currency) AND length(currency) = 3)
);

CREATE INDEX idx_organization_payouts_organization_created
    ON organization_payouts(organization_id, created_at DESC);
