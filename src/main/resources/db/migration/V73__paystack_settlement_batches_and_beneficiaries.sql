CREATE TABLE payout_beneficiaries (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    method VARCHAR(30) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    account_mask VARCHAR(40) NOT NULL,
    bank_code VARCHAR(30),
    provider_recipient_code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    created_by UUID NOT NULL REFERENCES users(id),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payout_beneficiary_method CHECK (method IN ('MOBILE_MONEY', 'BANK_ACCOUNT')),
    CONSTRAINT ck_payout_beneficiary_provider CHECK (provider = 'PAYSTACK'),
    CONSTRAINT ck_payout_beneficiary_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_payout_beneficiary_status CHECK (status IN ('PENDING_REVIEW', 'ACTIVE', 'SUSPENDED', 'REJECTED'))
);
CREATE INDEX idx_payout_beneficiary_organization_status
    ON payout_beneficiaries(organization_id, status, created_at DESC);
CREATE UNIQUE INDEX uk_payout_beneficiary_active_org_currency
    ON payout_beneficiaries(organization_id, currency)
    WHERE status = 'ACTIVE';

CREATE TABLE settlement_batches (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
    currency VARCHAR(3) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL,
    settlement_count INTEGER NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_settlement_batch_provider CHECK (provider = 'PAYSTACK'),
    CONSTRAINT ck_settlement_batch_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'PROCESSING', 'COMPLETED', 'PARTIALLY_FAILED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_settlement_batch_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_settlement_batch_amount CHECK (total_amount > 0),
    CONSTRAINT ck_settlement_batch_count CHECK (settlement_count > 0)
);
CREATE INDEX idx_settlement_batches_status_created
    ON settlement_batches(status, created_at DESC);

ALTER TABLE supplier_settlements
    ADD COLUMN beneficiary_id UUID REFERENCES payout_beneficiaries(id),
    ADD COLUMN settlement_batch_id UUID REFERENCES settlement_batches(id),
    ADD COLUMN provider VARCHAR(20),
    ADD COLUMN provider_transfer_code VARCHAR(120),
    ADD COLUMN payout_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN failure_reason TEXT;

CREATE INDEX idx_supplier_settlements_batch ON supplier_settlements(settlement_batch_id);
CREATE INDEX idx_supplier_settlements_payout_reference ON supplier_settlements(payout_reference);
