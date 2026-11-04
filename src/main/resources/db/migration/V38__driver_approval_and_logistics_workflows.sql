ALTER TABLE users
    ADD COLUMN IF NOT EXISTS driver_approval_status VARCHAR(30);

UPDATE users SET driver_approval_status = 'APPROVED' WHERE role = 'DRIVER' AND driver_approval_status IS NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_driver_approval_status
        CHECK (driver_approval_status IS NULL OR driver_approval_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED'));

CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    shipment_number VARCHAR(40) NOT NULL UNIQUE,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    trip_id UUID REFERENCES trips(id),
    created_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    tracking_reference VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_shipments_status CHECK (status IN ('READY', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_shipments_org_status ON shipments(organization_id, status);

CREATE TABLE shipment_status_history (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by UUID NOT NULL REFERENCES users(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shipment_status_history ON shipment_status_history(shipment_id, created_at DESC);

CREATE TABLE shared_loads (
    id UUID PRIMARY KEY,
    proposed_trip_id UUID NOT NULL REFERENCES trips(id),
    candidate_trip_id UUID NOT NULL REFERENCES trips(id),
    proposer_organization_id UUID NOT NULL REFERENCES organizations(id),
    candidate_organization_id UUID NOT NULL REFERENCES organizations(id),
    proposed_by UUID NOT NULL REFERENCES users(id),
    reviewed_by UUID REFERENCES users(id),
    status VARCHAR(30) NOT NULL,
    proposal_note TEXT,
    review_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_shared_loads_status CHECK (status IN ('RECOMMENDED', 'AWAITING_CONFIRMATION', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_shared_loads_distinct_orgs CHECK (proposer_organization_id <> candidate_organization_id),
    CONSTRAINT chk_shared_loads_distinct_trips CHECK (proposed_trip_id <> candidate_trip_id)
);

CREATE INDEX idx_shared_loads_proposer_status ON shared_loads(proposer_organization_id, status);
CREATE INDEX idx_shared_loads_candidate_status ON shared_loads(candidate_organization_id, status);

CREATE TABLE shared_load_confirmations (
    id UUID PRIMARY KEY,
    shared_load_id UUID NOT NULL REFERENCES shared_loads(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    confirmed_by UUID NOT NULL REFERENCES users(id),
    decision VARCHAR(20) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_shared_load_confirmation_org UNIQUE (shared_load_id, organization_id),
    CONSTRAINT chk_shared_load_confirmation_decision CHECK (decision IN ('CONFIRMED', 'DECLINED'))
);

CREATE INDEX idx_shared_load_confirmations_load ON shared_load_confirmations(shared_load_id, created_at);
