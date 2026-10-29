ALTER TABLE invitations
    ADD COLUMN IF NOT EXISTS invited_user_id UUID;
ALTER TABLE invitations
    ADD COLUMN IF NOT EXISTS driver_license_number VARCHAR(50);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_invitations_invited_user'
    ) THEN
        ALTER TABLE invitations
            ADD CONSTRAINT fk_invitations_invited_user
            FOREIGN KEY (invited_user_id) REFERENCES users(id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_invitations_invited_user
    ON invitations(invited_user_id)
    WHERE invited_user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    registration_number VARCHAR(30) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    make VARCHAR(80),
    model VARCHAR(80),
    capacity_kg INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_vehicles_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    CONSTRAINT ck_vehicles_capacity CHECK (capacity_kg IS NULL OR capacity_kg > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_vehicles_organization_registration
    ON vehicles(organization_id, lower(registration_number));
CREATE INDEX IF NOT EXISTS idx_vehicles_organization_status
    ON vehicles(organization_id, status);

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS vehicle_id UUID REFERENCES vehicles(id);
CREATE INDEX IF NOT EXISTS idx_trips_vehicle_status
    ON trips(vehicle_id, status);

CREATE TABLE IF NOT EXISTS trip_operational_actions (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id),
    action_type VARCHAR(30) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_trip_actions_type CHECK (
        action_type IN ('REPORT_DELAY', 'REROUTE', 'PAUSE', 'RESUME', 'CANCEL')
    )
);

CREATE INDEX IF NOT EXISTS idx_trip_actions_trip_created
    ON trip_operational_actions(trip_id, created_at DESC);
