ALTER TABLE checkout_payments
    ADD COLUMN IF NOT EXISTS provider_status_checked_at TIMESTAMPTZ;

ALTER TABLE supplier_settlements
    ADD COLUMN IF NOT EXISTS last_status_check_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_checkout_payments_unknown_paystack_reconcile
    ON checkout_payments (initiated_at, provider_status_checked_at)
    WHERE provider = 'PAYSTACK'
      AND status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_supplier_settlements_paystack_reconcile
    ON supplier_settlements (last_attempt_at, last_status_check_at)
    WHERE provider = 'PAYSTACK'
      AND status = 'PROCESSING';
