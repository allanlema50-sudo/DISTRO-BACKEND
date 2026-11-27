# Frontend handoff: Super Admin finance console

Audience: the Super Admin web developer implementing payment operations,
reconciliation, payouts, and financial oversight. This is an operator console,
not a place to initiate customer checkout. Preserve the existing admin app,
role guard, API client, navigation, and design system.

The inspected admin client is Angular with a shared `PaymentsService`, payment
models, and a `/payments` page. It currently consumes legacy payment list/detail
routes and does not call the unified activity or finance APIs below. Extend the
existing services/models and route structure; migrate the activity page to the
unified read API rather than maintaining a second browser-side aggregation.

## Financial concepts the UI must keep separate

- **Collection:** money reported as received by the provider for a customer
  order or marketplace checkout.
- **Allocation/ledger:** LogiFlow's accounting split of gross, provider fee,
  platform commission, and organization payable.
- **Settlement:** amount owed to a supplier organization.
- **Payout attempt:** a request to Paystack Transfers for an organization
  beneficiary. `SETTLED` means Paystack reported successful transfer; it is not
  independent proof of final bank posting.
- **Refund/dispute:** post-collection events that can hold or reverse amounts.
- **TEST vs LIVE vs DEMO:** provider mode and explicit mock/demo state must be
  visible on every relevant screen. TEST success is sandbox activity, not real
  money delivered. DEMO means no provider funds moved.

Never label an internal payable as already received by an organization. Use
confirmation dialogs before money-moving or irreversible administrative
actions; show organization, amount, currency, recipient mask, mode, and stable
reference in the dialog.

## Unified payment activity feed

Use the implemented unified, read-only API rather than combining browser-side
queries:

```http
GET /api/v1/admin/payments/activity?from=2026-11-01T00:00:00Z&to=2026-11-08T00:00:00Z&provider=PAYSTACK&providerMode=TEST&status=SUCCESS&organizationId=<uuid>&search=<text>&page=0&size=50
Authorization: Bearer <super-admin-token>
```

Filters are optional. `from` and `to` are ISO-8601 timestamps with offsets;
`to` is exclusive and a supplied range is at most 31 days. Provider options are
`PAYSTACK`, `DARAJA`, and `DEMO`; mode is `TEST` or `LIVE`. Pages are
zero-based, size is capped at 100, and newest-first ordering has a stable ID
tie-breaker. `search` is limited to 120 characters. The response is a Spring
`Page` with `content`, `number`, `size`, `totalElements`, and `totalPages`.

Rows identify `transactionType` (`CUSTOMER_ORDER` or
`MARKETPLACE_CHECKOUT`) and include payment state, provider/mode, amount and
currency, reference, initiating user, organization/context, and demo state.
Marketplace rows additionally include allocation totals, settlement states,
refund amounts/states, and dispute counts. This is LogiFlow's stored view; it
does not query Paystack or a bank statement. Refresh after actions; no live
stream/websocket is provided by this endpoint.

The current admin `Payment` model collapses some provider statuses and omits
`providerMode` and `transactionType`. Preserve `REVERSED` distinctly from
`REFUNDED`, add a TEST/LIVE/DEMO badge based on the API's returned mode/demo
fields, and identify customer-order versus marketplace-checkout rows. Never
infer mode from the configured environment: historical records carry their own
mode. Do not use the legacy `PATCH /api/v1/admin/payments/{paymentId}/status`
as a way to manually force a provider payment outcome; provider confirmation
and reconciliation are authoritative.

## Implemented finance and operations APIs

All routes under `/api/v1/admin/financial/**` require `SUPER_ADMIN`, unless
noted otherwise. Use backend validation/conflict messages; don't duplicate
settlement eligibility calculations in the UI.

| Screen/action | Endpoint |
| --- | --- |
| Unified activity | `GET /api/v1/admin/payments/activity` |
| Legacy customer-order payments | `GET /api/v1/admin/payments`, `GET /api/v1/admin/payments/{paymentId}` |
| Update legacy payment status/reverse | `PATCH /api/v1/admin/payments/{paymentId}/status`, `POST /api/v1/admin/payments/{paymentId}/reverse` |
| Ledger journal list/detail | `GET /api/v1/admin/financial/ledger/journals`, `GET /api/v1/admin/financial/ledger/journals/{journalId}` |
| Commission rules | `GET/POST /api/v1/admin/financial/commission-rules`, `POST /api/v1/admin/financial/commission-rules/{ruleId}/deactivate` |
| Beneficiary list/review | `GET /api/v1/admin/financial/beneficiaries`, `PATCH /api/v1/admin/financial/beneficiaries/{beneficiaryId}/review` |
| Supplier settlements | `GET /api/v1/admin/financial/settlements?status=<status>` |
| Payout attempt history | `GET /api/v1/admin/financial/settlements/{settlementId}/attempts` |
| Settlement batches | `GET/POST /api/v1/admin/financial/settlement-batches` |
| Approve/execute a manual batch | `POST /api/v1/admin/financial/settlement-batches/{batchId}/approve`, `POST /api/v1/admin/financial/settlement-batches/{batchId}/execute` |
| Retry a conclusive failed/reversed settlement | `POST /api/v1/admin/financial/settlements/{settlementId}/retry` |
| Reassign a beneficiary for eligible failed/reversed settlement | `PATCH /api/v1/admin/financial/settlements/{settlementId}/beneficiary` |
| Webhook inbox/dead-letter retry | `GET /api/v1/admin/financial/webhook-events?status=<status>`, `POST /api/v1/admin/financial/webhook-events/{eventId}/retry` |
| Refund list/request full checkout refund | `GET /api/v1/admin/financial/refunds`, `POST /api/v1/admin/financial/payments/{paymentId}/refund` |
| Dispute list/review | `GET /api/v1/admin/financial/disputes?status=<status>`, `PATCH /api/v1/admin/financial/disputes/{providerDisputeId}/review` |
| Internal reconciliation report | `GET /api/v1/admin/financial/reconciliation-report?from=<ISO>&to=<ISO>&page=0&size=50` |
| On-demand Paystack transaction comparison | `GET /api/v1/admin/financial/provider-transaction-matches?from=<ISO>&to=<ISO>&page=1&perPage=50` |
| Record payout completed externally (not a Paystack transfer) | `POST /api/v1/admin/organization-payouts` |

The provider transaction matching page uses **one-based** provider pagination;
the other Spring `Page` endpoints use zero-based `page` and `size`. The
provider-matching and internal-reconciliation reports have a maximum 31-day
window and page-size limits described by their APIs; keep the two reports
visibly separate because they answer different questions.

## Recommended screen structure and behavior

### 1. Activity

Make this the finance landing page: date range, flow, provider, mode, status,
organization, and search filters; paginated unified results; detail drawer or
route. Show a persistent TEST/LIVE/DEMO badge. For marketplace records show
gross, fees, commission, payable, settlement/refund/dispute indicators. Label
customer-order and marketplace-checkout transactions explicitly.

**Interaction requirements:** initialize with a bounded recent date window,
not an unbounded history query. Keep filters visible with Apply/Reset; changing
a filter resets to page zero. Show loading skeletons, an empty result with a
“change filters” action, and an error with retry. Preserve filters when opening
and closing a detail view. Show only fields returned by the API; absent values
must say “Not available”, not be inferred. After an action, refetch the detail
and activity page. This API is request/refresh-based, not a live push stream.

### 2. Ledger and commission rules

Show journals and entries as accounting evidence. A balanced journal means
LogiFlow's books balance; it does not prove a provider/bank transfer. Display
commission scope (currency/organization type), percent/fixed amount, effective
dates, active status, and audit metadata returned by the API. Confirm create
and deactivate actions; refresh the rule list after success. Never calculate
historical charges using today's rule.

Use a read-only journal detail view with debit/credit entries, account,
currency, source, event, and creation time exactly as returned. Rule creation
uses a dedicated form for currency, organization scope, percentage, fixed fee,
and effective dates. Keep input values on validation/conflict errors.
Deactivation requires confirmation naming the rule; it must not rewrite prior
journals.

### 3. Beneficiaries

List organization, method, currency, masked destination, provider mode, status,
submission/review timestamps, and review note. Do not display full account or
phone details. If review is enabled, the organization destination remains
unavailable until approved. Rejecting/reviewing must use the exact API status
and request DTO; ask the API for current state after a conflict.

Provide pending/active/rejected filters and a detail panel with masked
destination and organization identity. Review/reject dialogs should state that
this changes LogiFlow's eligibility record, not provider KYC. Require any
review note required by the request DTO, disable duplicate submission, and
refresh after a conflict or ambiguous timeout before offering another action.

### 4. Settlements and payout batches

Show each settlement's organization, amount, currency, status, beneficiary,
provider mode, eligibility/blocking explanation from server errors, and payout
attempt history. The configured delivery hold is for distributor-to-
manufacturer supplier settlement and begins after the distributor confirms
shipment receipt; do not start the clock at checkout or shipment dispatch.

Manual LIVE/TEST batches have create → approve → execute actions. Never execute
automatically on page load. For the configured TEST automatic settlement path,
show its `TEST_AUTOMATIC` execution label and explain that it is a Paystack
sandbox transfer. A `PROCESSING` or ambiguous attempt is unresolved; do not
offer a second transfer under a new reference. Retry only after a conclusive
failure/reversal and refresh batch/settlement state after any action. The
attempt-history endpoint is essential when older and newer transfer attempts
receive callbacks out of order.

For batch creation, show selectable eligible settlements with organization,
amount, currency, destination mask, and current status. Require explicit
selection and a review/total confirmation before creating a batch. Approval
and execution are separate actions with separate confirmations. The execution
confirmation must name mode, recipient mask, amount, currency, and explain the
result may remain pending. Disable actions while in flight. After timeout,
query the batch, settlement, and attempt history before retrying; never issue a
new transfer merely because the browser did not receive a response. Distinguish
`PROCESSING` (unresolved), `FAILED` (conclusive provider failure), `REVERSED`
(provider reversed a prior success), and `SETTLED` (provider-reported success,
not independent bank proof). For automatic TEST settlement, show that no live
funds moved and why the settlement became eligible, including delivery-hold
state when returned.

`PAYSTACK_TRANSFERS_ENABLED` is independent of checkout collection. An internal
Paystack TEST success does not mean a real payout occurred. `POST
/api/v1/admin/organization-payouts` only records a payout completed elsewhere;
it does not send money.

### 5. Refunds and disputes

Refund endpoint supports full Paystack marketplace-checkout refunds only; it
does not support partial refunds, customer-order refunds, or refunds after a
supplier payout has started. Confirm payment, amount, TEST/LIVE mode, and
settlement eligibility before request. Show `REFUND_PENDING` as pending; do not
restore payment state on an ambiguous outcome. A `needs-attention` refund
requires operator follow-up through Paystack and must remain on hold.

Disputes are sourced from provider events. Internal review does not submit
evidence to Paystack or resolve a provider dispute. A dispute can block supplier
settlement; clearing requires the provider to report resolved and a nonblank
internal review note. Show provider dashboard follow-up as a separate task.

The refund request view must show the exact payment, full amount, currency,
mode, and supported limitations before confirmation. After submission, keep
the result pending until refreshed provider evidence confirms processed or
failed; never optimistically change payment/payable state. Dispute detail may
show evidence/deadline fields only if the API returns them. Provide a separate
internal-review form and make clear that the note does not upload evidence to
Paystack.

### 6. Webhook inbox and reconciliation

For `DEAD_LETTER`, show event identity, type, attempt count, last error, and
timestamps; allow manual retry only after inspection. Dead letter means event
processing failed, not that the underlying payment failed. Do not expose raw
webhook secrets/signatures.

Use a searchable, paginated inbox with explicit `RETRY`, `PROCESSING`,
`PROCESSED`, and `DEAD_LETTER` states. A retry confirmation must name the event
type/reference and explain that it reprocesses a notification; it does not
recharge a customer or resend a transfer. Disable retry while processing and
refresh after the action. Reports need client-side date validation (maximum
31 days), loading/empty/error states, and pagination that respects the
endpoint's indexing convention. Do not imply that export or statement storage
exists unless a separate supported feature is implemented.

The internal report checks LogiFlow records for allocations, ledger balance,
settlements, refunds, disputes, and payout/reversal journals. The on-demand
provider match compares a requested Paystack transaction page to local records;
page through the range because the response is not a stored statement. Neither
report by itself proves that a bank has posted funds.

## Form request examples

Commission rule creation fields are defined by the current request DTO; obtain
currency, organization scope, percentage/fixed charge, and effective interval
from the API contract. Use the returned rule ID for deactivation.

Settlement batch creation accepts settlement IDs:

```json
{ "settlementIds": ["<settlement-uuid>"] }
```

Dispute review:

```json
{
  "reviewNote": "Provider reports resolved; delivery record reviewed.",
  "clearForSettlement": true
}
```

Confirm each financially meaningful action, display the server's exact
conflict/validation reason, then re-fetch the relevant resource. Do not assume
that an HTTP timeout means the operation did not reach Paystack.

## Super Admin delivery acceptance checklist

- Every page is role-protected and handles `401/403` without leaking finance
  records across sessions.
- Every list has loading, empty, error/retry, and pagination/filter states.
- Amounts always show currency; TEST/LIVE/DEMO comes from the stored API
  record, never from current deployment settings.
- Every write has the required permission, confirmation, pending state,
  duplicate-submit guard, and post-action refresh.
- Ambiguous provider outcomes stay visibly unresolved and cannot be retried as
  a new transfer/refund until the backend reports a conclusive state.
- Tests cover filters, date bounds, page indexing, masked beneficiary display,
  permission errors, and payment/settlement status distinctions.

## Copy-ready implementation prompt

> Build the Super Admin finance screens using only the implemented endpoints in
> this handoff and the actual DTO/OpenAPI contract. Preserve our current admin
> frontend architecture and authorization. Implement the unified activity
> feed, ledger/commission, beneficiary review, settlement/batch/attempt,
> refund/dispute, webhook inbox, and reconciliation screens. Clearly distinguish
> collection from ledger payable and provider payout; display TEST/LIVE/DEMO
> on every relevant record. Add confirmation for financial actions, safe
> handling of pending/unknown states, server-side filters/pagination, and
> refresh after mutations. Do not invent an endpoint, claim bank settlement
> based only on a journal/provider response, or expose full beneficiary data.
> Add tests for filters, pagination, permissions, pending/unknown outcomes,
> payout retries, refund/dispute holds, dead-letter retry, and test-mode labels.
