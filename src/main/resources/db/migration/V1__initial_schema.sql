CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) UNIQUE,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    organization_id UUID REFERENCES organizations(id),
    is_phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_organization_id ON users(organization_id);

CREATE TABLE otp_verifications (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    destination VARCHAR(150) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    max_attempts SMALLINT NOT NULL DEFAULT 5,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_otp_user_purpose_status
    ON otp_verifications(user_id, purpose, status, created_at DESC);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_revoked
    ON refresh_tokens(user_id, revoked);

CREATE TABLE stock_items (
    id UUID PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(80),
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    reorder_threshold INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    stock_item_id UUID NOT NULL REFERENCES stock_items(id),
    movement_type VARCHAR(255) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    performed_by UUID REFERENCES users(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_movements_item_created
    ON stock_movements(stock_item_id, created_at DESC);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(255) NOT NULL,
    delivery_address TEXT,
    delivery_lat DOUBLE PRECISION,
    delivery_lng DOUBLE PRECISION,
    subtotal_amount NUMERIC(12, 2) NOT NULL,
    delivery_fee NUMERIC(12, 2) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    cancellation_reason TEXT,
    placed_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_customer_status ON orders(customer_id, status);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    stock_item_id UUID NOT NULL REFERENCES stock_items(id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    stock_check_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    from_status VARCHAR(255),
    to_status VARCHAR(255) NOT NULL,
    changed_by UUID REFERENCES users(id),
    note TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_status_history_order_changed
    ON order_status_history(order_id, changed_at DESC);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    method VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    mpesa_checkout_request_id VARCHAR(100) UNIQUE,
    mpesa_merchant_request_id VARCHAR(100),
    mpesa_receipt_number VARCHAR(50),
    phone_number VARCHAR(20),
    callback_raw_payload JSONB,
    failure_reason TEXT,
    reconciled_by UUID REFERENCES users(id),
    reconciled_note TEXT,
    reconciled_at TIMESTAMPTZ,
    initiated_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rider_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    vehicle_type VARCHAR(50),
    vehicle_reg_number VARCHAR(30),
    license_number VARCHAR(50),
    is_available BOOLEAN NOT NULL DEFAULT FALSE,
    current_lat DOUBLE PRECISION,
    current_lng DOUBLE PRECISION,
    last_location_at TIMESTAMPTZ
);

CREATE TABLE trips (
    id UUID PRIMARY KEY,
    trip_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    order_id UUID REFERENCES orders(id),
    stock_item_id UUID REFERENCES stock_items(id),
    restock_quantity INTEGER,
    rider_id UUID REFERENCES users(id),
    origin_lat DOUBLE PRECISION,
    origin_lng DOUBLE PRECISION,
    destination_lat DOUBLE PRECISION,
    destination_lng DOUBLE PRECISION,
    estimated_eta TIMESTAMPTZ,
    assigned_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trips_rider_status ON trips(rider_id, status);

CREATE TABLE trip_status_history (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    from_status VARCHAR(255),
    to_status VARCHAR(255) NOT NULL,
    changed_by UUID REFERENCES users(id),
    note TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_status_history_trip_changed
    ON trip_status_history(trip_id, changed_at DESC);

CREATE TABLE trip_location_pings (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_location_pings_trip_recorded
    ON trip_location_pings(trip_id, recorded_at DESC);

CREATE TABLE audit_log (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    actor_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    metadata JSONB,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
