ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS email VARCHAR(150);

CREATE TABLE IF NOT EXISTS access_requests (
    id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    personal_email VARCHAR(150) NOT NULL,
    organization_email VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    organization_name VARCHAR(150) NOT NULL,
    organization_id UUID REFERENCES organizations(id),
    organization_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    activation_token_hash VARCHAR(64),
    activation_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id),
    activated_at TIMESTAMPTZ,
    CONSTRAINT ck_access_request_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'ACTIVATED')
    ),
    CONSTRAINT ck_access_request_org_type CHECK (
        organization_type IN ('MANUFACTURER', 'DISTRIBUTOR')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_access_requests_pending_email
    ON access_requests (LOWER(personal_email))
    WHERE status IN ('PENDING', 'APPROVED');

CREATE UNIQUE INDEX IF NOT EXISTS uq_organization_email
    ON organizations (LOWER(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_access_requests_pending_phone
    ON access_requests (phone_number)
    WHERE status IN ('PENDING', 'APPROVED');

CREATE INDEX IF NOT EXISTS idx_access_requests_status_created
    ON access_requests (status, created_at DESC);

CREATE TABLE IF NOT EXISTS invitations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20),
    role VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_invitation_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT ck_invitation_role CHECK (
        role IN ('MANUFACTURER_STAFF', 'DISTRIBUTOR_STAFF')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_invitation_email_org
    ON invitations (LOWER(email), organization_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_invitations_org_status
    ON invitations (organization_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    reference_id UUID,
    reference_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    CONSTRAINT ck_notification_status CHECK (status IN ('READ', 'UNREAD')),
    CONSTRAINT ck_notification_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_status
    ON notifications (recipient_id, status);
