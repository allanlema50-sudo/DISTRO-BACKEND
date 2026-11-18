ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS capacity_m3 NUMERIC(10, 2);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_vehicles_capacity_m3'
    ) THEN
        ALTER TABLE vehicles
            ADD CONSTRAINT ck_vehicles_capacity_m3
            CHECK (capacity_m3 IS NULL OR capacity_m3 > 0);
    END IF;
END $$;
