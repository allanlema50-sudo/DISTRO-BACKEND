-- Keep prior payment attempts for audit while allowing retries after a confirmed terminal outcome.
ALTER TABLE checkout_payments DROP CONSTRAINT IF EXISTS checkout_payments_checkout_id_key;
CREATE INDEX idx_checkout_payments_checkout_initiated
    ON checkout_payments(checkout_id, initiated_at DESC);
