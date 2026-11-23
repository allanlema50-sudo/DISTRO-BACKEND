ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS purchase_order_shipment_group_id UUID;

ALTER TABLE trip_stops
    ADD CONSTRAINT fk_trip_stops_purchase_order_shipment_group
    FOREIGN KEY (purchase_order_shipment_group_id)
    REFERENCES purchase_order_shipment_groups(id) NOT VALID;

CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_stop_manufacturer_shipment_group
    ON trip_stops(purchase_order_shipment_group_id)
    WHERE purchase_order_shipment_group_id IS NOT NULL;
