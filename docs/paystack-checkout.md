# Paystack checkout integration

This branch adds Paystack as a provider for the existing distributor
marketplace checkout and the reviewed supplier payout workflow. It does not
replace the internal ledger. A successful Paystack payment creates the same
allocations and pending supplier settlement records as a verified M-Pesa
checkout; a separate Super Admin workflow can later execute eligible supplier
settlements through Paystack Transfers.

## Configuration

Keep these values in Render or another secret manager, not in Git:

```dotenv
PAYSTACK_ENABLED=true
PAYSTACK_TRANSFERS_ENABLED=false
PAYSTACK_MODE=test
PAYSTACK_BASE_URL=https://api.paystack.co
PAYSTACK_SECRET_KEY=sk_test_...
PAYSTACK_WEBHOOK_SECRET=whsec_...
PAYSTACK_CALLBACK_URL=https://your-frontend.example/payments/paystack/complete
PAYSTACK_RECONCILIATION_SCAN_MS=60000
PAYSTACK_SETTLEMENT_RECONCILIATION_SCAN_MS=60000
PAYSTACK_SETTLEMENT_RECONCILIATION_BATCH_SIZE=100
PAYSTACK_SETTLEMENT_RECONCILIATION_INITIAL_DELAY_MS=300000
PAYSTACK_SETTLEMENT_RECONCILIATION_REPEAT_DELAY_MS=300000
```

The application refuses to start with Paystack enabled unless the secret key
and webhook signing secret are present. `PAYSTACK_MODE=test` requires an
`sk_test_` key, while `PAYSTACK_MODE=live` requires an `sk_live_` key. The API
host is restricted to `https://api.paystack.co`; Paystack selects test or live
transactions from the key. Test mode is suitable for an isolated staging
deployment and database. Do not run test transactions against production data:
the backend still records a successful test webhook as paid and starts normal
fulfilment.

## Checkout flow

1. The distributor creates a checkout with `POST /api/v1/checkouts`.
2. The distributor starts payment with `POST /api/v1/checkouts/{checkoutId}/payment`.
3. The backend initializes the transaction server-side and returns
   `payment.authorizationUrl`, `payment.accessCode`, and
   `payment.providerReference`.
4. Web or mobile clients open the authorization URL or use the Paystack SDK.
   The secret key must never be sent to a client.
5. Paystack calls the public webhook endpoint. The backend verifies the raw
   body signature, correlates the provider reference, checks amount and
   currency, and processes the event idempotently.
6. Only `charge.success` creates allocations and marks the checkout paid.
   Failed or mismatched events never release stock or create a payable amount.
7. If the initialization response or webhook is missed, a scheduled verifier
   checks Paystack by the server-generated reference. It accepts only a verified
   matching amount, currency, and reference. Unresolved attempts remain blocked
   from starting another charge and are retried with backoff. A Paystack
   initialization whose outcome was unknown and whose reference remains absent
   for ten minutes is timed out; the backend never returned its checkout URL to
   the buyer. Verified failed or abandoned attempts also become terminal.

`PAYSTACK_CALLBACK_URL` is only a browser redirect for the user experience; a
user reaching it does not prove payment. The backend changes payment state
from signed webhooks and server-side verification. Direct `MPESA` checkout
uses Daraja, while `PAYSTACK_MPESA` uses Paystack's mobile-money channel. Each
payment attempt selects one provider; do not start both for the same checkout.

Example payment request:

```json
{
  "method": "PAYSTACK",
  "channel": "card",
  "idempotencyKey": "paystack-checkout-<unique-client-key>"
}
```

Supported channel values are `card`, `mobile_money`, `bank_transfer`, `bank`,
`ussd`, `qr`, and `eft`, subject to the channels enabled for the Paystack
account. Convenience methods `PAYSTACK_CARD`, `PAYSTACK_MPESA`,
`PAYSTACK_PESALINK`, and `PAYSTACK_BANK_TRANSFER` map to the corresponding
channels.

## Webhook setup

Configure this URL in the Paystack dashboard:

```text
POST https://your-api.example/api/v1/checkouts/paystack/webhook
```

The endpoint accepts `Paystack-Signature` and supports the legacy
`X-Paystack-Signature` header during provider migration. Do not call this
endpoint from the frontend. Paystack must be able to reach it over HTTPS.

Provider verification is still required before production launch. Confirm the
Paystack account supports the channels and currency needed by LogiFlow, and
complete the provider's business/KYC review before using live keys.

## Supplier settlement flow

Transfers are independently disabled by default. Enabling collection does not
enable payouts. After Paystack approves transfers, set
`PAYSTACK_TRANSFERS_ENABLED=true` and complete the following workflow:

1. An organization administrator submits a beneficiary through
   `POST /api/v1/finance/beneficiaries`. LogiFlow sends the account details to
   Paystack and retains only the provider recipient code and a masked account.
2. A Super Admin reviews the beneficiary. Only an `ACTIVE` beneficiary can be
   attached to a settlement.
3. The distributor confirms receipt of the purchase-order shipment. A settlement
   cannot enter a batch before receipt is confirmed and the configured post-receipt
   hold has elapsed. The hold is controlled by
   `SUPPLIER_SETTLEMENT_DELIVERY_HOLD_DAYS` (default: 7 calendar days; set to `0`
   for immediate release after receipt in a controlled demo).
4. A Super Admin creates and approves a settlement batch. The API refuses demo,
   already batched, unpaid, unreceived, still-held, disputed, or beneficiary-less
   settlements.
5. A Super Admin executes the approved batch. LogiFlow commits each transfer
   attempt before contacting Paystack. If the API response is ambiguous, it
   retains that reference and verifies it; a recovery worker resumes batch
   members that were not yet dispatched. A new reference is created only after
   Paystack confirms a prior attempt failed or was reversed.

An open or uncleared Paystack dispute, or a refund that is still being processed,
blocks batch creation and transfer dispatch. After a beneficiary becomes inactive,
a Super Admin can assign a matching active beneficiary to a failed or reversed
settlement with `PATCH /api/v1/admin/financial/settlements/{settlementId}/beneficiary`
before retrying it. A Paystack `needs-attention` refund remains on hold and is
reconciled using its existing reference; an operator must complete any required
bank-detail action in Paystack rather than starting a second refund.
6. Signed `transfer.success`, `transfer.failed`, and `transfer.reversed`
   webhooks also update the settlement and batch status. Webhooks are stored
   before acknowledgement and processed asynchronously with bounded retries.
   After the configured attempt limit, Super Admins can inspect a
   `DEAD_LETTER` event and manually requeue it through the admin API.

Webhook processing settings:

```dotenv
PAYMENT_WEBHOOK_POLL_INTERVAL_MS=2000
PAYMENT_WEBHOOK_MAX_ATTEMPTS=8
PAYMENT_WEBHOOK_BATCH_SIZE=25
PAYMENT_WEBHOOK_PROCESSING_TIMEOUT=5m
```

Only the parsed event fields and payload fingerprint are retained; raw callback
bodies and signatures are not stored.

Paystack checkout attempts whose initialization response is ambiguous are
verified by their stored transaction reference as well. While verification is
still pending, the attempt remains unresolved and the buyer must not start a
second payment. A confirmed success or failure is applied through the same
idempotent processing path as a webhook. An initialization with an unknown
outcome and no authorization URL is timed out only after Paystack confirms no
transaction exists for its stored reference beyond the bounded verification
window. An accepted checkout remains unresolved until provider verification
returns a conclusive result.

Checkout payment verification cadence is controlled by
`PAYSTACK_RECONCILIATION_SCAN_MS`. Supplier transfer reconciliation has separate
settings: `PAYSTACK_SETTLEMENT_RECONCILIATION_SCAN_MS`,
`PAYSTACK_SETTLEMENT_RECONCILIATION_BATCH_SIZE`,
`PAYSTACK_SETTLEMENT_RECONCILIATION_INITIAL_DELAY_MS`, and
`PAYSTACK_SETTLEMENT_RECONCILIATION_REPEAT_DELAY_MS`. The default initial and
repeat delays are five minutes. Provider timeouts remain unresolved and are
queried again; they never trigger a transfer under a new reference. Transfer
reconciliation runs only when `PAYSTACK_TRANSFERS_ENABLED=true`.

## Refunds

Only a Super Admin can request a full refund for a successful, non-demo Paystack
checkout. The API refuses the request if any distributor settlement is already
batched or paid. Paystack's refund reference is stable, callback events are
handled through the durable inbox, and the reconciliation worker checks pending
refunds when a callback is delayed. A processed refund reverses each original
allocation journal once. The checkout enters `REFUND_PENDING` while Paystack
processes the request, which pauses warehouse fulfilment. A confirmed refund
cancels still-unpicked purchase orders and releases their stock reservations.
Requests are rejected once picking, packing, dispatch, or supplier payout has
started. If Paystack definitively rejects a refund, the payment becomes eligible
for a new Super Admin refund request; each attempt remains in the audit history.
Partial refunds and refunds after supplier payout are not supported by this
workflow.

## Disputes

Paystack `charge.dispute.create`, `charge.dispute.remind`, and
`charge.dispute.resolve` webhooks are stored in the durable inbox and update a
local dispute record. Super Admins can list disputes at
`GET /api/v1/admin/financial/disputes`, filter by `PaymentDisputeStatus`, and
record a review with `PATCH /api/v1/admin/financial/disputes/{providerDisputeId}/review`:

```json
{
  "reviewNote": "Paystack shows the dispute resolved; evidence and delivery record reviewed.",
  "clearForSettlement": true
}
```

`clearForSettlement` is accepted only after Paystack reports `RESOLVED`; this
also requires a nonblank internal review note. Until then, settlement creation,
batch execution, and retries for the payment are blocked. The API does not
submit evidence or resolve cases with Paystack, so time-critical evidence and
provider decisions must still be handled through Paystack's dashboard or a
separately approved dispute-evidence integration. A transfer already initiated
before a dispute was received cannot be cancelled here.

## Internal finance reconciliation report

`GET /api/v1/admin/financial/reconciliation-report?from=<ISO-8601>&to=<ISO-8601>`
returns paginated Paystack checkout records for a required window of at most 31
days (maximum page size 100). Each record checks that allocations sum to the
payment amount, capture journals exist and balance, supplier settlement rows
exist, processed refunds agree with the payment status, and disputes have been
reviewed before payout. A non-empty `issues` array identifies records needing
attention.

This report reconciles LogiFlow's own stored data, including whether confirmed
supplier settlements have balanced payout journals and whether provider-
reversed transfers have corresponding reversal journals. It does not import a
bank settlement statement.

`GET /api/v1/admin/financial/provider-transaction-matches?from=<ISO-8601>&to=<ISO-8601>&page=1&perPage=50`
reads one page from Paystack's transaction-list API and compares each provider
reference with the matching LogiFlow payment. It flags provider-only
transactions, amount/currency mismatches, and status mismatches. The date window
is capped at 31 days and `perPage` at 100. This is a live provider comparison,
not a stored statement import; page through the full range to review all
provider transactions. The Paystack secret key must remain configured only on
the backend.

Transfers use the Paystack balance. The business must complete Paystack's
transfer/KYC setup and ensure its balance and transfer approval settings are
ready before enabling this capability.

Each settlement retry receives a new provider reference and a durable attempt
record committed before the Paystack transfer request is sent. Unknown or
unresolved attempts are verified by their own reference, even when another
attempt has already changed the settlement's overall status.
`GET /api/v1/admin/financial/settlements/{settlementId}/attempts`
shows the attempt number, Paystack reference and transfer code, outcome,
timestamps, and failure reason to Super Admins. Late callbacks update their
matching attempt; a failure callback for an older attempt cannot overwrite the
state of a newer retry. Ledger payout and reversal journals are keyed to the
matched attempt and provider reference. Existing payout history is linked to
its original journal event during migration, so a reversal of a pre-migration
transfer does not create a second payout journal.
