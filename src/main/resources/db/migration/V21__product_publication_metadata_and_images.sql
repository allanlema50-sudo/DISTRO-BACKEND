ALTER TABLE stock_items
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN brand VARCHAR(100),
    ADD COLUMN unit VARCHAR(30),
    ADD COLUMN description TEXT;

UPDATE stock_items
SET publication_status = CASE WHEN is_active THEN 'PUBLISHED' ELSE 'DRAFT' END;

ALTER TABLE stock_items
    ADD CONSTRAINT ck_stock_items_publication_status
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'DISCONTINUED'));

CREATE TABLE product_images (
    product_id UUID PRIMARY KEY REFERENCES stock_items(id) ON DELETE CASCADE,
    content_type VARCHAR(100) NOT NULL,
    file_name VARCHAR(255),
    content BYTEA NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_images_size_positive CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);
