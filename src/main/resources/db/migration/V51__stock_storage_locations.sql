CREATE TABLE stock_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,
    location_code VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_stock_location_code UNIQUE (stock_item_id, location_code)
);

CREATE INDEX idx_stock_locations_stock_item ON stock_locations(stock_item_id);
