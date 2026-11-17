CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    address_line TEXT NOT NULL,
    city VARCHAR(100),
    county VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_customer_address_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_customer_address_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_customer_address_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX idx_customer_addresses_user ON customer_addresses(user_id);
CREATE UNIQUE INDEX uk_customer_addresses_one_default
    ON customer_addresses(user_id) WHERE is_default = TRUE;

CREATE TABLE push_device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    device_token VARCHAR(4096) NOT NULL UNIQUE,
    platform VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_push_device_tokens_user ON push_device_tokens(user_id);
