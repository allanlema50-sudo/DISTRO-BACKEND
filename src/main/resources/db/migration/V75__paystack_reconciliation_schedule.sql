ALTER TABLE checkout_payments
    ADD COLUMN next_reconciliation_at TIMESTAMPTZ,
    ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0;

UPDATE checkout_payments
SET next_reconciliation_at = COALESCE(initiated_at, CURRENT_TIMESTAMP)
WHERE provider = 'PAYSTACK' AND status = 'PENDING';

CREATE INDEX idx_checkout_payments_paystack_reconciliation
    ON checkout_payments(next_reconciliation_at, initiated_at)
    WHERE provider = 'PAYSTACK' AND status = 'PENDING';

ALTER TABLE supplier_settlements
    ADD COLUMN next_reconciliation_at TIMESTAMPTZ,
    ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0;

UPDATE supplier_settlements
SET next_reconciliation_at = COALESCE(last_attempt_at, CURRENT_TIMESTAMP)
WHERE provider = 'PAYSTACK' AND status = 'PROCESSING';

CREATE INDEX idx_supplier_settlements_paystack_reconciliation
    ON supplier_settlements(next_reconciliation_at, last_attempt_at)
    WHERE provider = 'PAYSTACK' AND status = 'PROCESSING';
