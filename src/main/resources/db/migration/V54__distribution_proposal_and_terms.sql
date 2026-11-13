ALTER TABLE distributor_product_authorizations
    ADD COLUMN IF NOT EXISTS target_market VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sales_channels VARCHAR(500),
    ADD COLUMN IF NOT EXISTS geographical_coverage VARCHAR(500),
    ADD COLUMN IF NOT EXISTS distribution_strategy TEXT,
    ADD COLUMN IF NOT EXISTS agreed_wholesale_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS minimum_order_quantity INTEGER;

ALTER TABLE distributor_product_authorizations
    ADD CONSTRAINT chk_authorization_minimum_order_quantity
        CHECK (minimum_order_quantity IS NULL OR minimum_order_quantity > 0);
