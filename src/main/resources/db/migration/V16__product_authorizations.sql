CREATE TABLE distributor_product_authorizations (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES stock_items(id),
    manufacturer_organization_id UUID NOT NULL REFERENCES organizations(id),
    distributor_organization_id UUID NOT NULL REFERENCES organizations(id),
    status VARCHAR(20) NOT NULL,
    requested_by UUID NOT NULL REFERENCES users(id),
    decided_by UUID REFERENCES users(id),
    decision_note TEXT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_authorization_product_distributor
        UNIQUE (product_id, distributor_organization_id),
    CONSTRAINT ck_product_authorization_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED', 'REJECTED')),
    CONSTRAINT ck_product_authorization_distinct_organizations
        CHECK (manufacturer_organization_id <> distributor_organization_id)
);

CREATE INDEX idx_product_authorizations_distributor_created
    ON distributor_product_authorizations(distributor_organization_id, created_at DESC);

CREATE INDEX idx_product_authorizations_manufacturer_created
    ON distributor_product_authorizations(manufacturer_organization_id, created_at DESC);
