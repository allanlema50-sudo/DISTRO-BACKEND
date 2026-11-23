ALTER TABLE checkout_payments
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20);

ALTER TABLE checkout_payments
    ADD COLUMN IF NOT EXISTS provider_authorization_url TEXT;

ALTER TABLE checkout_payments
    ADD COLUMN IF NOT EXISTS provider_access_code VARCHAR(120);

ALTER TABLE checkout_payments
    ADD COLUMN IF NOT EXISTS provider_verification_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS provider_next_verification_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_last_verification_error TEXT;

CREATE INDEX IF NOT EXISTS idx_checkout_payments_provider_status
    ON checkout_payments(provider, status, initiated_at DESC);
