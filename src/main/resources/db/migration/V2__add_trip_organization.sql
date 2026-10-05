ALTER TABLE trips
    ADD COLUMN organization_id UUID REFERENCES organizations(id);

CREATE INDEX idx_trips_organization_status
    ON trips(organization_id, status);
