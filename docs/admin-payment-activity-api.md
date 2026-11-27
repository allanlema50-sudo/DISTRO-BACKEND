# Super Admin payment activity API

## Unified activity feed

`GET /api/v1/admin/payments/activity` returns one newest-first page combining:

- Customer-to-distributor order payments.
- Distributor-to-manufacturer marketplace checkout payments.

The endpoint is read-only and requires a Super Admin access token. It reports data recorded by LogiFlow; it does not query Paystack, Daraja, a bank, or an M-Pesa statement. `providerMode` and `demo` describe the recorded payment configuration and are not independent proof of external settlement.

### Query parameters

All parameters are optional. Dates must be ISO-8601 timestamps with an offset. If both `from` and `to` are supplied, `to` is exclusive and the interval may not exceed 31 days.

| Parameter | Meaning |
| --- | --- |
| `from`, `to` | Filter by payment initiation time |
| `provider` | `PAYSTACK`, `DARAJA`, or `DEMO` |
| `providerMode` | `TEST` or `LIVE` |
| `status` | A supported status from either payment flow |
| `organizationId` | Match the distributor organization, or a marketplace supplier organization |
| `search` | Search provider reference, order/checkout reference, buyer organization, or marketplace supplier name (max 120 characters) |
| `page` | Zero-based page number; default `0` |
| `size` | Page size from `1` to `100`; default `50` |

Example:

```http
GET /api/v1/admin/payments/activity?from=2026-11-01T00:00:00Z&to=2026-11-08T00:00:00Z&provider=PAYSTACK&providerMode=TEST&page=0&size=50
Authorization: Bearer <super-admin-token>
```

The response uses Spring's `Page` shape (`content`, `number`, `size`, `totalElements`, and `totalPages`). Each row identifies the flow with `transactionType` (`CUSTOMER_ORDER` or `MARKETPLACE_CHECKOUT`) and includes the payment/provider state, mode, amount, initiating user's email, organization and context. Marketplace checkout rows also include allocated gross, provider fees, commission, supplier payable, settlement statuses, processed refund amount/statuses, and dispute/open-dispute counts. Customer-order rows report zero marketplace allocations and no marketplace refund/dispute data.

Refresh by polling this endpoint when the admin opens the finance activity screen or applies filters. The API does not currently stream events or provide statement-level provider reconciliation.
