# DEMO Centralized Checkout Implementation Report

Date: 2026-11-17

## Delivered

Implemented an authenticated distributor checkout API for carts spanning at least two suppliers. Checkout creation validates supplier grouping and buyer-owned active destination warehouses, delegates each supplier group to the existing purchase-order service for authorization, offer/MOQ, stock, and server-price enforcement, reserves stock through those purchase orders, then records a parent checkout, item snapshots, and one child PO per supplier.

Added one centralized DEMO payment attempt per checkout. The client selects only `DEMO_MPESA` or `DEMO_CARD`; backend configuration selects the simulated outcome after a configured delay. The backend records checkout payment state and, on success, per-PO accounting allocations plus pending demo supplier settlements. Failure, cancellation, timeout, or buyer cancellation cancel child POs and release reserved stock. Supplier financial-status access is scoped to the owning supplier. Checkout POs cannot ship until the central payment succeeds and the order-specific allocation and captured destination match.

Added the frontend contract and workflow in [LOGIFLOW_CENTRALIZED_CHECKOUT_FRONTEND_INTEGRATION_GUIDE.md](LOGIFLOW_CENTRALIZED_CHECKOUT_FRONTEND_INTEGRATION_GUIDE.md). Updated the older manufacturer/distributor guide to describe the wired DEMO scope accurately.

## API surface

- `POST /api/v1/checkouts` (requires `Idempotency-Key`)
- `GET /api/v1/checkouts` and `GET /api/v1/checkouts/{checkoutId}`
- `GET /api/v1/checkouts/{checkoutId}/orders`
- `POST /api/v1/checkouts/{checkoutId}/payment`
- `GET /api/v1/checkouts/{checkoutId}/payment`
- `GET /api/v1/checkouts/{checkoutId}/allocations`
- `POST /api/v1/checkouts/{checkoutId}/cancel`
- `GET /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/financial-status`

## DEMO configuration and safety

The feature is off by default. Enable with `LOGIFLOW_MARKETPLACE_CHECKOUT_ENABLED=true` and `LOGIFLOW_DEMO_PAYMENTS_ENABLED=true` only in `local`, `dev`, `development`, `staging`, or `test`; additionally allowlist distributor organization IDs with `LOGIFLOW_DEMO_ORGANIZATION_IDS`. The configured result is `LOGIFLOW_DEMO_PAYMENT_OUTCOME` (`SUCCESS`, `FAILED`, `CANCELLED`, or `TIMED_OUT`), with delay `LOGIFLOW_DEMO_PAYMENT_DELAY_SECONDS`. The scheduler checks profile and feature flags. DEMO payment responses are marked `DEMO — NO MONEY TRANSFERRED`.

This does not integrate real multi-supplier M-Pesa split settlement, real card processing, provider reconciliation, supplier KYC, payouts, refunds, chargebacks, or payout confirmations. A successful buyer demo payment is not a supplier payout. Demo allocations use zero fee/commission and supplier settlements remain `PENDING`; no internal wallet or platform-held funds exist. Existing Daraja continues to handle its established single-PO payment workflow.

## Persistence and operational assumptions

The V55 checkout tables and entities are wired into application services and routes; migration `V55__marketplace_checkout_financial_foundation.sql` includes the supporting idempotency, destination, and DEMO fields. Buyer purchase orders remain supplier-specific. Checkout item display/price fields are snapshots and product imagery is optional. Checkout uses KES, consistent with current marketplace configuration.

## Verification

- Full main compilation: `mvn -s target/maven-settings.xml -DskipTests compile` succeeded, including 404 Java sources.
- Targeted regression: `mvn -s target/maven-settings.xml -Dtest=CentralizedCheckoutServiceTest,PurchaseOrderServiceTest,PaymentServiceTest,PurchaseOrderShipmentServiceTest test` passed (19 tests, zero failures/errors/skips).
- Broader regression: `mvn -s target/maven-settings.xml -Dtest=!DistroBackendApplicationTests test` passed (134 tests, zero failures/errors/skips).
- `DistroBackendApplicationTests` was excluded because application-context startup against the deployment database is not available in this environment.
- V55 was not applied to a disposable PostgreSQL database in this run; validate the migration against the target PostgreSQL version before rollout.

## Remaining before live marketplace payments

Select and contract an approved Kenya-capable marketplace provider; confirm KES, STK and hosted-card support, supplier onboarding, split rules, fees, payout timing, callback authentication and reconciliation, refunds, and chargebacks. Only then implement the provider adapter and real payout lifecycle, with sandbox integration evidence and end-to-end payout tests. Until that work is approved and completed, keep this explicitly in DEMO mode and do not describe settlements as paid.
