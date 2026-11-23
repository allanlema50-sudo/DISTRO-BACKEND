# Paystack checkout integration

This branch adds Paystack as a provider for the existing distributor
marketplace checkout. It does not replace the internal ledger and it does not
execute supplier payouts. A successful Paystack payment creates the same
allocations and pending supplier settlement records as a verified M-Pesa
checkout.

## Configuration

Keep these values in Render or another secret manager, not in Git:

```dotenv
PAYSTACK_ENABLED=true
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
