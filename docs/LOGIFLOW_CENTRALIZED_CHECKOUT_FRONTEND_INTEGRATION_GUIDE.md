# Centralized Checkout and Payment Integration Guide

## Payment methods

The distributor centralized checkout is served under `/api/v1/checkouts`. Create the checkout using the distributor cart, then initiate payment against that checkout. Use `MPESA_SANDBOX` for Daraja sandbox STK Push. Use `DEMO`, `DEMO_MPESA`, or `DEMO_CARD` only for a simulated payment in an explicitly enabled non-production environment.

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

Keep the production service on its production profile and leave `LOGIFLOW_DEMO_PAYMENTS_ENABLED` unset or set to `false`. Never use the production database for the staging service. If the allowed distributor organization changes, update the UUID allowlist and redeploy. A missing DEMO enable flag produces `503 PAYMENT_NOT_CONFIGURED` with “DEMO checkout payment is disabled”; a buyer outside the allowlist receives an authorization failure.

Deployment steps:

1. Confirm the staging Render service uses the intended staging database and has `SPRING_PROFILES_ACTIVE=staging`.
2. Add the six variables above, replacing the placeholder with the distributor organization UUID. Preserve any existing Daraja sandbox variables on that service.
3. Deploy the latest backend commit (or use Render's manual deploy after saving the environment changes). Wait for a successful build and startup; invalid profiles or an empty/malformed allowlist fail startup.
4. Initiate one DEMO checkout from the allowlisted distributor workspace. The initiation response should be `PENDING` and label the payment `DEMO — NO MONEY TRANSFERRED`; after the configured delay, poll checkout payment status until terminal.
5. For an M-Pesa sandbox test, use `MPESA_SANDBOX` and the existing sandbox credentials/callback configuration. DEMO settings do not change Daraja configuration or initiation.

## Existing payment workflow

Checkout initiation creates a pending payment attempt. For M-Pesa, the backend initiates Daraja STK Push and processes verified callbacks through the existing checkout callback flow. For DEMO, the scheduled simulator confirms or fails that pending attempt according to server configuration. A successful confirmation records checkout allocations and retains the existing purchase order, settlement, and manufacturer fulfilment flow. Allocation is accounting state, not a supplier payout; DEMO settlements must not be represented as money received by suppliers.

For endpoint request and response contracts, use the checkout controller DTOs and the live `/v3/api-docs` schema. The frontend must not send payment outcome, allocation amounts, or a claim of payment success.
