-- Organization administrators may delegate administration within their own
-- organization. The service layer still enforces same-organization/type rules.
-- Keep this as a separate migration so databases that already applied V11 do
-- not require editing an applied Flyway migration.
ALTER TABLE invitations
    DROP CONSTRAINT IF EXISTS ck_invitation_role;

ALTER TABLE invitations
    ADD CONSTRAINT ck_invitation_role CHECK (
        role IN (
            'MANUFACTURER_ADMIN', 'MANUFACTURER_STAFF',
            'DISTRIBUTOR_ADMIN', 'DISTRIBUTOR_STAFF'
        )
    );
