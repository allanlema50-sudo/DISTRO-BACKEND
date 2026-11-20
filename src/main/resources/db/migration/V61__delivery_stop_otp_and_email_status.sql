ALTER TABLE otp_verifications
    ADD COLUMN delivery_stop_id UUID REFERENCES trip_stops(id);

CREATE INDEX idx_otp_delivery_stop_created
    ON otp_verifications(delivery_stop_id, created_at DESC)
    WHERE delivery_stop_id IS NOT NULL;

ALTER TABLE orders
    ADD COLUMN delivery_email_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN delivery_email_sent_at TIMESTAMPTZ,
    ADD COLUMN delivery_email_error VARCHAR(500);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_delivery_email_status
        CHECK (delivery_email_status IN ('NOT_REQUESTED', 'PENDING', 'SENT', 'FAILED'));
