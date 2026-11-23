CREATE TABLE supplier_payout_attempts (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES supplier_settlements(id),
    attempt_number INTEGER NOT NULL,
    provider_reference VARCHAR(64) NOT NULL UNIQUE,
    provider_transfer_code VARCHAR(120),
    status VARCHAR(24) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    failure_reason TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_supplier_payout_attempt_number UNIQUE (settlement_id, attempt_number),
    CONSTRAINT ck_supplier_payout_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_supplier_payout_attempt_amount CHECK (amount > 0),
    CONSTRAINT ck_supplier_payout_attempt_currency CHECK (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_supplier_payout_attempt_status CHECK (status IN
        ('PROCESSING', 'SUCCEEDED', 'FAILED', 'REVERSED', 'OUTCOME_UNKNOWN'))
);

CREATE INDEX idx_supplier_payout_attempt_settlement_started
    ON supplier_payout_attempts(settlement_id, started_at DESC);

-- Preserve callback lookup for transfers already initiated before this migration.
INSERT INTO supplier_payout_attempts (
    id, settlement_id, attempt_number, provider_reference, provider_transfer_code,
    status, amount, currency, failure_reason, started_at, completed_at
)
SELECT gen_random_uuid(), id, payout_attempts, payout_reference, provider_transfer_code,
       CASE status
           WHEN 'SETTLED' THEN 'SUCCEEDED'
           WHEN 'FAILED' THEN 'FAILED'
           WHEN 'REVERSED' THEN 'REVERSED'
           ELSE 'OUTCOME_UNKNOWN'
       END,
       amount, currency, failure_reason, COALESCE(last_attempt_at, created_at), settled_at
FROM supplier_settlements
WHERE payout_reference IS NOT NULL AND payout_attempts > 0
ON CONFLICT DO NOTHING;
