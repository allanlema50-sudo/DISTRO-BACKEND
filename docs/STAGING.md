# Staging runbook (no secrets)

Use a **fresh empty** Postgres (or new Supabase project). Flyway must own the schema from V1 through **V94**. Do **not** baseline, repair, or clean a database that already has conflicting history. Do **not** merge old branches that ship V83–V85 bank/credit under those numbers (see PR notes).

This document lists **variable names only**. Put real values in your host’s secret store / `.env` (never commit them).

## 1. Required environment (backend)

### Core

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Use `staging` or omit; use `prod` only when validating prod guards |
| `DATABASE_URL` | JDBC URL to empty Postgres |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials |
| `JWT_SECRET` | Strong secret (≥ 32 bytes recommended) |
| `JWT_ISSUER` | e.g. `logiflow` |
| `SERVER_PORT` / `PORT` | HTTP listen port |

### Demo flags (must stay off in staging-as-prod checks)

| Variable | Staging value |
| --- | --- |
| `APP_DEMO_CUSTOMER_FULFILLMENT` | `false` |
| `LOGIFLOW_DEMO_PAYMENTS_ENABLED` | `false` |
| `LOGIFLOW_DEMO_ORGANIZATION_IDS` | empty |

Production profile (`application-prod.properties`) refuses startup if either demo flag is true (`ProductionDemoFlagGuard`).

### First super admin (bootstrap once)

| Variable | Notes |
| --- | --- |
| `BOOTSTRAP_SUPER_ADMIN_ENABLED` | `true` only for first boot |
| `BOOTSTRAP_SUPER_ADMIN_EMAIL` | Platform admin email |
| `BOOTSTRAP_SUPER_ADMIN_PASSWORD` | Meets password rules; remove after bootstrap |
| `BOOTSTRAP_SUPER_ADMIN_FULL_NAME` | Display name |
| `BOOTSTRAP_SUPER_ADMIN_PHONE` | Phone |

After the first successful start: set `BOOTSTRAP_SUPER_ADMIN_ENABLED=false` and clear the bootstrap password from the environment.

### Daraja (M-Pesa sandbox)

| Variable | Notes |
| --- | --- |
| `MPESA_BASE_URL` | Default sandbox: `https://sandbox.safaricom.co.ke` |
| `MPESA_CONSUMER_KEY` / `MPESA_CONSUMER_SECRET` | Daraja app credentials |
| `MPESA_SHORTCODE` | Sandbox shortcode |
| `MPESA_PASSKEY` | STK passkey |
| `MPESA_CALLBACK_URL` | Public HTTPS URL ending with `?token=<callback-secret>` |
| `MPESA_CALLBACK_SECRET` | Shared secret; also accepted via header when enabled |
| `MPESA_VERIFY_CALLBACK` | Keep `true` |
| `MPESA_CALLBACK_ALLOWED_IPS` | Comma-separated IPs (or CIDRs if supported by your deploy) |
| `MPESA_CALLBACK_IP_ALLOWLIST_REQUIRED` | See allowlist section below |
| `MPESA_CALLBACK_TRUSTED_PROXY_COUNT` | Edge proxy hops (Render often `1`) |
| `MPESA_CALLBACK_HEADER_SECRET_ENABLED` | Prefer token-on-URL + secret for staging |

Optional: `MPESA_CHECKOUT_CALLBACK_URL` for marketplace checkout STK if separate from classic PO/customer.

### Paystack (card — optional for staging)

| Variable | Notes |
| --- | --- |
| `PAYSTACK_SECRET_KEY` / related Paystack vars | From your Paystack TEST project |
| `PAYSTACK_CALLBACK_URL` | Hosted return / webhook as configured in backend |

Do not put Paystack or Daraja secrets in mobile APKs or LogiFlow frontend.

### Other services (as deployed)

| Surface | Needs |
| --- | --- |
| LogiFlow web | `API` base URL to this backend; production build must keep mock/demo flags false |
| MyDistro | `BACKEND_BASE_URL` / `BuildConfig.BASE_URL`; `USE_MOCK_DATA=false` |
| Admin portal | Same API base; demo dispatch gated |

## 2. Fresh database + Flyway V1–V94

1. Create an **empty** Postgres database (no tables).
2. Point `DATABASE_*` at it.
3. Start the backend once. Flyway applies migrations through **V94** (`spring.flyway.clean-disabled=true`; do not run `flyway repair` / `clean`).
4. Confirm `flyway_schema_history` ends at version **94** (read-only query).
5. Confirm Hibernate starts with `ddl-auto=validate`.

If the database is not empty or history conflicts, **stop** and use a new empty database. Do not edit applied migrations.

## 3. Bootstrap super admin

1. Set bootstrap env vars (section above) with `BOOTSTRAP_SUPER_ADMIN_ENABLED=true`.
2. Start the app once; `DataInitializer` creates the SUPER_ADMIN when enabled.
3. Log in via `POST /api/auth/login` with that email/password.
4. Disable bootstrap and restart without the bootstrap password in env.

## 4. Daraja sandbox callback IP allowlist (staging without weakening prod)

**Prod** (`application-prod.properties`): `mpesa.callback-ip-allowlist-required` defaults **true**. Keep that for production.

**Staging options** (pick one; do not change prod defaults in code):

1. **Preferred:** Put staging behind the same edge as prod, set `MPESA_CALLBACK_ALLOWED_IPS` to Safaricom/sandbox egress (and your proxy), keep `MPESA_CALLBACK_IP_ALLOWLIST_REQUIRED=true`, and set `MPESA_CALLBACK_TRUSTED_PROXY_COUNT` to match the proxy chain. Always require `MPESA_CALLBACK_SECRET` on the callback URL (`?token=`).
2. **Temporary staging only:** If sandbox IPs are unstable, set `MPESA_CALLBACK_IP_ALLOWLIST_REQUIRED=false` **only** on the staging service env (not in `application-prod.properties`), and keep callback **token/secret** mandatory so callbacks without the secret still fail.
3. Never disable both allowlist and secret together.

## 5. Post-deploy checks

| Check | Expected |
| --- | --- |
| Start with `SPRING_PROFILES_ACTIVE=prod` and a demo flag forced `true` | Process **refuses to start** |
| Start staging with demo flags `false` | Healthy |
| `flyway_schema_history` max version | **94** |
| Customer or PO STK in Daraja sandbox | Real sandbox receipt / provider reference; **no** `DEMO` payment rows or “DEMO — NO MONEY TRANSFERRED” labels on that path |
| LogiFlow/MyDistro demo UI | Hidden unless explicitly non-prod and flagged |

## 6. Seed data without a seeder

Use `docs/scripts/staging-seed-via-api.ps1` against the staging API. It creates organizations (via access-request approve), users, warehouse/stock, and a driver through **REST only**. Passwords and URLs are parameters — not committed secrets.
