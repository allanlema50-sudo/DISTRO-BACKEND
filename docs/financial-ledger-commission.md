# Financial ledger and commission foundation

This branch adds the accounting foundation for provider-backed marketplace payments. It does not move money or pay an organization. A confirmed, non-demo payment now has a durable, idempotent journal that records the gross amount, provider fee, LogiFlow commission, and organization payable amount.

## How the calculation works

For a confirmed payment:

```text
gross payment
- provider fee
- LogiFlow commission
= organization payable
```

The default commission is `5.00%`, controlled by `LOGIFLOW_DEFAULT_COMMISSION_PERCENT`. A database commission rule takes precedence over that fallback when its currency, organization type, and effective date match. Rules are created and deactivated by `SUPER_ADMIN`, and changes are written to the existing audit log.

Commission is capped at the amount remaining after the provider fee. If a fixed fee and percentage would consume more than the available gross, the supplier payable becomes zero rather than allowing the journal to become negative. Provider fees greater than the gross payment are rejected because they cannot produce a balanced payable allocation.

The journal is balanced:

```text
DEBIT  provider clearing                 gross
CREDIT organization payable             supplier amount
CREDIT platform revenue                 LogiFlow commission
CREDIT payment processing payable       provider fee
```

Demo payments deliberately do not create financial journals. They continue to be clearly marked as simulated and remain controlled by the existing demo-payment settings.

## Super-admin APIs

All endpoints below require a `SUPER_ADMIN` access token.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/financial/commission-rules` | List commission rules |
| POST | `/api/v1/admin/financial/commission-rules` | Create a commission rule |
| POST | `/api/v1/admin/financial/commission-rules/{ruleId}/deactivate` | Deactivate a rule without deleting its history |
| GET | `/api/v1/admin/financial/ledger/journals?page=0&size=20` | Review payment and payout journals, including provider references |
| GET | `/api/v1/admin/financial/ledger/journals/{journalId}` | Inspect one journal and its entries |

Create-rule example:

```json
{
  "ratePercent": 5.0,
  "fixedFee": 0.00,
  "currency": "KES",
  "organizationType": "DISTRIBUTOR",
  "effectiveFrom": "2026-12-01T00:00:00+03:00",
  "effectiveUntil": null
}
```

Set `organizationType` to `null` for a global rule. Rules may not overlap for the same currency and scope. A more specific organization-type rule wins over a global rule.

## Supplier payout accounting

When Paystack confirms a supplier transfer, LogiFlow posts a balanced journal:

```text
DEBIT  organization payable
CREDIT provider clearing
```

If Paystack later reverses that transfer, LogiFlow writes the opposite entries
as a separate reversal journal. Replayed provider events do not duplicate an
open payout posting. Each payout and reversal journal retains its Paystack
transfer reference, and payout references are unique in the ledger. The Super Admin
reconciliation report flags missing or unbalanced payout journals for settled
transfers and checks reversal state.

These entries record the provider-confirmed movement; they do not initiate a
transfer or independently prove bank statement settlement.

## What is deliberately not included yet

The payout workflow supports Paystack beneficiary onboarding, Super Admin-
reviewed batches, transfer-reference verification, and retryable payout status
updates. `PAYSTACK_TRANSFERS_ENABLED` remains separate from collection and
defaults to false. Signed Paystack webhooks are saved to a durable inbox before
the provider receives an acknowledgement, retried by a worker, and moved to
`DEAD_LETTER` after the configured limit for admin review. Full-checkout Paystack
refunds are supported only before supplier settlement is batched or paid, and
Paystack disputes are tracked and reviewed by Super Admins. Disputes that are
unresolved or not explicitly cleared block supplier payouts. Partial refunds,
post-payout refunds, provider statement import, and bank settlement confirmation
remain follow-up work. Internal LogiFlow reconciliation and on-demand Paystack
transaction matching are available, but neither proves that a bank credited a
beneficiary account.

No Tingg, M-Pesa, PesaLink, card, or bank API key is created by this branch. Provider credentials must be added later as deployment secrets, never committed to `.env.example` or source control.
