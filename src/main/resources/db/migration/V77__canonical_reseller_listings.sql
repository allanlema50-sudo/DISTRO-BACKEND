CREATE TABLE IF NOT EXISTS reseller_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_organization_id UUID NOT NULL REFERENCES organizations(id),
    manufacturer_product_id UUID NOT NULL REFERENCES stock_items(id),
    retail_price NUMERIC(12,2) NOT NULL CHECK (retail_price > 0),
    listing_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    quantity_on_hand INTEGER NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity_on_hand),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_reseller_listing_seller_product UNIQUE (distributor_organization_id, manufacturer_product_id)
);
CREATE INDEX IF NOT EXISTS idx_reseller_listings_catalog ON reseller_listings(listing_status, manufacturer_product_id);
