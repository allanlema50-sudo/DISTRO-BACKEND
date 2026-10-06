# Test the access request flow locally

## What is already wired

- The public form posts to `POST /api/access-requests` and the backend persists the request as `PENDING`.
- The backend calls `AccessEmailSender` after submission.
- With the default `MAIL_ENABLED=false`, `LoggingAccessEmailSender` writes the notification and activation link to the backend console.
- Approval creates a random activation token, stores only its SHA-256 hash, and builds `{FRONTEND_BASE_URL}/auth/activate?email=...&token=...`. Activation tokens expire after 24 hours and are cleared after successful activation.
- The super-admin API is protected by `ROLE_SUPER_ADMIN`.

## 1. Start local services

1. Start PostgreSQL and make sure the database credentials in `application.properties` work.
2. Start the backend with `mvn spring-boot:run`.
3. Start the request-access frontend. Its exact origin (scheme, host, port) must be in `CORS_ALLOWED_ORIGINS`; the defaults allow `http://localhost:4200` and `http://localhost:5173`.
4. Submit a request in the form. Confirm the success response and check the backend console for `[DEV SUPER ADMIN NOTIFICATION]`.

SMTP is optional for local testing. Do not set `MAIL_ENABLED=true` unless a reachable SMTP service is configured.

## 2. Admin identity prerequisite

This backend does not create or seed a super-admin account. A real super-admin account must already exist in the backend identity store with role `SUPER_ADMIN`, active status, and verified phone. The account can then sign in at `POST /api/auth/login` and the backend will issue the token used below.

The current JWT filter trusts tokens signed and issued according to this backend's `app.jwt.secret` and `app.jwt.issuer` configuration. A token from a separate admin portal/identity provider will not work automatically. Do not copy the signing secret into browser code. If the admin portal uses a separate identity provider, its developer and backend owner must agree on a server-side verification integration (for example, an OIDC/JWKS issuer and audience) before the dashboard can call the admin API.

If no real super-admin account can sign in through this backend yet, you can still submit requests and inspect the database/logs, but cannot use the protected approve/reject endpoints as an admin. The portal UI is not required for API testing; a REST client can be used once a valid admin token is available.

## 3. Sign in and review requests

In PowerShell, use credentials for the real super-admin account:

```powershell
$loginBody = @{
  identifier = "<real-super-admin-email>"
  password = "<real-super-admin-password>"
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody

$token = $login.accessToken
$headers = @{ Authorization = "Bearer $token" }

$requests = Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/admin/access-requests" `
  -Headers $headers

$requests | Format-Table id, fullName, personalEmail, organizationName, organizationType, status
```

Use the pending request's `id` to approve:

```powershell
$requestId = "<pending-request-id>"
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/admin/access-requests/$requestId/approve" `
  -Headers $headers
```

If the dashboard retries this approval after a successful first call, it receives an "already approved" message and the backend does not send another link. If two approval requests arrive concurrently, the backend serializes them on the request row.

With SMTP disabled, copy the `[DEV ACTIVATION EMAIL]` URL from the backend console into a browser. The frontend activation page should read its `email` and `token` query parameters and post them with `password` and `confirmPassword` to `POST /api/access-requests/activate`. After activation, sign in as the applicant at `POST /api/auth/login` and confirm its role and organization type in the response.

Submitting the same activation link a second time must fail. Activation reads the organization type from the approved request, so adding or changing an organization type in the URL or request JSON cannot change the resulting workspace. The activation response contains only a message, not access or refresh tokens; the user must sign in normally.

To reject instead, use `POST /api/admin/access-requests/{id}/reject`. Only `PENDING` requests can be approved or rejected.

## 4. Optional local SMTP delivery

The `gmail-test` profile configures Gmail SMTP on `smtp.gmail.com:587` with authentication and STARTTLS. It enables SMTP but contains no credentials. Gmail's SMTP settings require authentication and TLS; Google documents port 587 for TLS and recommends app passwords for apps that cannot use Sign in with Google. An app password requires 2-Step Verification. See [Gmail SMTP settings](https://support.google.com/mail/answer/7104828) and [Google app passwords](https://support.google.com/accounts/answer/185833).

Set these in the same PowerShell session used to start the backend. `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` refer to the Gmail account that sends the emails. `SUPER_ADMIN_EMAILS` is the recipient list for new-request notifications. Approval messages go to each applicant's personal email from the access request.

```powershell
$env:MAIL_ENABLED = "true"
$env:MAIL_USERNAME = "<sending-gmail-address@gmail.com>"
$env:MAIL_PASSWORD = "<google-app-password>"
$env:MAIL_FROM = $env:MAIL_USERNAME
$env:SUPER_ADMIN_EMAILS = "<real-super-admin-email>"
$env:FRONTEND_BASE_URL = "http://localhost:4200"

mvn spring-boot:run -Dspring-boot.run.profiles=gmail-test
```

If the activation frontend is not at port 4200, set `FRONTEND_BASE_URL` to its actual origin. Keep the app password in your local environment only; do not add it to `application.properties`, source control, or the frontend. With SMTP enabled, request notifications go to `SUPER_ADMIN_EMAILS`, and approval links go to the applicant's `personalEmail`.
