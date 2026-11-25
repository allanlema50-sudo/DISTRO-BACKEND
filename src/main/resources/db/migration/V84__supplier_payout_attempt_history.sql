CREATE TABLE supplier_payout_attempts (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES supplier_settlements(id),
    attempt_number INTEGER NOT NULL,
    provider_reference VARCHAR(120) NOT NULL UNIQUE,
    provider_transfer_code VARCHAR(120),
    status VARCHAR(24) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    failure_reason TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    last_status_check_at TIMESTAMPTZ,
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
CREATE INDEX idx_supplier_payout_attempt_reconciliation
    ON supplier_payout_attempts(status, started_at, last_status_check_at)
    WHERE status IN ('PROCESSING', 'OUTCOME_UNKNOWN');

-- Preserve the ledger event identity for old transfers. payout_attempts also counts failures,
-- while the legacy PAYOUT_SETTLED_n suffix counts only posted payouts.
INSERT INTO supplier_payout_attempts (
    id, settlement_id, attempt_number, provider_reference, provider_transfer_code,
    status, amount, currency, failure_reason, started_at, completed_at
)
SELECT gen_random_uuid(), s.id,
       COALESCE(legacy_payout.attempt_number, s.payout_attempts),
       s.payout_reference, s.provider_transfer_code,
       CASE s.status
           WHEN 'SETTLED' THEN 'SUCCEEDED'
           WHEN 'FAILED' THEN 'FAILED'
           WHEN 'REVERSED' THEN 'REVERSED'
           ELSE 'OUTCOME_UNKNOWN'
       END,
       s.amount, s.currency, s.failure_reason, COALESCE(s.last_attempt_at, s.created_at), s.settled_at
FROM supplier_settlements s
LEFT JOIN LATERAL (
    SELECT substring(j.event_type FROM '^PAYOUT_SETTLED_([0-9]+)$')::INTEGER AS attempt_number
    FROM ledger_journals j
    WHERE j.source_type = 'SUPPLIER_SETTLEMENT'
      AND j.source_id = s.id
      AND j.event_type ~ '^PAYOUT_SETTLED_[0-9]+$'
      AND j.provider_reference = s.payout_reference
    ORDER BY j.created_at DESC
    LIMIT 1
) legacy_payout ON TRUE
WHERE s.payout_reference IS NOT NULL AND s.payout_attempts > 0
ON CONFLICT DO NOTHING;
