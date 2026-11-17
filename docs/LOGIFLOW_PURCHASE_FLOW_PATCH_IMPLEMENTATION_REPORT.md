# LogiFlow Purchase Flow Patch Implementation Report

Date: 2026-11-17

## Current implementation and changes

The service already persists `PurchaseOrder` and separate `PurchaseOrderSettlement` records, checks product publication and approved distributor authorization, enforces agreed MOQ and available stock, reserves stock, snapshots the wholesale unit price, and calculates line totals server-side. Existing logistics code requires a settled payment before creating the manufacturer shipment, validates the distributor-owned active destination warehouse, and records receipt into the linked offer inventory.

Changed: a purchase created from an approved authorization now starts in `APPROVED` rather than `SUBMITTED`. This reuses the existing payable state and removes the routine manufacturer PO review from the path. The existing legacy decision endpoint is retained for compatibility with any older submitted records. Product authorization approval notification copy now directs users to purchase directly. Purchase and shipment responses now include resolved buyer and supplier organization names with a safe fallback.

## Verified endpoints

- Create purchase: `POST /api/v1/purchase-orders` — distributor admin/staff; JWT tenant is buyer. Request `CreatePurchaseOrderRequest`: `supplierOrganizationId`, `items[]` (`stockItemId`, `quantity`, optional `distributorOfferId`). Returns `PurchaseOrderResponse`; HTTP 201. Server prices from approved authorization or master product and rejects unapproved/inactive/out-of-stock items. Destination warehouse is not selected during creation in this implementation; manufacturer selects it when preparing shipment.
- Purchase history/detail: `GET /api/v1/purchase-orders` and `GET /api/v1/purchase-orders/{purchaseOrderId}` — distributor admin/staff, buyer scoped.
- Incoming purchase history/detail: `GET /api/v1/manufacturer/purchase-orders` and `GET /api/v1/manufacturer/purchase-orders/{purchaseOrderId}` — manufacturer admin/staff, supplier scoped.
- M-Pesa initiation: `POST /api/v1/payments/initiate` — distributor role; body uses `orderType: PURCHASE_ORDER`, `orderId`, `phoneNumber`, optional `provider: MPESA`, `idempotencyKey`; returns pending response. Acknowledgement does not mark payment paid.
- M-Pesa callback: `POST /api/v1/payments/mpesa/callback` — provider callback, public route guarded by configured callback secret and Daraja query verification. Correlation IDs and amount/receipt are checked; duplicate settled callbacks are idempotent.
- Payment status: `GET /api/v1/payments/{purchaseOrderId}/status` — authenticated purchasing party; settlement history is available at `GET /api/v1/purchase-orders/{purchaseOrderId}/settlements`.
- Manufacturer shipment creation: `POST /api/v1/manufacturer/shipments`; manufacturer tenant only; requires approved PO and settled amount/currency. Manufacturer shipment status updates use `/api/v1/manufacturer/shipments/{shipmentId}/status`.
- Distributor receipt: `POST /api/v1/purchase-order-shipments/{shipmentId}/receive`; shipment ownership and destination warehouse are checked; accepted inventory is recorded.

See the integration guide for DTO examples, state enums, and the existing shipment/receipt route details. A card checkout/session and card webhook/status endpoint do not exist. No card provider is configured. There is no backend DEMO payment simulator; no frontend-provided success flag is accepted. The M-Pesa integration uses configured Daraja credentials, one configured shortcode and callback setup. No supplier-specific payee routing or manufacturer merchant-account setup exists, so it must not be represented as direct-to-manufacturer settlement. Provider/account owners must resolve routing before production supplier purchases are enabled.

## Environment and reliability findings

M-Pesa configuration is backend-held using `MPESA_BASE_URL`, `MPESA_CONSUMER_KEY`, `MPESA_CONSUMER_SECRET`, `MPESA_SHORTCODE`, `MPESA_PASSKEY`, `MPESA_CALLBACK_URL`, and `MPESA_CALLBACK_SECRET`; callback verification defaults enabled. Configure sandbox credentials and sandbox endpoint for provider tests. A production deployment must use production credentials and a reachable HTTPS callback. DEMO, card, supplier-specific merchant routing, and a separate customer-facing reconciliation operation are not implemented in this patch. Do not enable a simulated success path in production.

## Tests and execution

Attempted `mvnw.cmd -Dtest=!DistroBackendApplicationTests test` and a compile-only wrapper invocation. The checked-in Maven wrapper failed in PowerShell with `Cannot index into a null array` / `Cannot start maven from wrapper`. A system Maven run also failed because Maven attempted to create `C:\\.m2\\repository` outside the writable workspace. Automated test results are therefore unavailable. No endpoint is claimed as newly tested. Existing unit tests include purchase-order, payment, shipment, and authorization service suites; run them with a working Maven installation after repairing or replacing the wrapper.

## Remaining blockers

1. Add destination warehouse selection and persist it on the purchase if it must be chosen before payment (currently chosen at shipment creation).
2. Configure manufacturer-specific payment accounts and verified routing, or explicitly define platform collection and settlement; current Daraja config is global.
3. Integrate a real hosted card provider or implement backend-controlled, staging-only DEMO attempts with auditable outcomes and a production fail-closed guard.
4. Add and run focused tests for direct authorization-to-purchase behavior, organization display fields, initiation/callback verification, duplicate callbacks, and dispatch gating once Maven execution is available.

## Direct flow sequence

```mermaid
sequenceDiagram
    participant D as Distributor
    participant API as LogiFlow API
    participant M as Manufacturer
    participant P as Daraja
    D->>API: Create purchase against approved listing
    API->>API: Validate authorization, stock, MOQ; snapshot price
    API-->>D: APPROVED purchase + pending settlement
    D->>API: Initiate M-Pesa
    API->>P: STK Push
    P-->>D: Handset prompt
    P->>API: Callback
    API->>P: Query and verify callback result
    API->>API: Mark settlement SETTLED
    M->>API: Create shipment for paid purchase
    API-->>M: READY_FOR_DISPATCH
    M->>API: Mark IN_TRANSIT, then DELIVERED
    D->>API: Confirm received quantities
    API->>API: Update distributor warehouse inventory; fulfill purchase
```
