# LogiFlow Backend Payment Lifecycle Implementation Report

## Root causes addressed

- Centralized checkout persisted only one payment row per checkout, enforced by a unique database constraint; the attempt store rejected terminal attempts, preventing retries.
- The checkout payment response exposed status and timestamps but did not tell Angular whether a result was terminal or retryable.
- Supplier dispatch looked up the single payment row directly, which would be ambiguous once attempts are retained.

## Changes

- Changed checkout payments to many-to-one attempt history and added Flyway migration `V58__allow_checkout_payment_attempt_history.sql` to drop the checkout uniqueness constraint and add a lookup index. Versions 56 and 57 are already used by marketplace foundation and vehicle cargo volume migrations.
- Serialized attempts through the existing checkout row lock; idempotency keys replay the same attempt, unresolved and successful attempts block new attempts, and failed/cancelled/timed out attempts can be retried.
- Exposed checkout reference, attempt UUID, update timestamp, terminal/retry flags, and a safe user message in payment status responses.
- Updated dispatch validation to check the latest attempt and its matching allocation.
- Retained STK query callback verification and the existing DEMO simulator. Verified callback cancellation/failure remains unpaid; retries retain supplier orders and stock reservations until the checkout is explicitly cancelled.

## Migration and deployment

Deploy with Flyway enabled so V58 applies after V56 and V57. Existing payment data remains unchanged. No new environment variables were introduced. Daraja callback URL and credentials plus the existing STK query URL must be configured as documented in `application.properties`.

## Verification status

The requested full backend test run and migration validation have not completed. The Maven wrapper invocation failed with `Cannot start maven from wrapper` in this environment. This report therefore documents implementation behavior and does not claim those paths are tested. A scheduled reconciliation process for pending M-Pesa attempts without callbacks is not implemented; the integration guide calls out that limitation.
