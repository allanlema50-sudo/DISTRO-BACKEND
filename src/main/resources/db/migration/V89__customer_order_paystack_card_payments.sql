ALTER TABLE payments
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'DARAJA',
    ADD COLUMN provider_reference VARCHAR(120),
    ADD COLUMN provider_authorization_url TEXT,
    ADD COLUMN provider_mode VARCHAR(8),
    ADD CONSTRAINT ck_payments_provider CHECK (provider IN ('DARAJA', 'PAYSTACK', 'DEMO')),
    ADD CONSTRAINT ck_payments_provider_mode CHECK (provider_mode IS NULL OR provider_mode IN ('TEST', 'LIVE'));

CREATE UNIQUE INDEX uk_payments_provider_reference
    ON payments(provider_reference) WHERE provider_reference IS NOT NULL;

CREATE TABLE customer_payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    attempt_number INTEGER NOT NULL,
    provider_reference VARCHAR(120) NOT NULL UNIQUE,
    provider_mode VARCHAR(8) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'INITIALIZING',
    authorization_url TEXT,
    access_code VARCHAR(120),
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    failure_reason TEXT,
    next_verification_at TIMESTAMPTZ,
    verification_attempts INTEGER NOT NULL DEFAULT 0,
    last_verified_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_payment_attempt_number UNIQUE (payment_id, attempt_number),
    CONSTRAINT ck_customer_payment_attempt_status CHECK
        (status IN ('INITIALIZING', 'PENDING', 'OUTCOME_UNKNOWN', 'REVIEW_REQUIRED',
                    'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_customer_payment_attempt_mode CHECK (provider_mode IN ('TEST', 'LIVE')),
    CONSTRAINT ck_customer_payment_attempt_amount CHECK (amount > 0),
    CONSTRAINT ck_customer_payment_attempt_currency CHECK
        (currency = upper(currency) AND length(currency) = 3),
    CONSTRAINT ck_customer_payment_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_customer_payment_attempt_verification_count CHECK (verification_attempts >= 0)
);

CREATE INDEX idx_customer_payment_attempts_reconciliation
    ON customer_payment_attempts(status, next_verification_at, created_at)
    WHERE status IN ('INITIALIZING', 'PENDING', 'OUTCOME_UNKNOWN');

CREATE INDEX idx_customer_payment_attempts_payment_created
    ON customer_payment_attempts(payment_id, created_at DESC);
