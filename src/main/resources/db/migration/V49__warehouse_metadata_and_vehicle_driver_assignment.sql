ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS city VARCHAR(60),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS site_manager VARCHAR(80);

ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS assigned_driver_id UUID REFERENCES users(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_vehicles_assigned_driver
    ON vehicles(assigned_driver_id)
    WHERE assigned_driver_id IS NOT NULL;
