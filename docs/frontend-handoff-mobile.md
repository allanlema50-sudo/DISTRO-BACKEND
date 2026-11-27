# Frontend handoff: mobile app

Audience: the customer, distributor, and driver mobile-app developers. This is
an integration guide for implemented backend behavior. Preserve the current
mobile app's navigation, networking, state management, authentication, and
design system. Do not add provider credentials or invent endpoints.

The inspected mobile client is Kotlin/Jetpack Compose with Retrofit services,
repositories, and Hilt dependency injection. Add payment behavior through the
existing customer API/repository/view-model flow; don't introduce a second
network stack. Its current payment Retrofit DTO omits provider selection,
Paystack authorization URL, and provider mode, so those fields and UI states
must be added before the real sandbox flow can work end to end.

## Customer order payment: customer → distributor

Customer-order payments are separate from a distributor paying a manufacturer.
The implemented methods are Daraja M-Pesa STK Push and Paystack hosted bank-card
checkout. Paystack card checkout is not bank transfer. The customer chooses
one provider for one order; the app must not launch both providers for the
same unresolved payment.

| Action | Endpoint |
| --- | --- |
| Start payment | `POST /api/v1/payments/initiate` |
| Read order payment status | `GET /api/v1/payments/{orderId}/status` |
| Read a payment | `GET /api/v1/payments/{paymentId}` |
| List payments | `GET /api/v1/payments` |
| Read payment(s) for an order | `GET /api/v1/orders/{orderId}/payments` |

Example Paystack card request:

```http
POST /api/v1/payments/initiate
Authorization: Bearer <customer-token>
Idempotency-Key: <unique-action-key>
Content-Type: application/json
```

```json
{
  "orderId": "<order-uuid>",
  "provider": "PAYSTACK",
  "idempotencyKey": "<unique-action-key>"
}
```

The current Retrofit `PaymentInitiateRequest` requires a phone number and has
no provider field. Change it to match the backend request: include `provider`
and `idempotencyKey`, make `phoneNumber` optional for Paystack, and require it
for Daraja in client-side validation. Include response `provider`,
`authorizationUrl`, and `providerMode` in the Retrofit response model.

For Daraja, send `provider: "DARAJA"` (or the supported `MPESA` alias) and a
Kenyan `phoneNumber`; Daraja sends an STK prompt to that number. For Paystack
card, the phone number is not required. The response includes `paymentId`,
`status`, `customerMessage`, `paymentReference`, `provider`,
`authorizationUrl`, and `providerMode`.

### Payment user experience

1. Show order total/currency and the provider choices actually enabled by the
   app/backend. Label Paystack as “Card (Paystack)”; label Daraja as
   “M-Pesa (Safaricom)”. Do not show duplicate “Paystack M-Pesa” and “Daraja
   M-Pesa” options as though they were the same route.
2. On submit, disable duplicate taps and send a unique idempotency key. Keep it
   for a network retry of that exact action.
3. For Daraja, explain that the customer should approve the STK prompt. Poll or
   refresh `GET /api/v1/payments/{orderId}/status`; do not treat the phone
   prompt as confirmation.
4. For Paystack, open the returned hosted `authorizationUrl` in the system
   browser or approved secure browser. Show `providerMode` as TEST or LIVE.
   On app deep-link/return and on app resume, re-read LogiFlow status. A deep
   link, browser return, or SDK result is not authoritative payment success.
5. While status is `INITIATING` or `PENDING`, or the provider outcome is
   otherwise unresolved, show “Payment confirmation pending” and refresh. Do
   not start another provider payment. Customer-order payment responses do not
   expose a `retryAllowed` field: offer another attempt only after a definitive
   `FAILED` result, and let the backend accept or reject the new initiation.
6. On verified success, refresh the order and display its backend status. On
   `409`, explain the conflict from the server and reload state. On timeout or
   `5xx`, refresh status before offering another attempt.

### Required customer payment screens and states

- **Order payment summary:** show the order reference, server amount/currency,
  and separate choices for “M-Pesa (Safaricom)” and “Card (Paystack)” when
  available. Do not call Paystack card “bank transfer”. Require a number only
  after Daraja is selected; preserve the order and selection if validation
  fails.
- **Submitting:** disable the pay button, show progress, and submit one stable
  idempotency key per user action. A repeated tap must not create another
  attempt. If the request times out, query backend status before deciding what
  to show or allowing another attempt.
- **Daraja waiting:** say that the STK prompt was requested and ask the user
  to approve it on their handset. Do not say “paid” until the server returns a
  terminal success.
- **Paystack handoff:** open the returned authorization URL using the existing
  safe browser/deep-link mechanism. If no URL is returned, keep the attempt
  pending and offer status refresh/help—not a second payment. Show TEST/LIVE
  near the payment summary and after browser return.
- **Pending/unknown:** show a non-alarming “We’re confirming your payment”
  message, a refresh action, and explain not to pay again yet. Refresh on app
  resume/reconnect. Do not busy-poll in the background or claim the provider
  declined it.
- **Success/failure/reversal:** show separate terminal screens from the
  server state; success refreshes the order, definitive failure offers a new
  attempt only after the backend permits it, and reversed/refunded states must
  not be displayed as ordinary success. Provide a support-friendly reference
  without exposing provider secrets.
- **Offline/auth/validation:** preserve the form and show an actionable
  connection retry; on `401`, use existing login refresh; on `403`, explain
  access denial; on `409`, reload server state and explain the conflict. Never
  render raw exception text as a crash screen.

Use the app's existing accessibility conventions: labeled provider choices,
announced loading/result state, keyboard/screen-reader accessible browser
return, and no color-only distinction between TEST and LIVE.

The backend performs provider verification and handles signed webhooks. Never
mark the order paid based on a client redirect. Amount, currency, commission,
and provider charges are server-owned; the app does not calculate them.

## Customer delivery and driver app

Payment success and delivery completion are separate states. Continue to use
the existing order/trip/stop and delivery OTP APIs already used by the app.
The customer's delivery-code request is `POST
/api/v1/orders/{orderId}/delivery-otp`; use the existing trip-stop equivalent
if that is the app's current path. Display a code only to the authenticated
customer authorized for that delivery. The driver app should only receive the
assigned stop data and the information needed to complete that stop; it must
not expose another customer's OTP or order details.

A delivery code is not a payment receipt. Do not mark a payment successful
because a driver delivered an order, or mark a delivery complete because the
payment succeeded. Drivers do not currently have a payout-beneficiary or
compensation payout API; do not collect driver bank/M-Pesa payout details as
part of this integration.

## Distributor procurement (only if procurement is in the mobile app)

This is the separate distributor → manufacturer journey. The distributor
creates and pays a centralized checkout; it is not the customer order payment
API.

| Action | Endpoint |
| --- | --- |
| Create checkout | `POST /api/v1/checkouts` |
| List checkouts | `GET /api/v1/checkouts?page=0&size=20` |
| Get checkout | `GET /api/v1/checkouts/{checkoutId}` |
| Start payment | `POST /api/v1/checkouts/{checkoutId}/payment` |
| Read payment state | `GET /api/v1/checkouts/{checkoutId}/payment` |
| Read supplier purchase orders | `GET /api/v1/checkouts/{checkoutId}/orders` |
| Read allocations | `GET /api/v1/checkouts/{checkoutId}/allocations` |
| Cancel when allowed | `POST /api/v1/checkouts/{checkoutId}/cancel` |

Use the server-returned total. For Paystack, open its `authorizationUrl`, show
`providerMode`, and refresh the backend state when the user returns. `PENDING`,
`PROCESSING`, and unknown outcomes are not failures and do not permit another
charge unless the backend says retry is allowed. Show supplier allocation as
“payable/awaiting settlement,” not “paid to manufacturer.”

## Organization payout destinations (if organization admin tools are mobile)

Manufacturer/distributor admins can add payout destinations via these APIs:

```text
GET  /api/v1/finance/beneficiaries/banks?currency=KES
GET  /api/v1/finance/beneficiaries/mobile-money-providers?currency=KES
POST /api/v1/finance/beneficiaries/resolve-bank-account
POST /api/v1/finance/beneficiaries
GET  /api/v1/finance/beneficiaries?page=0&size=20
GET  /api/v1/organization/ledger-balance?currency=KES
```

For bank onboarding, load the dropdown options from the backend. After the user
selects a bank and enters an account number, call the resolve endpoint and show
the returned `accountName`, `bankName`, and `maskedAccountNumber` read-only.
Ask the user to confirm the name/details, then submit the beneficiary with
`detailsConfirmed: true`. The backend resolves the account again during
creation and rejects a changed/mismatched result. Clear the full account number
from app state after submission; never log or persist it in analytics.

For M-Pesa/mobile-money payout, load the provider code from the backend list,
ask for recipient name and phone, display them for confirmation, and submit the
selected code. The returned recipient name is user-entered, not a provider-
verified registered name. Show only the response's masked account data.
`PENDING_REVIEW` means not yet usable; `ACTIVE` means available for a future
eligible payout, not that money was sent. The ledger balance is an internal
payable, not a wallet/bank balance. Driver payout linking is not implemented.

## Sandbox and security rules

- Paystack TEST checkout is hosted by Paystack; the mobile app receives only
  the backend authorization URL. TEST must be visually labeled as sandbox.
- LogiFlow's explicit `DEMO` payment is different: show `DEMO — NO MONEY
  TRANSFERRED`; do not present it as a Paystack transaction.
- Provider keys, webhook secrets, callback payloads, and Daraja credentials
  must never be embedded in the app.
- Protect bearer tokens using the app's secure token storage. Handle `401`,
  `403`, `409`, validation errors, and provider-pending results distinctly.
- On resume/reconnect, fetch authoritative payment/order state before allowing
  a retry. Use decimal-safe money formatting and do not calculate fees locally.

### Disable fabricated payment data for provider sandbox runs

The inspected app selects `MockOrderRepository` versus
`NetworkOrderRepository` using `BuildConfig.USE_MOCK_DATA`; the build currently
defaults this flag to true when no local property is supplied, and the mock
repository fabricates payment IDs/receipts and can auto-confirm. Paystack
hosted TEST is not exercised while this mock path is active. Before a
Render-backed sandbox walkthrough, explicitly build with
`USE_MOCK_DATA=false`, verify the customer order and payment status come from
the deployed API, and ensure release builds cannot silently fall back to mock
payments. Keep mock fixtures for deliberate local/debug previews with a clear
“Demo data” indicator; never present a mock receipt as provider activity.

### Mobile sandbox release checklist

Before the team calls a mobile run an end-to-end provider test, verify all of
the following on the exact APK/build being demonstrated:

1. `USE_MOCK_DATA=false` is compiled into the selected build variant; verify
   the app reads orders and payment status from the configured Render API.
2. The build has the correct backend base URL and a valid authenticated user;
   no Paystack or Daraja secret is present in the APK or local app config.
3. The payment screen shows the provider actually selected, and Paystack's
   returned `providerMode` is shown as TEST. A mock/demo label must never be
   confused with Paystack TEST.
4. Success is confirmed by a fresh API status response after returning from
   Paystack/Daraja. Capture a redacted order/payment reference for test
   debugging, not a token or full callback payload.

## Copy-ready implementation prompt

> Integrate the customer order payment flow described here into our existing
> mobile app, preserving our current architecture and design system. Support
> Daraja STK Push and Paystack hosted card checkout as distinct providers. Use
> the server response to launch the provider flow and the LogiFlow status API
> to confirm payment after redirects, app resume, and network recovery. Never
> trust a client success callback or create another charge while the prior one
> is unresolved. If organization procurement is in scope, integrate centralized
> checkout separately. If payout-destination onboarding is in scope, implement
> bank-list → account resolve → read-only name/masked account → explicit
> confirmation → beneficiary-create, plus the mobile-money provider flow. Do
> not build driver payouts or invent API fields. Add UI tests for pending,
> unknown, success, failure, retry, TEST labeling, beneficiary name mismatch,
> masking, and authorization/offline errors.
