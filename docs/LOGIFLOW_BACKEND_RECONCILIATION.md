# Logiflow backend reconciliation

**Scope.** This report records the backend audit and implementation in this workspace. The `logiflow/` directory is a read-only copy of the frontend and was not edited. Existing user changes were preserved.

## Architecture observed

The application is a Spring Boot 4 / Java 17 service with PostgreSQL, JPA, Flyway migrations, JWT-authenticated organization users, service-layer business rules, DTO-based REST controllers, and database transactions. Existing integrations include M-Pesa Daraja STK Push/callback handling, OTP/email services, notifications, and trip/driver location workflows. No new dependencies or mock production persistence were introduced.

## Workflow status

| Area | Status | Backend implementation and remaining boundary |
| --- | --- | --- |
| Manufacturer product management | COMPLETE/PARTIAL | Persisted product lifecycle, image metadata, publication/availability and manufacturer inventory exist. Catalog responses intentionally expose public fields. Rich manufacturer discovery/profile fields are limited to the existing organization/catalog contract. |
| Manufacturer discovery and authorization | PARTIAL | Product catalog discovery and persistent product-authorization requests/decisions exist. The application does not have a separate partnership entity/history workflow beyond those authorizations. Purchase-order validation must continue to require approved authorization. |
| Purchase orders | COMPLETE/PARTIAL | Persistent lines, price snapshots, totals, tenant ownership, reservation and cancellation/expiry logic exist. Shipment preparation is linked to approved/settled orders. Duplicate submissions rely on persisted records/locking; no separate client idempotency key contract was found. |
| M-Pesa | PARTIAL | Daraja client, STK initiation, persisted settlement state and callback handling are present. Callback-driven payment state is server-controlled and duplicate callbacks are guarded. Live sandbox execution needs credentials and a reachable callback URL; settlement ownership and organization-specific credentials remain deployment/business decisions. |
| Manufacturer shipment and distributor receipt | COMPLETE/PARTIAL | Purchase-order shipments, status tracking and destination warehouse association exist. Receipt now records accepted, damaged and missing quantities per line, adds accepted quantities only, emits movement references, and is idempotent after receipt. Receipt currently fulfills the PO when the accounted shipment is received, including discrepancy quantities; partial/multiple shipment allocation is not implemented by the existing one-shipment-per-PO model. |
| Driver, vehicle and trips | PARTIAL | Existing driver staff categories, vehicle availability, trip assignment, trip locations, stops, tracking, and delivery OTP flows are persisted. Active assignment validation exists in the workflow. A full live-driver-app end-to-end run was not performed. |
| Delivery OTP | PARTIAL | Existing trip/stop OTP creation and verification, authenticated driver checks, expiry/attempt handling, and delivery status workflow are present. Delivery email depends on configured mail transport. OTP storage/notification behavior should be verified against deployment secrets and mail configuration before production rollout. |
| Distributor inventory | COMPLETE/PARTIAL | Manufacturer stock and distributor offers are separate persisted stock records connected by source-product references. Shipment receipt increases distributor stock. Warehouse locations and receipt discrepancy records have been added. Some dashboard inventory summaries are existing endpoints; a dedicated warehouse operations dashboard aggregate was not found. |
| Distributor catalog and customer orders | PARTIAL | Distributor offers/catalog and customer orders/reservations exist. Customer picking and packing progress are now persisted. A customer checkout spanning multiple distributors is not represented as a single split-checkout aggregate in the existing model. |
| Stock adjustment/history | PARTIAL | Existing stock movement/audit endpoints persist user, reason/note, timestamp and transaction reference. Authorization and movement-type validation are enforced by services. |
| Warehouse operations UI integration | BACKEND READY, FRONTEND PENDING | APIs now support receipt detail, put-away locations, pick/pack progress and trip dispatch inventory deduction. The checked-in frontend warehouse service still mutates local state and does not invoke these APIs. It was left unchanged per instruction. |

## Implemented in this pass

### Receipt reconciliation

`POST /api/v1/purchase-order-shipments/{shipmentId}/receive` accepts `ReceiveShipmentRequest` with a note and optional `lines[]` containing `purchaseOrderItemId`, `acceptedQuantity`, `damagedQuantity`, and `missingQuantity`. For explicit breakdowns, every PO item must occur exactly once, values must be non-negative, and the three values must sum to the ordered quantity. Only accepted quantities increment stock and create `RESTOCK_IN` movements. The older note-only request remains accepted as “all expected quantity accepted.” Repeating an already completed receipt does not add stock again. Response DTO includes receipt lines.

### Put-away and inventory locations

- `GET /api/stock/items/{stockItemId}/locations` returns stored locations and quantities.
- `POST /api/stock/items/{stockItemId}/locations` accepts `PutAwayStockRequest` (`locationCode`, `quantity`) and records or adds a quantity in a normalized location.

The service enforces organization ownership, active warehouse/offer rules, positive quantities and a total assigned quantity no greater than the stock item’s on-hand balance. This implementation represents current location allocation; it does not separately model a receiving/staging bin lifecycle.

### Customer order fulfillment and inventory timing

- `POST /api/v1/orders/{orderId}/warehouse/pick` accepts `PickOrderItemRequest` (`orderItemId`, `pickedQuantity`). Quantities are cumulative, bounded by ordered quantity, and order status advances to `READY_TO_PACK` only when all lines are picked.
- `POST /api/v1/orders/{orderId}/warehouse/pack` accepts `PackOrderRequest` (`packageCount`). It requires complete picking, a positive package count, persists packed quantities, and advances to `AWAITING_DISPATCH`.
- `OrderResponse` and `OrderItemResponse` now expose package count and picked/packed quantities.
- `POST /api/v1/trips/{tripId}/dispatch` dispatches the existing trip and commits reserved order quantities transactionally for its order stops. Dispatch records `SALE_OUT` movements referencing the customer order. Location balances are reduced FIFO so recorded locations do not exceed on-hand stock. The generic order status endpoint cannot bypass warehouse fulfillment or dispatch steps.

## Database changes

New Flyway migrations:

| Migration | Changes |
| --- | --- |
| `V49__warehouse_metadata_and_vehicle_driver_assignment.sql` | Adds optional warehouse address metadata and a unique vehicle assigned-driver reference. |
| `V50__persist_order_picking_and_packing.sql` | Adds package count and picked/packed quantities with check constraints. |
| `V51__stock_storage_locations.sql` | Adds `stock_locations`, uniqueness per stock item/location and positive quantity constraint. |
| `V52__purchase_order_shipment_receipt_details.sql` | Adds per-line accepted/damaged/missing receipt details with unique and quantity consistency constraints. |

New/extended persistence models include `StockLocation`, `PurchaseOrderShipmentReceiptLine`, `Order.packageCount`, and `OrderItem.pickedQuantity`/`packedQuantity`. Existing stock movement, trip, order, warehouse and shipment models remain the source of truth.

## Endpoint inventory for this pass

| Method | Path | Purpose | Request / response |
| --- | --- | --- | --- |
| `POST` | `/api/v1/purchase-order-shipments/{shipmentId}/receive` | Confirm goods receipt and discrepancies | `ReceiveShipmentRequest` / `PurchaseOrderShipmentResponse` |
| `GET` | `/api/stock/items/{stockItemId}/locations` | Read location balances | none / `List<StockLocationResponse>` |
| `POST` | `/api/stock/items/{stockItemId}/locations` | Put away stock | `PutAwayStockRequest` / `StockLocationResponse` |
| `POST` | `/api/v1/orders/{orderId}/warehouse/pick` | Record picked quantity | `PickOrderItemRequest` / `OrderResponse` |
| `POST` | `/api/v1/orders/{orderId}/warehouse/pack` | Complete packing | `PackOrderRequest` / `OrderResponse` |
| `POST` | `/api/v1/trips/{tripId}/dispatch` | Dispatch trip and deduct reserved order stock once | Existing dispatch request / trip response |

Other existing workflow APIs (catalog, authorization, purchase orders, settlement/payment, manufacturer shipments, stock movements/audits, trips, driver, vehicles and OTP) were retained. Their complete route definitions and DTO validation are in the respective controller and DTO source files; this change did not rename or replace those APIs.

## Security and integrity

The added operations use authenticated organization context and service-level tenant checks; caller-supplied organization IDs are not used to establish ownership. Receipt, put-away, pick, pack and dispatch execute in transactions. Database row locks are used where inventory/order rows are modified. Receipt and dispatch have persistent state guards against repeated deductions/additions. Quantity bounds and state transitions are validated in service code and migrations.

## Verification

- `.\mvnw.cmd -Dtest=OrderServiceTest,TripServiceTest,StockLocationServiceTest,PurchaseOrderShipmentServiceTest test` — **20 tests passed**.
- `.\mvnw.cmd -Dtest=!DistroBackendApplicationTests test` — **122 tests passed**, zero failures/errors.
- Full default test execution previously reached the Spring application-context test but could not connect to the configured PostgreSQL server. Database-backed migration and end-to-end verification therefore remain outstanding; the suite excluding that context test passed.
- Frontend build had been run earlier in the session and passed with a Leaflet CommonJS optimization warning. No frontend source was edited, and no frontend build was rerun after the read-only constraint was clarified.

## Remaining dependencies and limitations

1. Provide a PostgreSQL instance and run Flyway migrations against a copy of the target schema before deployment; database migration execution was unavailable in this environment.
2. Provide Daraja sandbox credentials, callback reachability, and deployment secrets to verify STK/callback end to end. Define settlement ownership and credential ownership before enabling live transactions.
3. Configure the email provider for shipment OTP and notification delivery.
4. Wire the read-only frontend’s warehouse views/actions to the APIs above in a later frontend-authorized task. Until then, its warehouse operations are local-only and will not persist through this backend.
5. The current purchase-order shipment model supports one shipment per purchase order and full-order receipt accounting; true partial/multiple shipments need an explicit shipment-line allocation model and policy decision.
6. A persistent distribution partnership lifecycle, multi-distributor split checkout, and integration-level concurrency tests remain gaps relative to the full brief.

