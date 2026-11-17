ALTER TABLE users
    ADD COLUMN job_title VARCHAR(100),
    ADD COLUMN base_warehouse_id UUID REFERENCES warehouses(id);

ALTER TABLE invitations
    ADD COLUMN job_title VARCHAR(100),
    ADD COLUMN base_warehouse_id UUID REFERENCES warehouses(id);
