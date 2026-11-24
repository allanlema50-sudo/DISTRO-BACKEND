CREATE TABLE provider_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    event_key VARCHAR(64) NOT NULL UNIQUE,
    event_name VARCHAR(80) NOT NULL,
    provider_reference VARCHAR(120) NOT NULL,
    provider_status VARCHAR(40),
    amount_minor BIGINT,
    currency VARCHAR(3),
    gateway_response TEXT,
    transfer_code VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    claim_token UUID,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    processed_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_provider_webhook_provider CHECK (provider IN ('PAYSTACK')),
    CONSTRAINT ck_provider_webhook_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'RETRY', 'PROCESSED', 'DEAD_LETTER')),
    CONSTRAINT ck_provider_webhook_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_provider_webhook_status_next_attempt
    ON provider_webhook_events(status, next_attempt_at, received_at);
