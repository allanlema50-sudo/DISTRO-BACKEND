CREATE TABLE payment_disputes (
    id UUID PRIMARY KEY,
    provider_dispute_id VARCHAR(80) NOT NULL UNIQUE,
    checkout_payment_id UUID REFERENCES checkout_payments(id),
    transaction_reference VARCHAR(120),
    status VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    resolution VARCHAR(80),
    disputed_amount NUMERIC(14,2),
    currency VARCHAR(3),
    due_at TIMESTAMPTZ,
    last_event_name VARCHAR(80) NOT NULL,
    last_event_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reminder_count INTEGER NOT NULL DEFAULT 0,
    review_note TEXT,
    cleared_by UUID REFERENCES users(id),
    cleared_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_dispute_status CHECK (
        status IN ('AWAITING_MERCHANT_FEEDBACK','AWAITING_BANK_FEEDBACK','PENDING','RESOLVED','UNKNOWN')),
    CONSTRAINT ck_payment_dispute_amount CHECK (disputed_amount IS NULL OR disputed_amount >= 0),
    CONSTRAINT ck_payment_dispute_reminders CHECK (reminder_count >= 0),
    CONSTRAINT ck_payment_dispute_currency CHECK (currency IS NULL OR (currency = upper(currency) AND length(currency) = 3))
);

CREATE INDEX idx_payment_disputes_status_due ON payment_disputes(status, due_at, last_event_at);
CREATE INDEX idx_payment_disputes_payment ON payment_disputes(checkout_payment_id, last_event_at DESC);

ALTER TABLE provider_webhook_events
    ADD COLUMN provider_dispute_id VARCHAR(80),
    ADD COLUMN dispute_status VARCHAR(40),
    ADD COLUMN dispute_resolution VARCHAR(80),
    ADD COLUMN dispute_due_at TIMESTAMPTZ;
