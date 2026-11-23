ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS location_description VARCHAR(500);

ALTER TABLE warehouses
    ADD CONSTRAINT ck_warehouse_coordinate_pair
    CHECK ((latitude IS NULL) = (longitude IS NULL)
       AND (latitude IS NULL OR latitude BETWEEN -90 AND 90)
       AND (longitude IS NULL OR longitude BETWEEN -180 AND 180)) NOT VALID;
