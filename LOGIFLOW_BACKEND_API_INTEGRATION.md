# LogiFlow backend integration: inventory through delivery

This document covers the established APIs related to this workflow and the backend additions in this implementation. All endpoints require the existing bearer JWT. IDs are UUIDs unless stated otherwise. Page endpoints use Spring `Pageable` query parameters (`page`, `size`, `sort`) and return Spring page objects. Tenant ownership comes from the authenticated organization, not a request-supplied organization ID.

## Existing APIs retained

Authentication, unified checkout, demo payments, M-Pesa STK Push/callbacks, product authorization, manufacturer purchase orders, distributor receipt, driver onboarding, vehicle management, trip location pings, and customer-order OTP delivery remain on their existing APIs. The changes do not replace those workflows. Payment provider endpoints and callback payloads are intentionally not duplicated here; see their existing controllers and DTOs.

## Inventory and warehouse

Manufacturer and distributor staff can use `GET /api/stock/items` and `GET /api/stock/items/{id}` for organization-scoped stock-item data. Manufacturer and distributor staff can use `GET /api/v1/warehouses`, `GET /api/v1/warehouses/{warehouseId}`; manufacturer/distributor administrators create with `POST /api/v1/warehouses`. Warehouse create/update DTOs accept `latitude`, `longitude`, and optional `locationDescription`; coordinates must be supplied as a pair and within geographic ranges.

The existing warehouse association and location APIs remain available:

| Method and URL | Purpose | Authorization and validation |
|---|---|---|
| `POST /api/stock/items/{id}/warehouse/{warehouseId}` | Associate a manufacturer source item with its active warehouse | Manufacturer admin/staff; both item and warehouse must belong to the caller |
| `GET /api/stock/items/{stockItemId}/locations` | List assigned storage-location balances | Manufacturer/distributor admin/staff; item must belong to caller |
| `POST /api/stock/items/{stockItemId}/locations` | Put away already received, unassigned stock into a storage location | Manufacturer/distributor admin/staff; positive quantity, active owned warehouse/location and sufficient not-yet-put-away inventory |

`POST /api/stock/items/{id}/intakes` is new and records an authorized manufacturer production/finished-goods receipt. Example:

```json
{
  "quantity": 120,
  "idempotencyKey": "production-lot-2026-11-23-01",
  "sourceReference": "LOT-2026-11-23-01",
  "note": "Finished production lot"
}
```

Returns `StockIntakeResponse`: `movementId`, `stockItemId`, `warehouseId`, `quantityReceived`, `quantityOnHand`, `reservedQuantity`, `availableQuantity`, `sourceReference`, `status`. A repeated item/idempotency key is treated idempotently. Intake requires that the manufacturer stock item already be associated with its active warehouse.

`GET /api/stock/items/{id}/movements` retrieves paged stock ledger records. `POST /api/stock/items/{id}/movements` uses the existing authorized stock-adjustment contract. Ledger entries include intake, put-away, pick, reservation/release, and dispatch movement metadata. Put-away is a location allocation and does not increment on-hand stock.

## Purchase fulfilment

Existing manufacturer PO reads remain `GET /api/v1/manufacturer/purchase-orders` and `GET /api/v1/manufacturer/purchase-orders/{purchaseOrderId}`. A PO line response contains `stockItemId`, `quantity`, `pickedQuantity`, and `packedQuantity`; the canonical stock ID is the line's inventory reference.

| Method and URL | Request | Result |
|---|---|---|
| `PATCH /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/warehouse/pick` | `{"purchaseOrderItemId":"<uuid>","pickedQuantity":4}` | Updated `PurchaseOrderResponse`; quantity is cumulative and cannot exceed the line quantity or reservation |
| `PATCH /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/warehouse/pack` | `{"packageCount":2,"lines":[{"purchaseOrderItemId":"<uuid>","packedQuantity":4}]}` | Updated PO; every submitted quantity must be picked and packing cannot exceed picking |
| `POST /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/fulfilment/retry` | No body | Retries inventory validation/reservation after a successful checkout or settled direct payment |

Picking requires an approved, fully paid order with active stock reservation. It consumes the relevant storage-location balance and writes a `PICK` ledger entry. Packing records quantities; a PO is ready for dispatch only when every line is fully picked and packed and `packageCount > 0`.

Purchase orders created before payment do not hold stock. After a verified checkout payment allocation or confirmed M-Pesa settlement, all PO lines are locked and validated before reservation. A shortage does not roll back successful payment and records `INVENTORY_EXCEPTION`. `fulfilmentStatus` is returned on the PO response. The manufacturer retry operation re-evaluates verified payments after inventory is added.

## Manufacturer consignment and consolidation

The pre-existing single-PO API is retained: `POST /api/v1/manufacturer/shipments` with `{"purchaseOrderId":"<uuid>","destinationWarehouseId":"<uuid>","trackingNumber":"optional","carrier":"optional"}`. It requires an approved, paid, reserved, fully picked/packed PO, and cannot create a duplicate active consignment.

New consolidation APIs:

| Method and URL | Purpose | Request/response |
|---|---|---|
| `GET /api/v1/manufacturer/shipments/consolidations/suggestions` | List deterministic compatible groups | Same manufacturer, active origin and destination warehouse IDs; no route optimizer is claimed |
| `POST /api/v1/manufacturer/shipments/consolidations` | Create one physical consignment from eligible individual PO consignments | `{"purchaseOrderShipmentIds":["<uuid>","<uuid>"]}`; returns `ShipmentConsolidationResponse` with origin/destination, consignment list, status and routePlanningStatus |
| `GET /api/v1/manufacturer/shipments/consolidations/{groupId}` | Fetch the tenant-owned group and consignments | Manufacturer organization isolation |

Selection is atomic and rejects missing, foreign, paid/reservation/pick/pack-ineligible, already-grouped or origin/destination-incompatible consignments. Grouping currently requires the same destination warehouse. `routePlanningStatus` is `READY` only where both warehouse coordinates exist; otherwise `LOCATION_DATA_REQUIRED`.

Individual PO consignment statuses are `AWAITING_PICKING`, `READY_FOR_DISPATCH`, `IN_TRANSIT`, `DELIVERED`, `RECEIVED`, `CANCELLED`. Creating a consignment does not dispatch it. The group statuses are `CREATED`, `TRIP_ASSIGNED`, `IN_TRANSIT`, `DELIVERED`, `RECEIVED`, `CANCELLED`.

Distributor receipt remains `POST /api/v1/purchase-order-shipments/{shipmentId}/receive` with optional body `{"note":"Received","lines":[{"purchaseOrderItemId":"<uuid>","acceptedQuantity":4,"damagedQuantity":0,"missingQuantity":0}]}`. Receipt records only accepted inventory at the distributor offer and is idempotent after receipt. When all consignments in a group are received, the group is marked `RECEIVED`; an active trip's matching stop is completed and the trip completes when all stops complete.

## Trip integration

The existing trip API remains `POST /api/v1/trips`. For Manufacturer PO consolidation trips, request:

```json
{
  "vehicleId": "<manufacturer-vehicle-uuid>",
  "driverId": "<manufacturer-driver-uuid>",
  "manufacturerShipmentGroupIds": ["<shipment-group-uuid>", "<shipment-group-uuid>"],
  "plannedStart": "2026-11-24T08:00:00+03:00",
  "notes": "North route"
}
```

The response is the existing `TripResponse`, whose stop objects now include `manufacturerShipmentGroupId`, `manufacturerShipmentReference`, and `purchaseOrderNumbers`. Only active approved drivers and active vehicles belonging to the authenticated Manufacturer can be assigned. Shipment groups must be unassigned and share one origin warehouse. Missing coordinate pairs are returned as null; no distance or ETA is fabricated.

Existing operations apply: `GET /api/v1/trips/{tripId}`, `GET /api/v1/trips/{tripId}/stops`, `POST /api/v1/trips/{tripId}/dispatch`, `POST /api/v1/trips/{tripId}/location`, and driver-scoped `GET /api/v1/trips/active`. Dispatch consumes each consignment's reserved source stock through the existing shipment dispatch operation and changes the groups to `IN_TRANSIT`. Customer-order delivery OTP endpoints remain for customer orders; Manufacturer-to-Distributor shipment delivery is confirmed by the Distributor receipt API above, not by customer OTP.

## Fulfilment lifecycle and known scope

`PAYMENT_PENDING` → `INVENTORY_EXCEPTION` or `READY_FOR_PICKING` → `PICKING` → `READY_FOR_DISPATCH` → `AWAITING_PICKING`/consignment creation → group `CREATED` → `TRIP_ASSIGNED` → `IN_TRANSIT` → `DELIVERED` → `RECEIVED`.

Financial payment status is authoritative and independent of inventory availability. Reservation is atomic per PO and is recorded separately from on-hand movements. Picking/packing and trip/receipt transitions are persisted. Trip tracking uses the existing ping API. No external route optimization or estimated time/distance calculation is enabled by this work. This implementation does not add a new transfer API or an inventory reservation query endpoint. The linked group route only consolidates same-destination POs; trips can combine multiple such groups with a shared origin.

## Frontend Codex prompt

```text
Integrate the LogiFlow Angular app with the backend contracts in LOGIFLOW_BACKEND_API_INTEGRATION.md. Preserve the current Angular architecture, styling, routes, auth, and working screens. Do not modify or invent backend behavior. Update manufacturer inventory screens to call stock intake, warehouse association, locations/put-away, and movement history APIs; show on-hand, reserved, and available quantities and distinguish production intake from put-away. Update manufacturer PO details to show payment and fulfilment status, canonical stock item IDs, picking and packing quantities, and fulfilment retry. Update manufacturer shipments to display individual consignment and consolidation groups, origin/destination warehouse coordinates when present, and explicit LOCATION_DATA_REQUIRED state. Add trip creation using manufacturerShipmentGroupIds with organization-owned driver and vehicle choices; render returned ordered stops and shipment references. Keep Distributor receipt as its own accepted/damaged/missing quantity workflow. Keep customer-order OTP delivery separate from distributor warehouse receipt. Reuse existing driver active-trip, stop, and location-ping integrations. Do not show optimized routes, route distances, or ETAs when the API reports missing route data. Add Angular service/component tests for successful flow, payment-success/inventory-shortage state, duplicate retry, authorization errors, and regression of existing order OTP delivery. Do not change frontend API assumptions without confirming them against the actual response interfaces and existing controllers.
```
