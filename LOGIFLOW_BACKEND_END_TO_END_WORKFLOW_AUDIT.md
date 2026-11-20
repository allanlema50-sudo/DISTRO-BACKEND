# LogiFlow Backend End-to-End Workflow Audit

Audit date: 2026-11-19

## Conclusion

The backend contains product discovery/publication, distributor authorization, purchase orders, centralized checkout, payment integrations, manufacturer shipment/warehouse receipt, standalone shipments, multi-stop trips, fleet assignment, tracking, and tenant checks. Delivery OTP confirmation is now stop-specific and driver-submitted; successful confirmation attempts an order-specific receipt email.

The full acceptance workflow is **not complete**. Manufacturer-to-distributor purchase-order shipments still use a separate receipt workflow and do not automatically create a driver trip, dispatch OTP, driver confirmation, or email. Standalone shipments are one-order-per-row; multiple trip stops are route consolidation, not a shared multi-order shipment. Distributor inventory updates through purchase-order receipt, not driver OTP.

## Existing functionality

- Manufacturer master products, publication, distributor offers, authorization requests, and manufacturer decisions are separate persisted flows.
- Centralized checkout creates separate supplier purchase orders and buyer payment allocations. DEMO is a backend scheduled simulation and transfers no money. M-Pesa uses Daraja STK Push and verified callbacks.
- Purchase-order settlements are separate from buyer allocations. Manufacturer PO shipment receipt updates distributor inventory and stock movements.
- `Shipment` can link to at most one customer order and optionally a `Trip`. A `Trip` is a journey with ordered stops and can contain multiple order stops.
- Trip assignment checks approved active driver, active vehicle, and conflicting active assignments. Locations and status histories are persisted.
- Organization-scoped services use authenticated organization IDs. Driver and customer access is checked against trip assignment/order membership.

## Implemented changes

- Migration V59 adds a nullable `delivery_stop_id` reference and index to OTP records, plus delivery email status fields on orders.
- Delivery OTPs use `SecureRandom`, password hashing, configured TTL and attempt limit, resend cooldown, and invalidation of earlier pending codes. Codes are bound to one stop.
- Trip dispatch validates assigned driver/vehicle, dispatches delivery orders, and issues a stop-specific recipient code for each delivery stop.
- `POST /api/v1/trips/{tripId}/stops/{stopId}/confirm` is the authenticated Driver App route. Backend checks assigned driver, active stop, scoped OTP, expiry, attempts, and in-transit order before marking stop/order delivered and advancing trip state.
- A linked standalone shipment advances to `IN_TRANSIT` on dispatch and `DELIVERED` after valid OTP; status history is recorded.
- Customer resend APIs issue the scoped code. The order resend route resolves only an active stop.
- After verification, configured `EmailProviderClient` attempts subject `Your LogiFlow Order Has Arrived` with order reference and delivery address. `OrderResponse` exposes `deliveryEmailStatus` and `deliveryEmailSentAt`. Failure is recorded without reverting delivery.
- Updated the centralized checkout frontend guide with the Driver App contract and PO shipment distinction.

## API contracts

| Purpose | Route / DTO |
| --- | --- |
| Product discovery | `GET /api/v1/products` → `Page<ProductResponse>` |
| Create checkout | `POST /api/v1/checkouts`, `CreateCheckoutRequest`, required `Idempotency-Key` |
| Start payment | `POST /api/v1/checkouts/{checkoutId}/payment`, `InitiateCheckoutPaymentRequest` |
| Checkout callback | `POST /api/v1/checkouts/mpesa/callback`; provider callback and configured secret |
| Create trip | `POST /api/v1/trips`, `TripRequest` |
| Assign driver/vehicle | `PATCH /api/v1/trips/{tripId}/assign`, `TripAssignRequest` |
| Dispatch | `POST /api/v1/trips/{tripId}/dispatch` → `TripResponse`; organization staff |
| Resend OTP by stop | `POST /api/v1/trips/{tripId}/stops/{stopId}/delivery-otp`; customer |
| Resend OTP by order | `POST /api/v1/orders/{orderId}/delivery-otp`; customer; active stop only |
| Driver confirms | `POST /api/v1/trips/{tripId}/stops/{stopId}/confirm`, `ConfirmStopRequest` `{ "otp": "123456" }` → `TripResponse` |
| Track trip | `GET /api/v1/trips/{tripId}/tracking` → authorized location pings |

Use deployed `/v3/api-docs` for complete schemas and legacy aliases. No frontend endpoint can mark delivery complete.

## Lifecycle and entity boundaries

Checkout states include `PAYMENT_PENDING`, `PAID`, `PAYMENT_FAILED`, `CANCELLED`, `UNKNOWN`; attempts include `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `TIMED_OUT`, `UNKNOWN`. DEMO outcome is backend-configured. STK initiation is not payment confirmation: verified callbacks/reconciliation confirm payment. Allocations are accounting records, not supplier payouts. Supplier procurement uses PO settlement and fulfillment gates; do not infer every customer order is paid by centralized checkout.

Checkout, supplier POs, buyer payment attempts/allocations, and customer orders are distinct commercial records. Shipment, trip, and trip stop are distinct physical records. A trip has one driver and one vehicle, and may route multiple order stops. PO shipments are separate PO-to-warehouse records and are not connected to the Driver OTP flow.

The current schema cannot put multiple customer orders in one standalone shipment. Multiple trip stops do not satisfy multi-order consignment grouping.

## Notification, tracking, and inventory

Dispatch sends codes through configured OTP infrastructure to each order customer, assuming the customer is the recipient and has a configured contact channel. Email runs only after OTP verification. Failure status is persisted, but no automatic email retry/outbox exists.

Trip locations and history are persisted and authorized through trip routes. PO shipment receipt credits accepted quantities to distributor offer/warehouse stock and writes stock movements. Driver OTP confirmation does not update PO receipt inventory.

## Gaps and deployment requirements

1. Connect manufacturer PO dispatch, trip assignment, recipient OTP, driver confirmation, PO status, distributor inventory receipt, and confirmation email into one workflow.
2. Add shipment-to-orders association if physical shipments need multiple orders; keep supplier POs and allocations separate.
3. Add durable email retry/outbox if automatic recovery is required.
4. Model warehouse/distributor receiving contacts rather than assuming `Order.customer` is the recipient.
5. Apply V59 and prior migrations on deployment-equivalent PostgreSQL; configure OTP/email delivery and exercise Daraja sandbox callbacks.

## Verification status

Automated test run: `mvn -Dmaven.repo.local=C:/Users/Kabugi/.m2/repository -Dtest=!DistroBackendApplicationTests test` — 146 tests, 0 failures, 0 errors. This includes three focused stop-scoped OTP unit tests. `DistroBackendApplicationTests` was excluded by the existing project test command. PostgreSQL migration execution and external provider delivery are not claimed; no local `psql` or Docker executable was available for a database-backed migration run.
