# LogiFlow Backend Payment Lifecycle Integration Guide

This guide describes the centralized distributor checkout endpoints in `CheckoutController` and `CentralizedCheckoutService`.

## Initiate payment

`POST /api/v1/checkouts/{checkoutId}/payment` (distributor admin/staff JWT)

Send an `Idempotency-Key` header (preferred) or `idempotencyKey` in the body. M-Pesa requires `phoneNumber` in Kenyan `254XXXXXXXXX` format. DEMO ignores the phone number and is restricted by server configuration.

```json
{"method":"MPESA_SANDBOX","idempotencyKey":"attempt-1","phoneNumber":"254712345678"}
```

Allowed methods are `MPESA_SANDBOX`/`MPESA` and `DEMO` (legacy `DEMO_MPESA`/`DEMO_CARD` aliases remain accepted). The response is the checkout representation. Its `payment` object includes:

```json
{
  "id":"<payment-attempt-uuid>","checkoutReference":"CHK-...","method":"MPESA",
  "status":"PENDING","providerReference":"<checkout-request-id>","demo":false,
  "demoLabel":null,"initiatedAt":"2026-11-19T10:00:00Z","confirmedAt":null,
  "updatedAt":"2026-11-19T10:00:01Z","terminal":false,"retryAllowed":false,
  "userMessage":"Payment is awaiting provider confirmation."
}
```

`id` is the unique attempt reference. Checkout creation remains separate; payment retries use the same checkout ID.

## Read status

`GET /api/v1/checkouts/{checkoutId}/payment` returns the `payment` object above, or `null` if no attempt has been started. `GET /api/v1/checkouts/{checkoutId}` returns the same payment object inside the checkout response. `UNKNOWN` means the result cannot currently be established; it is neither success nor failure.

Attempt states are `PENDING`, `UNKNOWN`, `SUCCESS`, `FAILED`, `CANCELLED`, and `TIMED_OUT`. `SUCCESS`, `FAILED`, `CANCELLED`, and `TIMED_OUT` are terminal. A new attempt is allowed after confirmed `FAILED`, `CANCELLED`, or `TIMED_OUT`, as indicated by `retryAllowed`; `UNKNOWN` and `PENDING` block retries. `SUCCESS` blocks further attempts.

## Provider handling and polling

Daraja callbacks are correlated by attempt UUID and provider request IDs, then checked using the configured STK query endpoint before state changes are accepted. Result code `0` is successful only when the callback amount matches the checkout total. Result code `1032` maps to user cancellation; other verified nonzero results map to failure. Duplicate callbacks after a terminal result are ignored. Callback verification failures are returned as an API error so the provider can retry.

The backend does not yet run a scheduled STK reconciliation sweep for missing callbacks. An accepted request that times out at initiation remains pending and blocks retry; the callback URL contains the attempt UUID for later correlation. A frontend polling timeout must not be treated as payment failure. Poll the status endpoint with bounded backoff and stop when `terminal` is true. Keep showing an unresolved state for `PENDING`/`UNKNOWN`; do not create a new attempt for those states.

DEMO outcomes are applied by the existing scheduled simulator after `logiflow.demo-payments.confirmation-delay-seconds`. It persists success, failure, cancellation, or timed out status; the same status endpoint exposes the result. DEMO is disabled by default, must be allowlisted by organization UUID, and is prohibited in production.

## Retries, errors and fulfilment

The checkout row is locked while an attempt is created. Reusing the same idempotency key on the same checkout returns that attempt; using it on another checkout conflicts. A different key creates a new attempt only after the latest attempt is terminal and retryable. The backend preserves attempt history. A pending or unknown M-Pesa attempt cannot be duplicated through DEMO or another M-Pesa request.

Errors use the standard `ApiError` body (`code`, `message`, `path`, and optional field violations). Common responses include 400 for invalid method/phone/body, 401/403 for authentication or organization access, 404 for an unknown checkout, 409 for a blocked retry/state conflict, and 503 when payment configuration/provider service is unavailable. The `message` in the payment object is safe to show to users.

Supplier dispatch still requires the latest payment attempt to be `SUCCESS` and a matching allocation. Failed, cancelled, and timed out attempts do not create allocations or authorize dispatch.

## Known limitation

Daraja STK query is used to verify callback outcomes. The integration does not currently poll STK query for attempts that never receive a callback, so those attempts may remain pending and require operational/provider follow-up. No automatic timeout is inferred from browser polling or missing callbacks.
