CREATE SEQUENCE trip_delivery_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE trips ADD COLUMN delivery_number VARCHAR(20);

WITH numbered_trips AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS number
    FROM trips
)
UPDATE trips AS trip
SET delivery_number = 'DEL-' || LPAD(numbered_trips.number::text, 6, '0')
FROM numbered_trips
WHERE trip.id = numbered_trips.id;

SELECT setval('trip_delivery_number_seq', GREATEST(COUNT(*) + 1, 1), false)
FROM trips;

ALTER TABLE trips ALTER COLUMN delivery_number SET NOT NULL;
ALTER TABLE trips ADD CONSTRAINT uk_trips_delivery_number UNIQUE (delivery_number);
