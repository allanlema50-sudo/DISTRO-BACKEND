-- Schema for the access-request flow and in-app platform-admin notifications.
-- Run after the core users and organizations tables exist.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS access_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    personal_email VARCHAR(150) NOT NULL UNIQUE,
    organization_email VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    organization_name VARCHAR(150) NOT NULL,
    organization_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    activation_token_hash VARCHAR(64),
    activation_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    activated_at TIMESTAMPTZ,
    CONSTRAINT chk_access_request_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'ACTIVATED')
    )
);

ALTER TABLE access_requests
    ADD COLUMN IF NOT EXISTS personal_email VARCHAR(150),
    ADD COLUMN IF NOT EXISTS organization_email VARCHAR(150),
    ADD COLUMN IF NOT EXISTS activation_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS activation_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_by UUID,
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ;

-- Upgrade installations created by the alternate dashboard branch, which used
-- a single `email` plus a caller-supplied `requested_role` column.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'access_requests'
          AND column_name = 'email'
    ) THEN
        EXECUTE 'UPDATE access_requests SET personal_email = email WHERE personal_email IS NULL';
        EXECUTE 'ALTER TABLE access_requests ALTER COLUMN email DROP NOT NULL';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'access_requests'
          AND column_name = 'requested_role'
    ) THEN
        EXECUTE 'ALTER TABLE access_requests ALTER COLUMN requested_role DROP NOT NULL';
    END IF;
END $$;

UPDATE access_requests
SET organization_email = COALESCE(organization_email, personal_email)
WHERE organization_email IS NULL;
ALTER TABLE access_requests ALTER COLUMN personal_email SET NOT NULL;
ALTER TABLE access_requests ALTER COLUMN organization_email SET NOT NULL;

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS access_request_id UUID UNIQUE;
ALTER TABLE organizations
    DROP CONSTRAINT IF EXISTS fk_organizations_access_request;
ALTER TABLE organizations
    ADD CONSTRAINT fk_organizations_access_request
    FOREIGN KEY (access_request_id) REFERENCES access_requests(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_organizations_access_request
    ON organizations (access_request_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_access_requests_personal_email
    ON access_requests (personal_email);
CREATE UNIQUE INDEX IF NOT EXISTS uq_access_requests_phone_number
    ON access_requests (phone_number);
CREATE INDEX IF NOT EXISTS idx_access_requests_status
    ON access_requests (status);
CREATE INDEX IF NOT EXISTS idx_access_requests_created_at
    ON access_requests (created_at DESC);

ALTER TABLE access_requests
    DROP CONSTRAINT IF EXISTS fk_access_request_reviewer;
ALTER TABLE access_requests
    ADD CONSTRAINT fk_access_request_reviewer
    FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
    CONSTRAINT chk_notification_status CHECK (status IN ('READ', 'UNREAD')),
    CONSTRAINT chk_notification_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient
    ON notifications (recipient_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status
    ON notifications (status);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at
    ON notifications (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_reference
    ON notifications (reference_id);
