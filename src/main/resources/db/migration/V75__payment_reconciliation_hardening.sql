ALTER TABLE provider_webhook_events
    ADD COLUMN claim_token UUID;

ALTER TABLE supplier_settlements
    ADD COLUMN last_status_check_at TIMESTAMPTZ;

CREATE INDEX idx_supplier_settlements_paystack_reconcile
    ON supplier_settlements (last_attempt_at, last_status_check_at)
    WHERE provider = 'PAYSTACK'
      AND status = 'PROCESSING';
