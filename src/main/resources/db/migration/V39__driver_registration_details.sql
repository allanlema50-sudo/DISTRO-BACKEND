ALTER TABLE rider_profiles
    ADD COLUMN national_id VARCHAR(50),
    ADD COLUMN license_expiry_date DATE,
    ADD COLUMN internal_notes TEXT;

CREATE UNIQUE INDEX uk_rider_profiles_national_id
    ON rider_profiles (LOWER(national_id))
    WHERE national_id IS NOT NULL;
