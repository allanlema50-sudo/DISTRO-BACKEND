# End-to-end story checklist

Story: customer registers and orders → pays via M-Pesa sandbox → distributor sees order/payment → raises PO → pays by bank transfer with proof → manufacturer confirms with `amount_received` → dispatch to driver → driver completes delivery with OTP → admin records a payout → reversal.

Statuses: **PASSED** | **FAILED** | **NOT RUN** (needs sandbox/staging).

| # | Step | How verified | Status |
| --- | --- | --- | --- |
| 1 | Backend Phase 1 migrations V92–V94 apply on empty embedded Postgres | `mvn test` / Flyway log to v94 | **PASSED** |
| 2 | Phase 1 bank/credit/fee integration tests (confirm, exact amount, credit partial, fee, M-Pesa payable journal, payer≠confirmer) | `Phase2SettlementIntegrationTest` 19/19 | **PASSED** |
| 3 | Full backend unit+integration suite | `mvn test` 283/0 fail | **PASSED** |
| 4 | Prod profile refuses demo flags | `ProductionDemoFlagGuardTest` | **PASSED** |
| 5 | LogiFlow production build | `ng build --configuration=production` | **PASSED** |
| 6 | MyDistro customer Paystack+Daraja DTO/checkout unit tests | `CustomerCheckoutTest` | **PASSED** |
| 7 | Customer registers on staging | Needs staging + email verify | **NOT RUN** |
| 8 | Customer places order against live API | Needs staging + stock | **NOT RUN** |
| 9 | Customer pays via **Daraja sandbox STK**; real receipt; no DEMO row | Needs Daraja sandbox + public callback | **NOT RUN** |
| 10 | Distributor sees order and payment in LogiFlow | Needs staging session | **NOT RUN** |
| 11 | Distributor creates PO (PAY_NOW) | Needs staging orgs + auth | **NOT RUN** |
| 12 | Distributor submits bank transfer + proof (multipart + Idempotency-Key) | Needs staging + Phase 1 deploy | **NOT RUN** |
| 13 | Manufacturer admin confirms with exact `amount_received`; payer cannot self-confirm | Covered by integration test locally; live UI **NOT RUN** | **PASSED** (unit/IT) / **NOT RUN** (UI) |
| 14 | Dispatch PO shipment to driver | Needs staging trip/driver | **NOT RUN** |
| 15 | Driver completes delivery with OTP | Needs staging OTP path | **NOT RUN** |
| 16 | Admin records a payout | Needs Paystack/beneficiary staging | **NOT RUN** |
| 17 | Reversal path | Needs settled payment + admin tools | **NOT RUN** |

## Locally runnable vs blocked

- **Runnable locally:** Flyway through V94 (embedded), Phase 1 settlement/credit/fee tests, demo-flag guard, LogiFlow prod build, MyDistro checkout unit tests.
- **Blocked without hosted staging/sandbox:** real Daraja STK callback, Paystack hosted card return, multi-role LogiFlow UI walkthrough, driver OTP on device, live payout/reversal.

Do not mark sandbox STK or payout steps PASSED until exercised against a deployed API with `USE_MOCK_DATA=false` / demo flags off.
