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
PAYSTACK_BASE_URL=https://api.paystack.co
PAYSTACK_SECRET_KEY=sk_test_...
PAYSTACK_WEBHOOK_SECRET=whsec_...
PAYSTACK_CALLBACK_URL=https://your-frontend.example/payments/paystack/complete
```

The application refuses to start with Paystack enabled unless the secret key
and webhook signing secret are present. The API host is restricted to
`https://api.paystack.co`. Use test keys until the full payment and settlement
workflow has been accepted.

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
3. A Super Admin creates and approves a settlement batch. The API refuses
   demo, already batched, unpaid, or beneficiary-less settlements.
4. A Super Admin executes the approved batch. Each transfer has one stable
   reference, so an ambiguous provider response is not retried with a new
   transfer and cannot double-credit a beneficiary.
5. Webhooks are signature-verified and persisted before acknowledgement. A
   bounded background worker processes charge and `transfer.success`,
   `transfer.failed`, and `transfer.reversed` events.
6. Events are deduplicated by payload fingerprint and retried with increasing
   delays. After the configured attempt limit, Super Admins can inspect the
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

The scheduled reconciliation job checks pending Paystack transactions and
processing transfers through the provider's server-side verification APIs.
Requests are bounded per run and each unresolved record gets an increasing
retry delay, so one provider outage does not keep the same oldest records at
the front of every scan. Only provider-final statuses are applied; ongoing,
pending, processing, and queued statuses remain eligible for a later check.

```dotenv
PAYMENT_RECONCILIATION_POLL_INTERVAL_MS=60000
PAYMENT_RECONCILIATION_BATCH_SIZE=25
```

Transfers use the Paystack balance. The business must complete Paystack's
transfer/KYC setup and ensure its balance and transfer approval settings are
ready before enabling this capability.
