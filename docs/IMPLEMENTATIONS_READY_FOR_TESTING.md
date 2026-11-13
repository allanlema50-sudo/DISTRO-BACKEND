# Logiflow backend implementations ready for testing

This document records the backend changes available for frontend integration and end-to-end testing. Backend code is in this repository; the frontend source was used only as a reference and was not changed. The backend unit suite was run on 10 November 2026 and passed **122 tests**. The PostgreSQL application-context test was excluded, so migration and live-database behavior still need environment testing.

## Flow overview

```text
Manufacturer publishes product
  -> Distributor discovers product
  -> Distributor requests product authorization
  -> Manufacturer approves authorization
  -> Distributor creates an offer and submits a purchase order
  -> Manufacturer approves purchase order
  -> Distributor pays the settled amount through M-Pesa
  -> Manufacturer creates shipment and dispatches it
  -> Distributor marks it delivered and confirms received quantities
  -> Accepted stock is added to distributor inventory
  -> Distributor offer becomes visible to customers when available stock is positive
```

## Implemented backend behavior

### Manufacturer discovery and purchase authorization

- Catalog access exposes active, published manufacturer products to distributors.
- The distributor can request authorization for a published product. The manufacturer can approve, reject, or revoke the request; authorization decisions are persisted and the distributor administrator is notified.
- Purchase order creation requires approved authorization, an active/published product, and sufficient available manufacturer stock. The backend rejects duplicate lines and invalid quantities.
- Manufacturer catalog stock is reserved when the purchase order is submitted. Rejection releases the reservation.

### Purchase order, payment, and shipment

- Purchase orders persist their state, parties, line quantities and price snapshots, total, review metadata, and settlement state.
- Manufacturer administrators can approve or reject submitted orders. Rejection requires a reason.
- Existing M-Pesa settlement endpoints and callback handling remain the payment integration. Shipment creation requires an approved purchase order and fully settled payment.
- Manufacturer shipment transitions are validated. Moving a shipment to `IN_TRANSIT` deducts reserved manufacturer stock and records an inventory movement referencing the shipment. Repeating an already completed transition does not deduct stock again.
- Manufacturer can mark a shipment delivered; the distributor confirms receipt. Receipt records accepted, damaged, and missing quantities per purchase order line, and increments distributor inventory only by accepted quantities. A repeated receipt does not add stock again.

### Distributor offer prerequisite

The current shipment contract associates each purchase order line with an existing distributor offer and destination warehouse. For testing, create the distributor offer after authorization and before shipment creation, with zero on-hand stock. Shipment receipt then adds accepted stock to that offer. Customer catalog results exclude offers with no available stock.

The backend therefore supports creating an offer before physical receipt and making it sellable after receipt. If the intended UI creates the offer only after receipt, that sequence needs a further backend change because shipment creation currently requires the offer linkage in advance.

### Warehouse and order operations

- Warehouse records support operational metadata and vehicle/driver assignment fields.
- Stock locations persist put-away quantities and return location records for inventory display.
- Purchase shipment receipt persists per-line receipt details.
- Order picking and packing persist progress and package count, validate state transitions, and prevent over-picking beyond allocated stock.
- Dispatch connects warehouse order handling to the existing trip workflow; stock deduction is server-side and guarded against repeat dispatch.
- Stock adjustments and movement history use stock audit/movement services with a responsible user and transaction references.

### Driver onboarding

- Organization admins use `POST /api/v1/drivers/invitations`; `POST /api/v1/drivers` remains the platform-admin registration flow. Calling the latter as an organization admin explains the reported `403`.
- Backend invitation authorization permits both manufacturer and distributor organization admins to invite a driver.
- Invitation acceptance remains a separate public flow: `GET /api/invitations/{token}`, then `POST /api/invitations/{token}/accept` with `{ "phoneNumber": "...", "password": "..." }`. Driver phone OTP verification is still required before login.
- Invitation email is sent only when mail is enabled and provider credentials/from address are configured. The frontend must use an acceptance route compatible with the invitation URL and API; the existing frontend activation route was identified as incompatible.

## Frontend integration checklist

1. For organization-managed drivers, call `/api/v1/drivers/invitations`, map the invitation response separately from a driver record, and implement token acceptance plus OTP verification.
2. Load distributor catalog and authorization statuses from the API; request authorization and block purchase until approved.
3. Create an authorized distributor offer linked to a warehouse before shipment creation. Keep it unavailable to customers until received stock is available.
4. Submit purchase orders, show manufacturer review state, and start M-Pesa payment only in the supported state. Treat backend settlement status/callback result as authoritative.
5. After settlement, load shipment records and use shipment/trip APIs for dispatch status. On delivery, submit accepted/damaged/missing quantities and refresh stock from the server.
6. Use warehouse APIs for put-away, picking, packing, dispatch, adjustments, and movement history. Do not update inventory locally as the source of truth.
7. Display backend validation and authorization errors, including insufficient stock and invalid state transitions.

The frontend handoff and API contract documents in the reference folder remain useful for exact request/response details; this repository does not contain the authoritative Angular source.

## Testing status and prerequisites

- Backend unit tests: `.\mvnw.cmd -Dtest=!DistroBackendApplicationTests test` — **122 passed, 0 failed**.
- PostgreSQL migration and application-context verification: not run in this test pass. Apply migrations V49 through V53 to a test database and verify ORM/query behavior there.
- M-Pesa sandbox transaction: not verified. Requires Daraja credentials and a reachable callback URL.
- Driver email delivery: not verified. Requires enabled mail configuration and a real provider.
- Full manufacturer-to-distributor-to-customer E2E: not run. Verify stock reservation, settlement, one-time dispatch deduction, receipt increments, and customer catalog availability with separate test organizations.

## Migrations in the current backend working tree

- `V49__warehouse_metadata_and_vehicle_driver_assignment.sql`
- `V50__persist_order_picking_and_packing.sql`
- `V51__stock_storage_locations.sql`
- `V52__purchase_order_shipment_receipt_details.sql`
- `V53__reserve_manufacturer_stock_for_purchase_orders.sql`

Confirm migration history on the target database before deployment; do not apply these manually if Flyway has already recorded them.

