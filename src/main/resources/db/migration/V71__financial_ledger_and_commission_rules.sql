CREATE TABLE commission_rules (
    id UUID PRIMARY KEY,
    rate_percent NUMERIC(7,4) NOT NULL,
    fixed_fee NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    organization_type VARCHAR(30),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_commission_rate_range CHECK (rate_percent >= 0 AND rate_percent <= 100),
    CONSTRAINT ck_commission_fixed_fee_nonnegative CHECK (fixed_fee >= 0),
    CONSTRAINT ck_commission_rule_window CHECK (effective_until IS NULL OR effective_until > effective_from),
    CONSTRAINT ck_commission_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_commission_organization_type CHECK (
        organization_type IS NULL OR organization_type IN ('MANUFACTURER', 'DISTRIBUTOR'))
);
CREATE INDEX idx_commission_rules_lookup
    ON commission_rules(currency, organization_type, active, effective_from);

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    account_type VARCHAR(40) NOT NULL,
    organization_id UUID REFERENCES organizations(id),
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ledger_account_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_ledger_account_type CHECK (
        account_type IN ('PROVIDER_CLEARING', 'ORGANIZATION_PAYABLE', 'PLATFORM_REVENUE', 'PAYMENT_PROCESSING_PAYABLE'))
);
CREATE UNIQUE INDEX uk_ledger_platform_account
    ON ledger_accounts(account_type, currency) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uk_ledger_organization_account
    ON ledger_accounts(account_type, organization_id, currency) WHERE organization_id IS NOT NULL;
CREATE INDEX idx_ledger_accounts_organization ON ledger_accounts(organization_id);

CREATE TABLE ledger_journals (
    id UUID PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT NOT NULL,
    simulated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ledger_journal_source_event UNIQUE(source_type, source_id, event_type),
    CONSTRAINT ck_ledger_journal_currency CHECK (currency = upper(currency) AND length(currency) = 3)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    journal_id UUID NOT NULL REFERENCES ledger_journals(id),
    account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT NOT NULL,
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_entry_currency CHECK (currency = upper(currency) AND length(currency) = 3)
);
CREATE INDEX idx_ledger_entries_account ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_journal ON ledger_entries(journal_id);
