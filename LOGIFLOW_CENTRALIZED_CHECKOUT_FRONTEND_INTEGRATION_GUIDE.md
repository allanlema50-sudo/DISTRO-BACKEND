# LogiFlow Centralized Checkout — Frontend Integration Guide

Date: 2026-11-17

## Scope and environment

This guide covers distributor-to-supplier checkout across multiple suppliers. The backend creates one checkout and one supplier-specific purchase order per supplier group. The current payment implementation is explicitly DEMO: it simulates a backend-configured result and transfers no money. The existing Daraja API remains a separate real-payment flow for a single purchase order. There is no live multi-supplier split provider, live card processing, or supplier payout integration.

The API is available only when the backend has both marketplace checkout and DEMO payments enabled, the active organization is allowlisted, and the application runs in an allowed non-production profile. A disabled or unauthorized feature returns a standard API error. The frontend must never offer these demo methods as live payment methods.

Base path: `/api/v1`. All routes require the application's normal authenticated bearer/session credentials. Checkout routes require `DISTRIBUTOR_ADMIN` or `DISTRIBUTOR_STAFF`; supplier financial status requires manufacturer credentials for the owning supplier.

## Backend configuration for a demo environment

```properties
SPRING_PROFILES_ACTIVE=local
LOGIFLOW_MARKETPLACE_CHECKOUT_ENABLED=true
LOGIFLOW_DEMO_PAYMENTS_ENABLED=true
LOGIFLOW_DEMO_ORGANIZATION_IDS=<comma-separated distributor organization UUIDs>
LOGIFLOW_DEMO_PAYMENT_OUTCOME=SUCCESS
LOGIFLOW_DEMO_PAYMENT_DELAY_SECONDS=3
LOGIFLOW_DEMO_PAYMENT_POLL_INTERVAL_MS=1000
```

Allowed outcomes are `SUCCESS`, `FAILED`, `CANCELLED`, and `TIMED_OUT`. They are server configuration; no API request or UI control can choose an outcome. The simulator is disabled in production profiles. Payment responses carry `demo: true` and `demoLabel: "DEMO — NO MONEY TRANSFERRED"`.

## Frontend flow

1. Build the cart grouped by supplier. Select an active destination warehouse for each supplier group.
2. Submit all supplier groups once to create a checkout. Send a stable `Idempotency-Key` for that user action and reuse it on retries of the same action. The backend validates the distributor's existing supplier authorization, MOQ, stock, offer terms, and destination warehouse, and calculates prices and totals.
3. Render the returned checkout summary. It contains one independent PO per supplier. Do not calculate authoritative prices or totals in the client.
4. Initiate one demo payment using `DEMO_MPESA` or `DEMO_CARD`. Both are simulation labels only. Do not collect or transmit a card number or M-Pesa PIN for this endpoint.
5. Poll `GET /checkouts/{id}` or `GET /checkouts/{id}/payment` about once per second until payment reaches a terminal state. The usual configured confirmation delay is three seconds. Stop polling when payment becomes `SUCCESS`, `FAILED`, `CANCELLED`, or `TIMED_OUT`; checkout status `UNKNOWN` represents the timeout outcome.
6. On success, show the independently payable/fulfillable supplier orders. Allocations are accounting demonstrations only and each supplier settlement remains `PENDING`. On failure, cancellation, timeout, or buyer cancellation, the backend releases the reserved stock and cancels the child POs.
7. Track each supplier order and shipment separately through the existing purchase-order and shipment APIs. Receiving one shipment does not receive another supplier's shipment.

## Checkout endpoints

| Method and path | Purpose | Success |
| --- | --- | --- |
| `POST /checkouts` | Create multi-supplier checkout | `201 CheckoutResponse` |
| `GET /checkouts?page=0&size=20` | Buyer checkout history | `200 Page<CheckoutResponse>` |
| `GET /checkouts/{checkoutId}` | Checkout, child orders, payment and allocations | `200 CheckoutResponse` |
| `GET /checkouts/{checkoutId}/orders` | Child supplier orders | `200 SupplierOrder[]` |
| `POST /checkouts/{checkoutId}/payment` | Start one DEMO payment attempt | `200 CheckoutResponse` |
| `GET /checkouts/{checkoutId}/payment` | Read payment state | `200 Payment` or `null` before initiation |
| `GET /checkouts/{checkoutId}/allocations` | Read per-supplier demo allocations | `200 Allocation[]` |
| `POST /checkouts/{checkoutId}/cancel` | Cancel an unpaid checkout | `200 CheckoutResponse` |
| `GET /manufacturer/purchase-orders/{purchaseOrderId}/financial-status` | Supplier's checkout allocation and settlement status | `200 SupplierFinancialStatusResponse` |

### Create checkout request

`Idempotency-Key` is required. Use a random UUID or another unique stable key per attempted cart submission. The request groups lines by supplier, and each group has exactly one destination warehouse.

```http
POST /api/v1/checkouts
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: 7a58f9dc-52d8-45c0-8aa8-c7e5c5e69a30
```

```json
{
  "supplierOrders": [
    {
      "supplierOrganizationId": "4df3d4f1-cfff-4f4f-a027-5a7f18140761",
      "destinationWarehouseId": "690cbd7a-24ca-4e5d-a2c7-ad87c011a2b8",
      "items": [
        { "stockItemId": "e27f13be-4f9a-4d43-b4d7-7b0494e4851c", "quantity": 12, "distributorOfferId": null }
      ]
    },
    {
      "supplierOrganizationId": "b61fd211-8a63-4bc1-b729-71757d858489",
      "destinationWarehouseId": "690cbd7a-24ca-4e5d-a2c7-ad87c011a2b8",
      "items": [
        { "stockItemId": "0467b301-e6e6-4bfd-906e-94d9f7f08bb3", "quantity": 4 }
      ]
    }
  ]
}
```

Use `distributorOfferId` only when selecting a specific applicable offer. Send at least two distinct supplier groups, no duplicate supplier groups, at least one item per group, positive quantities, and a warehouse owned by the buyer distributor. Existing authorization and commercial terms still apply.

### Initiate payment

```http
POST /api/v1/checkouts/{checkoutId}/payment
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: 231ec470-eeb1-45f2-b06a-84970e555ed0
```

```json
{ "method": "DEMO_MPESA" }
```

The supported values are `DEMO_MPESA` and `DEMO_CARD`. The optional `Idempotency-Key` header (or `idempotencyKey` request property) is accepted for client retry tracking. The backend permits one payment attempt for this checkout. The initial response is pending; the configured simulator determines its later outcome.

## Response shape

`CheckoutResponse` contains:

```json
{
  "id": "d5e63cb6-54ed-4b00-b2c4-5cc256a2de21",
  "checkoutReference": "CHK-ABC123DEF4567890",
  "status": "PAYMENT_PENDING",
  "totalAmount": 125000.00,
  "currency": "KES",
  "buyerOrganizationId": "<buyer-org-uuid>",
  "buyerOrganizationName": "Example Distributor",
  "demo": true,
  "demoLabel": "DEMO — NO MONEY TRANSFERRED",
  "payment": {
    "id": "<payment-uuid>", "method": "DEMO_MPESA", "status": "PENDING",
    "providerReference": "DEMO-PENDING-<uuid>", "demo": true,
    "demoLabel": "DEMO — NO MONEY TRANSFERRED",
    "initiatedAt": "2026-11-17T12:00:00Z", "confirmedAt": null
  },
  "supplierOrders": [
    {
      "purchaseOrderId": "<po-uuid>", "purchaseOrderNumber": "PO-...",
      "supplierOrganizationId": "<supplier-org-uuid>", "supplierOrganizationName": "Supplier A",
      "destinationWarehouseId": "<warehouse-uuid>", "status": "APPROVED",
      "totalAmount": 75000.00, "currency": "KES",
      "items": [{ "productId": "<stock-item-uuid>", "productName": "Product", "sku": "SKU-1",
        "imageUrl": null, "quantity": 3, "unitPrice": 25000.00, "lineTotal": 75000.00 }]
    }
  ],
  "allocations": []
}
```

The illustration abbreviates IDs and lists one supplier order; actual responses contain all orders. `imageUrl` may be null. After successful demo payment, each allocation reports gross, provider fee, commission, supplier amount, currency, and settlement status. In DEMO the fee and commission are zero, supplier amount equals the order total, and settlement status remains `PENDING` with a note stating that no payout was made.

## States and UI mapping

| Field | Values | Suggested UI |
| --- | --- | --- |
| Checkout `status` | `PAYMENT_PENDING`, `PAID`, `PAYMENT_FAILED`, `CANCELLED`, `UNKNOWN` | Awaiting payment, paid, failed, cancelled, or timed out/unknown |
| Payment `status` | `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `TIMED_OUT`, `UNKNOWN` | Poll while pending; show a terminal result otherwise |
| Payment `method` | `DEMO_MPESA`, `DEMO_CARD` | Always display a DEMO badge |
| Supplier settlement `settlementStatus` | `PENDING`, `PROCESSING`, `SETTLED`, `FAILED`, `REVERSED` | Show as separate from buyer payment; DEMO remains pending |
| PO `status` | `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `FULFILLED` | Track independently per supplier |
| Shipment status | `READY_FOR_DISPATCH`, `IN_TRANSIT`, `DELIVERED`, `RECEIVED`, `CANCELLED` | Track separately per PO/shipment |

A `PAID` checkout does not mean suppliers have been paid. Supplier payout confirmation is not implemented. In real mode, do not infer a provider or payment capability from this DEMO API.

## Continue fulfillment with existing APIs

Distributor purchase-order list/detail: `GET /purchase-orders`, `GET /purchase-orders/{purchaseOrderId}`. Manufacturer list/detail and decisions: `GET /manufacturer/purchase-orders`, `GET /manufacturer/purchase-orders/{purchaseOrderId}`, `PATCH /manufacturer/purchase-orders/{purchaseOrderId}/decision`. The supplier can query its financial status from the endpoint above.

Shipment APIs remain per supplier order: manufacturer creates with `POST /manufacturer/shipments`, updates with `PATCH /manufacturer/shipments/{shipmentId}/status`; distributor reads `GET /purchase-order-shipments` and `GET /purchase-order-shipments/{shipmentId}`, then receives with `POST /purchase-order-shipments/{shipmentId}/receive`. Shipment creation for a checkout PO requires the successful centralized checkout payment and its matching allocation. The destination warehouse captured at checkout must be used. Receipt lines identify `purchaseOrderItemId` and report accepted, damaged, and missing quantities.

## Errors

Errors use the shared shape; nullable fields can be omitted:

```json
{
  "timestamp": "2026-11-17T12:00:00Z", "status": 400,
  "code": "VALIDATION_FAILED", "message": "Validation failed",
  "path": "/api/v1/checkouts", "fieldErrors": [{ "field": "supplierOrders", "message": "must not be empty" }]
}
```

Common outcomes include `400` (`BAD_REQUEST`, `VALIDATION_FAILED`), `401` (`UNAUTHENTICATED`), `403` (`ACCESS_DENIED`), `404` (`NOT_FOUND`), `409` (`CONFLICT`, `PAYMENT_STATE_CONFLICT`), and `503` (`PAYMENT_NOT_CONFIGURED`). Display the server message where useful, but branch on stable `code`. A failed/terminal payment cannot be retried as a new attempt on that checkout; create a new checkout after the backend has cancelled the old orders and released reservations.

## Frontend security and correctness notes

- Treat all totals, supplier prices, quantities accepted, statuses, and allocations as server-owned values.
- Never send payment outcomes, provider references, settlement states, or card credentials.
- Keep checkout and payment idempotency keys stable across transport retries; use new keys for a genuinely new action.
- Respect tenant scoping: buyers see their own checkouts; suppliers see only their own purchase-order financial status.
- Do not describe DEMO allocations as payouts, wallet balances, escrow, or split settlement.
- Keep the single-PO Daraja flow separate from this multi-supplier DEMO flow.
