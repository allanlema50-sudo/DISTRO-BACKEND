-- Snapshot which Paystack environment owns each marketplace checkout payment.
-- Existing rows remain NULL because their historical provider mode cannot be
-- safely inferred from the current deployment configuration.
ALTER TABLE checkout_payments
    ADD COLUMN provider_mode VARCHAR(8),
    ADD CONSTRAINT ck_checkout_payments_provider_mode
        CHECK (provider_mode IS NULL OR provider_mode IN ('TEST', 'LIVE'));
