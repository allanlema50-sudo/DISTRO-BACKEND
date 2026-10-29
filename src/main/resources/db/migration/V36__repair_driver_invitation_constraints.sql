-- Driver invitations may be resent, so multiple historical invitation rows
-- may reference the same invited user. Only the latest row is used for lookup.
DROP INDEX IF EXISTS uk_invitations_invited_user;

CREATE INDEX IF NOT EXISTS idx_invitations_invited_user_created
    ON invitations(invited_user_id, created_at DESC)
    WHERE invited_user_id IS NOT NULL;

ALTER TABLE invitations
    DROP CONSTRAINT IF EXISTS ck_invitation_role;

ALTER TABLE invitations
    ADD CONSTRAINT ck_invitation_role CHECK (
        role IN (
            'MANUFACTURER_ADMIN', 'MANUFACTURER_STAFF',
            'DISTRIBUTOR_ADMIN', 'DISTRIBUTOR_STAFF',
            'DRIVER'
        )
    );
