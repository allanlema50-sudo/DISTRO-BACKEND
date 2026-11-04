ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE organizations
    ADD CONSTRAINT chk_organizations_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));
