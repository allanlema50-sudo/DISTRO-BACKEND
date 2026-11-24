CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,
    checkout_payment_id UUID NOT NULL REFERENCES checkout_payments(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    provider_reference VARCHAR(64) NOT NULL UNIQUE,
    provider_refund_id VARCHAR(80),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reason TEXT NOT NULL,
    failure_reason TEXT,
    next_reconciliation_at TIMESTAMPTZ,
    reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_refund_status CHECK (status IN ('REQUESTED','PENDING','PROCESSING','PROCESSED','FAILED')),
    CONSTRAINT ck_payment_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_refund_attempts CHECK (reconciliation_attempts >= 0),
    CONSTRAINT ck_payment_refund_currency CHECK (currency = upper(currency) AND length(currency) = 3)
);
CREATE INDEX idx_payment_refunds_payment_created ON payment_refunds(checkout_payment_id, created_at DESC);
CREATE UNIQUE INDEX uk_payment_refunds_one_active_per_payment
    ON payment_refunds(checkout_payment_id)
    WHERE status IN ('REQUESTED','PENDING','PROCESSING','PROCESSED');
CREATE INDEX idx_payment_refunds_status_created ON payment_refunds(status, created_at);
CREATE INDEX idx_payment_refunds_reconciliation ON payment_refunds(next_reconciliation_at, created_at)
    WHERE status IN ('REQUESTED','PENDING','PROCESSING');

ALTER TABLE checkout_payments DROP CONSTRAINT ck_checkout_payment_status;
ALTER TABLE checkout_payments ADD CONSTRAINT ck_checkout_payment_status
    CHECK (status IN ('PENDING','SUCCESS','REFUND_PENDING','REFUNDED','FAILED','CANCELLED','TIMED_OUT','UNKNOWN'));

ALTER TABLE checkouts DROP CONSTRAINT ck_checkouts_status;
ALTER TABLE checkouts ADD CONSTRAINT ck_checkouts_status
    CHECK (status IN ('PAYMENT_PENDING','PAID','REFUND_PENDING','REFUNDED','PAYMENT_FAILED','CANCELLED','UNKNOWN'));
