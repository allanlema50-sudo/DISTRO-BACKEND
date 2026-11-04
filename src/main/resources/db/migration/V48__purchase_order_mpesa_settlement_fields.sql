ALTER TABLE purchase_order_settlements
    ADD COLUMN idempotency_key VARCHAR(100) UNIQUE,
    ADD COLUMN mpesa_checkout_request_id VARCHAR(100) UNIQUE,
    ADD COLUMN mpesa_merchant_request_id VARCHAR(100),
    ADD COLUMN phone_number VARCHAR(20),
    ADD COLUMN failure_reason TEXT,
    ADD COLUMN callback_raw_payload JSONB;
