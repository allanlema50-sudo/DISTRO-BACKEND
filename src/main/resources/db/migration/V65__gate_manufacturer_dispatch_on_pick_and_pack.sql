ALTER TABLE purchase_order_shipments
    DROP CONSTRAINT IF EXISTS ck_purchase_order_shipment_status;

UPDATE purchase_order_shipments shipment
SET status = 'AWAITING_PICKING', updated_at = now()
WHERE shipment.status = 'READY_FOR_DISPATCH'
  AND EXISTS (
      SELECT 1
      FROM purchase_order_items item
      WHERE item.purchase_order_id = shipment.purchase_order_id
        AND (item.picked_quantity <> item.quantity OR item.packed_quantity <> item.quantity)
  );

ALTER TABLE purchase_order_shipments
    ADD CONSTRAINT ck_purchase_order_shipment_status CHECK (
        status IN ('AWAITING_PICKING', 'READY_FOR_DISPATCH', 'IN_TRANSIT', 'DELIVERED', 'RECEIVED', 'CANCELLED')
    ) NOT VALID;
