# Centralized Checkout and Payment Integration Guide

## Payment methods

The distributor centralized checkout is served under `/api/v1/checkouts`. Create the checkout using the distributor cart, then initiate payment against that checkout. Use `MPESA_SANDBOX` for Daraja sandbox STK Push. Use `DEMO`, `DEMO_MPESA`, or `DEMO_CARD` only for a simulated payment in an explicitly enabled, organization-allowlisted environment.

DEMO uses the backend's existing scheduled simulator. The client can initiate a pending payment, but cannot choose its outcome or report success. The server applies the configured outcome after the configured delay, then runs the existing checkout confirmation and allocation workflow. Successful DEMO allocations and supplier settlement records remain marked simulated; supplier settlements remain `PENDING`. DEMO does not contact Daraja or transfer money. Responses identify the payment as `DEMO — NO MONEY TRANSFERRED`.

## Configuration source and safety

`application.properties` maps these Render environment variables to the Spring properties:

| Render environment variable | Spring property | Default | Purpose |
| --- | --- | --- | --- |
| `LOGIFLOW_MARKETPLACE_CHECKOUT_ENABLED` | `logiflow.marketplace-checkout.enabled` | `false` | Enables centralized checkout endpoints |
| `LOGIFLOW_DEMO_PAYMENTS_ENABLED` | `logiflow.demo-payments.enabled` | `false` | Enables backend-simulated DEMO payment |
| `LOGIFLOW_DEMO_ORGANIZATION_IDS` | `logiflow.demo-payments.allowed-organization-ids` | empty | Comma-separated distributor organization UUID allowlist |
| `LOGIFLOW_DEMO_PAYMENT_OUTCOME` | `logiflow.demo-payments.outcome` | `SUCCESS` | Server-selected outcome: `SUCCESS`, `FAILED`, `CANCELLED`, or `TIMED_OUT` |
| `LOGIFLOW_DEMO_PAYMENT_DELAY_SECONDS` | `logiflow.demo-payments.confirmation-delay-seconds` | `3` | Simulator delay, from 0 to 300 seconds |
| `LOGIFLOW_DEMO_PAYMENT_POLL_INTERVAL_MS` | `logiflow.demo-payments.poll-interval-ms` | `1000` | Background simulator poll interval |

When DEMO is enabled, startup requires a non-empty list of valid organization UUIDs and an allowed profile (`local`, `dev`, `development`, `staging`, or `test`). A `prod` or `production` profile rejects DEMO configuration at startup; request-time authorization independently rejects production and organizations outside the allowlist. These checks are backend enforced and cannot be overridden by request data.

## Render demonstration or staging setup

Set these variables on a separate Render demonstration/staging service, using the UUID of the distributor organization that is allowed to run checkout:

```text
SPRING_PROFILES_ACTIVE=staging
LOGIFLOW_MARKETPLACE_CHECKOUT_ENABLED=true
LOGIFLOW_DEMO_PAYMENTS_ENABLED=true
LOGIFLOW_DEMO_ORGANIZATION_IDS=<DISTRIBUTOR_ORGANIZATION_UUID>
LOGIFLOW_DEMO_PAYMENT_OUTCOME=SUCCESS
LOGIFLOW_DEMO_PAYMENT_DELAY_SECONDS=3
```

Keep `LOGIFLOW_DEMO_PAYMENTS_ENABLED=false` unless a controlled test is explicitly required. Never use the production database for a demo test. If the flag is enabled, update the UUID allowlist and redeploy; a missing flag produces `503 PAYMENT_NOT_CONFIGURED` with “DEMO checkout payment is disabled”, while a buyer outside the allowlist receives an authorization failure.

Deployment steps:

1. Confirm the staging Render service uses the intended staging database and has `SPRING_PROFILES_ACTIVE=staging`.
2. Add the six variables above, replacing the placeholder with the distributor organization UUID. Preserve any existing Daraja sandbox variables on that service.
3. Deploy the latest backend commit (or use Render's manual deploy after saving the environment changes). Wait for a successful build and startup; invalid profiles or an empty/malformed allowlist fail startup.
4. Initiate one DEMO checkout from the allowlisted distributor workspace. The initiation response should be `PENDING` and label the payment `DEMO — NO MONEY TRANSFERRED`; after the configured delay, poll checkout payment status until terminal.
5. For an M-Pesa sandbox test, use `MPESA_SANDBOX` and the existing sandbox credentials/callback configuration. DEMO settings do not change Daraja configuration or initiation.

## Existing payment workflow

Checkout initiation creates a pending payment attempt. For M-Pesa, the backend initiates Daraja STK Push and processes verified callbacks through the existing checkout callback flow. For DEMO, the scheduled simulator confirms or fails that pending attempt according to server configuration. A successful confirmation records checkout allocations and retains the existing purchase order, settlement, and manufacturer fulfilment flow. Allocation is accounting state, not a supplier payout; DEMO settlements must not be represented as money received by suppliers.

For endpoint request and response contracts, use the checkout controller DTOs and the live `/v3/api-docs` schema. The frontend must not send payment outcome, allocation amounts, or a claim of payment success.

## Driver delivery OTP confirmation

After staff dispatches a delivery trip through `POST /api/v1/trips/{tripId}/dispatch`, the backend creates and sends a separate six-digit delivery code for each order stop. Codes are hashed in `otp_verifications`, tied to `delivery_stop_id`, expire after the configured OTP TTL, allow a configured number of attempts, and invalidate an earlier pending code when reissued. The recipient can request a resend with `POST /api/v1/trips/{tripId}/stops/{stopId}/delivery-otp` (or `POST /api/v1/orders/{orderId}/delivery-otp` for the active stop). The customer shares the code with the driver in person; the customer does not submit the completion request.

The authenticated assigned driver submits the code from the Driver App:

```http
POST /api/v1/trips/{tripId}/stops/{stopId}/confirm
Authorization: Bearer <driver JWT>
Content-Type: application/json
```

```json
{"otp":"123456"}
```

The server checks driver assignment, active stop, stop-specific code, expiry, and attempt count before setting the stop and order delivered. A linked standalone shipment is also marked delivered. The response is the updated `TripResponse`; invalid codes return the normal API error envelope. A successful delivery attempts an email to the order's customer email with its order reference and delivery address. `OrderResponse.deliveryEmailStatus` is `NOT_REQUESTED`, `PENDING`, `SENT`, or `FAILED`, with `deliveryEmailSentAt` populated on success. Email failure does not reverse delivery.

The manufacturer purchase-order shipment/receipt workflow is a separate domain path; it does not yet create this OTP-protected driver stop automatically. The app should continue using `/api/v1/purchase-order-shipments/{shipmentId}/receive` for that warehouse receipt workflow.
