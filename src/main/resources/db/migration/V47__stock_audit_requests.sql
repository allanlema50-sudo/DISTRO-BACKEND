CREATE TABLE stock_audit_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    scope VARCHAR(30) NOT NULL,
    terminal_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_audit_requests_org_created
    ON stock_audit_requests (organization_id, created_at DESC);
