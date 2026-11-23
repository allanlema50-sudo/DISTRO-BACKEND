ALTER TABLE supplier_settlements
    ADD COLUMN IF NOT EXISTS last_status_check_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_supplier_settlements_paystack_reconcile
    ON supplier_settlements (last_attempt_at, last_status_check_at)
    WHERE provider = 'PAYSTACK'
      AND status = 'PROCESSING';
