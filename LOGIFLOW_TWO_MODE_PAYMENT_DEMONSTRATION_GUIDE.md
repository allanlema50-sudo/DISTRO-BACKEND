# LogiFlow Two-Mode Payment Demonstration Guide

## Current payment modes

A centralized checkout represents one buyer payment and one child purchase order per supplier group. `POST /api/v1/checkouts` accepts one or more supplier groups. The backend calculates totals and owns authorization, stock reservation, supplier allocation, and fulfilment transitions.

Exactly two payment methods are accepted by checkout payment initiation:

- `MPESA_SANDBOX`: Daraja sandbox STK Push initiated by the backend. This is a simulated provider test transaction; it does not promise a real deduction or a prompt on a personal handset.
- `DEMO`: internal backend simulator. It transfers no money and is labeled `DEMO — NO MONEY TRANSFERRED`.

No live card method is implemented. Neither checkout payment is a supplier payout; successful confirmation creates order allocations and leaves the supplier settlement pending.

## Endpoints

All checkout endpoints require distributor authentication except the provider callback.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/checkouts` | Create a checkout with one or more supplier groups; requires `Idempotency-Key` |
| GET | `/api/v1/checkouts` | List buyer checkout history |
| GET | `/api/v1/checkouts/{checkoutId}` | Read checkout, child orders, payment and allocations |
| POST | `/api/v1/checkouts/{checkoutId}/payment` | Start one checkout payment; header `Idempotency-Key` recommended |
| GET | `/api/v1/checkouts/{checkoutId}/payment` | Poll payment status |
| POST | `/api/v1/checkouts/{checkoutId}/cancel` | Cancel an unpaid checkout |
| POST | `/api/v1/checkouts/mpesa/callback` | Daraja callback; header `X-Mpesa-Callback-Secret` |

Request body for sandbox: `{"method":"MPESA_SANDBOX","phoneNumber":"2547XXXXXXXX"}`. Request body for simulator: `{"method":"DEMO"}`. `phoneNumber` uses Kenyan international digits, with no plus sign. The backend response is `CheckoutResponse`; its `payment` member reports `method`, `status`, `providerReference`, `demo`, `demoLabel`, `initiatedAt`, and `confirmedAt`. Statuses include `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `TIMED_OUT`, and `UNKNOWN`.

## Environment

Set `LOGIFLOW_MARKETPLACE_CHECKOUT_ENABLED=true` to enable checkout. DEMO is disabled by default. Enable it only in local, development, test, or explicitly enabled staging profiles with `LOGIFLOW_DEMO_PAYMENTS_ENABLED=true`, and allowlist distributor UUIDs with `LOGIFLOW_DEMO_ORGANIZATION_IDS`. Set `LOGIFLOW_DEMO_PAYMENT_OUTCOME` to `SUCCESS`, `FAILED`, `CANCELLED`, or `TIMED_OUT`; this backend configuration controls outcomes. `LOGIFLOW_DEMO_PAYMENT_DELAY_SECONDS` controls the processing delay (0–300 seconds). Production profiles reject DEMO.

Daraja settings use `MPESA_BASE_URL`, `MPESA_CONSUMER_KEY`, `MPESA_CONSUMER_SECRET`, `MPESA_SHORTCODE`, `MPESA_PASSKEY`, `MPESA_CALLBACK_URL`, and `MPESA_CALLBACK_SECRET`. Use sandbox credentials and the sandbox host only. The callback URL must route to `/api/v1/checkouts/mpesa/callback`. Never put these values in Angular environment files.

## Lifecycle and demonstration

Create the checkout once with all supplier groups, then initiate one payment for its total. Poll the checkout payment endpoint until the backend returns a terminal status. On confirmed success, the parent becomes `PAID`, allocations are recorded against child supplier orders, and supplier fulfilment can continue independently. Manufacturer access remains limited to that manufacturer's own order. Shipment tracking and distributor receipt continue through the existing order and shipment APIs.

For a DEMO, set one backend outcome, create a checkout whose total reflects the intended demonstration value (for example KSh 10,000, KSh 100,000, or KSh 5,000,000), initiate `DEMO`, and poll for the backend result. On completion, the simulator stores a unique `DEMO-...` reference. It does not contact an external provider or transfer funds. DEMO allocations and settlements remain explicitly marked as simulated.

For Daraja, initiation persists `MerchantRequestID` and `CheckoutRequestID`. The public callback is correlated by `CheckoutRequestID`, checked against the configured callback secret, and reconciled through a Daraja STK query before success is recorded. A callback is not itself treated as proof of payment.
