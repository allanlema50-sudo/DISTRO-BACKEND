# Frontend handoff: organization and marketplace web

Audience: the web application developer integrating distributor/manufacturer
payment, procurement, and payout-destination screens. This document describes
the backend contract and the intended user experience. Keep the project's
existing API client, authentication, routing, component library, and visual
system; do not invent a parallel payment model.

The inspected LogiFlow client is an Angular app with `PaymentService`, shared
`API_ENDPOINTS`, and a payment-history table. Implement the response adapter in
that existing service/model boundary. The backend does **not** currently return
one normalized transaction DTO for both payment journeys.

## The two payment journeys are different

1. **Customer → distributor:** a customer pays for a distributor's customer
   order. The implemented options are Safaricom Daraja M-Pesa STK Push and
   Paystack hosted card checkout (`PAYSTACK`). Paystack here means card
   checkout, not bank transfer.
2. **Distributor → manufacturer:** a distributor checks out one or more
   manufacturer purchase orders through centralized checkout. The checkout
   can use Daraja M-Pesa or a configured Paystack channel. A successful
   collection creates accounting allocations and a manufacturer payable; it
   does not itself mean that the manufacturer has received a payout.

Do not combine these flows into one payment object or display a customer-order
payment as a manufacturer payout. Paystack secret keys and Daraja credentials
are backend-only. Never collect card PAN/CVV in LogiFlow; Paystack hosts the
card entry page.

## Distributor procurement checkout

Use the centralized checkout API for a distributor paying one or more
manufacturers:

| Action | Endpoint |
| --- | --- |
| Create checkout | `POST /api/v1/checkouts` |
| List own checkouts | `GET /api/v1/checkouts?page=0&size=20` |
| Get checkout and payment summary | `GET /api/v1/checkouts/{checkoutId}` |
| List purchase orders in checkout | `GET /api/v1/checkouts/{checkoutId}/orders` |
| Start payment | `POST /api/v1/checkouts/{checkoutId}/payment` |
| Refresh payment state | `GET /api/v1/checkouts/{checkoutId}/payment` |
| Read allocations | `GET /api/v1/checkouts/{checkoutId}/allocations` |
| Cancel when permitted | `POST /api/v1/checkouts/{checkoutId}/cancel` |

Create the checkout from the selected manufacturer offers/products, quantities,
and the destination warehouse. The server validates and calculates the total;
always display the returned `totalAmount` and `currency`, not a client-side
recalculation. Send an `Idempotency-Key` for checkout creation and payment
initiation. Reuse the same key only when retrying the same user action after a
network failure.

Payment request example:

```http
POST /api/v1/checkouts/{checkoutId}/payment
Authorization: Bearer <organization-token>
Idempotency-Key: <unique-action-key>
Content-Type: application/json
```

```json
{
  "method": "PAYSTACK_CARD",
  "idempotencyKey": "<unique-action-key>",
  "channel": "card"
}
```

Methods/channels must follow the backend's supported values and current
provider/account configuration. Daraja M-Pesa is selected as `MPESA` and uses
the Kenyan phone number required by the request. Paystack card checkout uses
`PAYSTACK` or `PAYSTACK_CARD`; the server returns a hosted `authorizationUrl`.
Paystack generic mobile money is `PAYSTACK_MOBILE_MONEY`; it is not a promise
of a Safaricom-specific Paystack product and may include provider-supported
wallets such as Airtel Money. `PAYSTACK_MPESA` is a legacy alias and should not
be offered as a new distinct M-Pesa option. Do not label generic bank transfer
as a bank card, and do not promise PesaLink unless the deployed account and API
explicitly support it.

### Checkout payment screen states

- Before starting: show purchase-order lines, supplier, delivery warehouse,
  total and currency. Explain that supplier payout happens later under the
  settlement policy.
- During provider initiation: disable duplicate submit and retain the same
  idempotency key for this action.
- Paystack hosted checkout: open the returned `payment.authorizationUrl` in
  the system browser or approved Paystack flow. Show `payment.providerMode`
  (`TEST` or `LIVE`) prominently. Never mark success because the browser
  returned to the app.
- Pending/processing/unknown: show “checking payment”; refresh the checkout
  status. Do not offer another provider charge unless the server says
  `retryAllowed=true` or the prior attempt has a definitive failed/abandoned
  result.
- Success: show the confirmed checkout and each supplier order. Display
  allocation amounts as gross, provider fee, LogiFlow commission, and supplier
  payable. Use “payable/awaiting payout,” not “paid to supplier.”
- Failure or conflict (`409`): show the backend message and refresh state;
  never infer that a timeout means payment failed.
- Demo: show `DEMO — NO MONEY TRANSFERRED`. A Paystack TEST transaction is a
  real provider sandbox event, not LogiFlow's mock/demo payment.

### Checkout screens and interaction details

Implement a review/confirmation step followed by a payment-status view, using
the existing Angular routes and shared components:

1. **Review checkout:** list each supplier, product/quantity, destination
   warehouse, server total, and currency. Confirm before creating the checkout.
   Disable submit while pending; on network retry reuse the original
   idempotency key. On validation/authorization errors, keep form values and
   show API messages beside the relevant control.
2. **Choose method:** show only methods enabled by current app configuration.
   Daraja asks for a Kenyan number and explains an STK prompt will arrive.
   Paystack card says “Card (Paystack)”; generic Paystack mobile money says
   “Mobile money (Paystack)”—never “Paystack M-Pesa”. Explain TEST is sandbox
   activity. Do not present DEMO beside provider methods as another provider.
3. **Starting/redirecting:** show progress and prevent double submit. For
   Paystack, open only the backend-returned HTTPS authorization URL; don't
   construct it. On return, refresh once and provide “Check payment status”.
   The redirect itself is not proof of payment.
4. **Waiting:** show amount, reference, provider, TEST/LIVE badge, and
   “Payment pending confirmation”. Poll at a bounded interval while active,
   stop on terminal state or after a reasonable timeout, and retain a manual
   refresh action. Never start another charge while the API says unresolved.
5. **Paid/cancelled/failed:** show distinct success, cancellation, and
   definitive-failure states. After verified success, show supplier
   allocations separately as payable/awaiting settlement. Closing the browser
   is not a provider failure unless the API says so. On timeout, 5xx, or
   malformed response, preserve the checkout and let the user refresh; don't
   claim failure or clear order state.
6. **Empty/error:** checkout history gets a useful “No checkouts yet” state
   and a return-to-procurement action. `401/403` use the existing auth/permission
   flow; `404` refreshes the list; `409` reloads checkout and displays the API
   conflict. Never show stack traces, webhook payloads, or credentials.

Provide a detail view reachable from checkout history so the user can inspect
supplier orders, payment state, allocations, and settlement state later without
restarting payment.

`CheckoutResponse.Payment` includes `method`, `provider`, `status`,
`providerReference`, `providerMode`, `authorizationUrl`, `accessCode`, `demo`,
`demoLabel`, `terminal`, `retryAllowed`, and `userMessage`. An allocation
includes `grossAmount`, `providerFee`, `commission`, `supplierAmount`,
`settlementStatus`, and `settlementNote`.

## Customer-to-distributor payments (if these screens are in this web app)

These use the order payment API, not centralized checkout:

| Action | Endpoint |
| --- | --- |
| Start payment | `POST /api/v1/payments/initiate` |
| Read status for order | `GET /api/v1/payments/{orderId}/status` |
| Read payment by payment ID | `GET /api/v1/payments/{paymentId}` |
| List payments | `GET /api/v1/payments` |
| Read payments for order | `GET /api/v1/orders/{orderId}/payments` |

Example Paystack card request:

```json
{
  "orderId": "<order-uuid>",
  "provider": "PAYSTACK",
  "idempotencyKey": "<unique-action-key>"
}
```

`phoneNumber` is not required for Paystack card checkout. Daraja M-Pesa does
require the customer's Kenyan number. The initiation response includes
`paymentId`, `status`, `customerMessage`, `paymentReference`, `provider`,
`authorizationUrl`, and `providerMode`. Poll the status endpoint after browser
return/app resume; only the backend's verified status is authoritative.

## Payment-history adapter: actual DTOs and safe normalization

The current web table expects `direction`, `counterpartyName`, `updatedAt`, and
reconciliation details. Those fields are not present on every backend response.
Keep a UI `PaymentTransaction` model if useful, but parse the two server DTOs
separately in `PaymentService` and normalize only at that API boundary:

| Request | Actual backend response | Mapping rules |
| --- | --- | --- |
| `GET /api/v1/payments?orderType=CUSTOMER_ORDER` | Spring `Page<PaymentResponse>` (`content` plus paging metadata). Fields include `id`, `orderId`, `amount`, `method`, `status`, `mpesaReceiptNumber`, `initiatedAt`, `confirmedAt`, `orderType`, `provider`, `currency`, `failureReason`, `providerMode`, `paymentReference`, and `authorizationUrl`. | Use `initiatedAt` as created time and `confirmedAt ?? initiatedAt` as last known update. Keep provider, mode, reference, status, currency, receipt, and failure reason from the API. Derive direction from authenticated organization context. Reconciliation fields are unavailable: show “Not provided”/“—”, never claim `RECONCILED`. |
| `GET /api/v1/payments?orderType=CUSTOMER_ORDER&orderId=<id>` | A one-element `List<PaymentResponse>` when found (or empty list). | Use the same customer mapping; do not assume a `Page` wrapper. |
| `GET /api/v1/payments?orderType=PURCHASE_ORDER&orderId=<id>` | `List<PurchaseOrderSettlementResponse>`: `id`, `status`, `amount`, `currency`, `providerReference`, `note`, `settledAt`, and `createdAt`. Order ID is known from the request and not repeated in each row. | Attach the requested order ID. Direction is `INCOMING` for manufacturer and `OUTGOING` for distributor. Use `createdAt` and `settledAt ?? createdAt`; map `providerReference` only as a reference. Provider, mode, counterparty name, M-Pesa receipt, and reconciliation state are not supplied; render “Not provided”/“—”, do not infer them. |

Map only backend statuses: customer payment uses `INITIATING`, `PENDING`,
`CONFIRMED`, `FAILED`, `REVERSED`, or `RECONCILED`; purchase-order settlement
uses `PENDING`, `SETTLED`, `FAILED`, or `REVERSED`. Treat `CONFIRMED`,
`RECONCILED`, and `SETTLED` as successful for their own record type, but don't
collapse settlement success into “provider payment” or “payout complete”.
Unknown future status strings should display as “Needs review” with the raw
status available in details, not crash the entire list. Don't display
`REFUNDED` for customer-payment history unless that endpoint actually returns
it; refund support has a separate limited scope.

`GET /api/v1/payments` without `orderId` is a paged customer-payment response,
not a combined history feed. Supplier history currently requires listing the
organization's purchase orders and requesting each order's settlement list,
then sorting normalized results by creation time. Empty lists are normal. Keep
HTTP DTO types separate from the view model so a future unified backend-history
API can replace the adapter without rewriting the UI.

The current `payment.service.spec.ts` fixtures use the proposed normalized
shape, not the backend's actual DTOs. Replace/add fixtures using the exact
`PaymentResponse` page/list and `PurchaseOrderSettlementResponse[]` shapes
above. Cover manufacturer and distributor direction, empty arrays, absent
provider/reconciliation fields, and dates where `settledAt` is null. The
current table's “Reconciliation” column must not imply that purchase-order
settlements include reconciliation evidence; render “Not provided by this
endpoint” for those rows or use a neutral “Settlement details” column. Do not
invent provider or counterparty data where the DTO has none.

Keep normalization defensive: accept either a page's `content` or a list;
handle malformed rows without failing the entire screen; preserve raw status
for details; and sort only after validating timestamps. Do not log tokens,
account numbers, provider payloads, or full response bodies. For customer
payments, direction is incoming from the organization's view; for supplier
settlements use organization type as described above. These DTOs lack
counterparty names, so use a neutral “Customer order”/“Supplier order” label
plus order ID unless another authorized API supplies the name.

## Organization payout destination onboarding

A payout beneficiary is where an organization may receive a supplier payout.
It is not a card, a customer collection method, or evidence that a payout has
already occurred. These APIs are for manufacturer/distributor administrators;
Paystack credentials stay on the backend.

| Action | Endpoint |
| --- | --- |
| Load supported banks | `GET /api/v1/finance/beneficiaries/banks?currency=KES` |
| Load mobile-money providers | `GET /api/v1/finance/beneficiaries/mobile-money-providers?currency=KES` |
| Resolve a bank account name | `POST /api/v1/finance/beneficiaries/resolve-bank-account` |
| Register payout destination | `POST /api/v1/finance/beneficiaries` |
| List own organization's destinations | `GET /api/v1/finance/beneficiaries?page=0&size=20` |
| Read own organization ledger payable | `GET /api/v1/organization/ledger-balance?currency=KES` |

All beneficiary calls require a bearer token for an organization administrator.
Availability also depends on Paystack being configured with transfers enabled.

### Bank-account form UX (important)

1. On opening the form, load the bank list from the backend. Populate the
   dropdown from returned `name`, `code`, `currency`, `type`, and `active`;
   never hard-code provider bank codes.
2. Let the user choose a bank and enter the account number. Keep the account
   number transient in form state; do not write it to analytics, logs,
   browser storage, or error telemetry.
3. Call `POST /api/v1/finance/beneficiaries/resolve-bank-account` with
   `accountNumber`, selected `bankCode`, and `currency` (KES). The response
   provides `accountName`, `bankCode`, `bankName`, `currency`, and
   `maskedAccountNumber`.
4. Show the returned account name as read-only, along with bank and masked
   number. Ask the user to confirm that this is the intended payout account.
   Do not call it proof that the organization legally owns the account.
5. Only after explicit confirmation, submit
   `POST /api/v1/finance/beneficiaries` with `method: "BANK_ACCOUNT"`, the
   returned account name, currency, full account number, selected bank code,
   and `detailsConfirmed: true`.
6. The backend resolves the account again during creation and checks that the
   name still matches. If it does not, show the returned conflict, ask the
   user to resolve again, and require confirmation again. This avoids creating
   a recipient with stale or mismatched details.
7. On success clear the full number from client state and show only the
   backend's masked account information.

Resolve request:

```json
{ "accountNumber": "0123456789", "bankCode": "<code-from-bank-list>", "currency": "KES" }
```

Create request:

```json
{
  "method": "BANK_ACCOUNT",
  "accountName": "<resolved-account-name>",
  "currency": "KES",
  "accountNumber": "0123456789",
  "bankCode": "<code-from-bank-list>",
  "detailsConfirmed": true
}
```

### M-Pesa/mobile-money beneficiary UX

Load the provider list from `GET .../mobile-money-providers?currency=KES` and
use the returned provider `code`. Ask for the recipient name and Kenyan mobile
number, then display both for explicit confirmation before submit. The current
provider flow does not resolve a Kenyan mobile number to a verified registered
name; label the name as user-entered, not provider-verified.

```json
{
  "method": "MOBILE_MONEY",
  "accountName": "<user-confirmed-recipient-name>",
  "currency": "KES",
  "mobileNumber": "+254700000001",
  "bankCode": "<code-from-mobile-money-list>",
  "detailsConfirmed": true
}
```

The response contains only a masked `accountMask`, plus `status`, `provider`,
`providerMode`, and other beneficiary metadata. If status is `PENDING_REVIEW`,
explain that the destination is not yet usable. If `ACTIVE`, explain that it
can be selected for a future eligible payout; do not promise an immediate
transfer. Super Admin review is configurable and defaults off. A ledger balance
is an internal payable, not money in a bank account.

## Render sandbox configuration and client behavior

The frontend receives no provider credentials. For sandbox testing, backend
configuration uses `PAYSTACK_ENABLED=true`, `PAYSTACK_MODE=test`, an `sk_test_`
secret key, and the corresponding webhook signing secret. The API's public
webhook target is:

```text
POST https://<backend-host>/api/v1/checkouts/paystack/webhook
```

Set `LOGIFLOW_MARKETPLACE_CHECKOUT_ENABLED=true` only when testing the
distributor centralized checkout feature. Transfer testing is independently
controlled on the backend; never assume that enabling collection enables
payouts. Keep `APP_DEMO_CUSTOMER_FULFILLMENT=false` when testing genuine
provider payments. A Paystack `TEST` success is sandbox-only and must be
visually distinct from a live payment/payout.

The current web customer-payment model is MPESA-only and treats phone as
required. Update the existing model/service/component so provider is selectable
(`DARAJA` or `PAYSTACK`), phone is required only for Daraja, and Paystack
authorization URL plus `providerMode` are handled. The centralized-checkout UI
should offer backend-supported Paystack card and generic mobile-money methods
when enabled; don't label generic mobile money as Paystack M-Pesa, and don't
use `DEMO` for provider sandbox testing. Keep an explicitly enabled demo path
visibly separate from Paystack TEST.

## Shared implementation rules

- Bearer tokens go only to LogiFlow APIs. No Paystack secret, Daraja secret,
  signing secret, or provider callback body in the client.
- Store currency amounts as decimal values; never calculate commission or
  provider fees in the client.
- Refresh authoritative state after redirects, app resume, network recovery,
  and `409` conflicts. Use the API's state and `retryAllowed` field.
- Treat a provider redirect as navigation only. Treat webhook/verified backend
  status as payment confirmation.
- Do not build driver payout onboarding: no driver payout beneficiary API is
  implemented.

## Copy-ready implementation prompt

> Integrate the organization/marketplace payment and payout-destination flows
> from this handoff into our existing web app. Preserve current architecture,
> design system, routing, API client, and auth. Implement separate customer
> order payment and distributor procurement checkout experiences; use server
> totals and server payment state. For bank payout onboarding, load the bank
> list, resolve the selected bank/account number, display the returned name and
> masked number, require explicit user confirmation, then submit the beneficiary
> request. Include mobile-money onboarding using backend provider options and
> state clearly that the recipient name is user-entered. Never expose provider
> secrets, trust redirects, show a payable as paid out, or invent API fields.
> Add tests for pending/unknown/success/failure, idempotency, TEST/LIVE labels,
> beneficiary confirmation, masked data, API errors, and access restrictions.
> Update the existing payment-history adapter first: customer payment history
> returns `PaymentResponse` (page or one-row list), while a purchase-order
> history request returns `PurchaseOrderSettlementResponse` rows. Parse them
> separately and never fabricate absent provider, reconciliation, or
> counterparty fields.
