ALTER TABLE organizations
    ADD COLUMN tax_registration_id VARCHAR(100),
    ADD COLUMN timezone VARCHAR(80),
    ADD COLUMN default_warehouse_id UUID REFERENCES warehouses(id);
