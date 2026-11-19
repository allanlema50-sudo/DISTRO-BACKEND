ALTER TABLE rider_profiles
    ADD COLUMN IF NOT EXISTS pending_full_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS pending_phone_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pending_license_number VARCHAR(50);
