-- Support the bounded, newest-first unified administrator activity feed.
CREATE INDEX idx_payments_activity_timeline
    ON payments (initiated_at DESC, id DESC);

CREATE INDEX idx_payments_activity_provider_mode_status
    ON payments (provider, provider_mode, status, initiated_at DESC, id DESC);

CREATE INDEX idx_checkout_payments_activity_timeline
    ON checkout_payments (initiated_at DESC, id DESC);

CREATE INDEX idx_checkout_payments_activity_provider_mode_status
    ON checkout_payments (provider, provider_mode, status, initiated_at DESC, id DESC);
