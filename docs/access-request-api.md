# Access request API handoff

The backend owns request persistence, review state, invitation delivery, token validation, and account creation. The request-access frontend and super-admin dashboard should call these APIs; neither frontend should write access-request or user records directly.

## Public request form

`POST /api/access-requests` (no authentication; returns `201 Created`)

```json
{
  "organizationType": "MANUFACTURER",
  "organizationName": "Example Manufacturing Ltd",
  "fullName": "Ada Example",
  "phoneNumber": "+254712345678",
  "personalEmail": "ada@example.com",
  "organizationEmail": "operations@example.com"
}
```

`organizationType` must be `MANUFACTURER` or `DISTRIBUTOR`. A successful response is `{"message":"Request submitted. A super administrator will review it."}`. Duplicate email or phone conflicts return HTTP 409. With SMTP disabled, the notification is written to backend logs; production must enable SMTP as described below.

## Super-admin dashboard

All routes below require `Authorization: Bearer <backend access token>`. The token must resolve to authority `ROLE_PLATFORM_ADMIN` (the admin portal's platform role) or legacy `ROLE_SUPER_ADMIN`; other roles receive HTTP 403.

| Dashboard action | API | Result |
| --- | --- | --- |
| Load request queue/history | `GET /api/admin/access-requests` | Array of requests, newest first |
| Approve pending request | `POST /api/admin/access-requests/{id}/approve` | Sets `APPROVED` and emails a one-use activation link |
| Reject pending request | `POST /api/admin/access-requests/{id}/reject` | Sets `REJECTED` |

Each list item contains `id`, `fullName`, `phoneNumber`, `personalEmail`, `organizationEmail`, `organizationName`, `organizationType`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `ACTIVATED`), and `createdAt`. Only `PENDING` requests can be approved or rejected. Show the API error `message` when an action fails, and reload the row/list after success.

Approval is idempotent: the first approval creates the organization and sends an activation link. Repeating approval for an already `APPROVED` request returns a message without sending another link or creating another organization. Only one activation can consume a request token. The organization type is taken from the saved request; the activation API has no organization-type field. Activation creates the user inside the organization but does not sign the user in; the user must use the normal login endpoint afterwards. Workspace authorization is enforced by the backend token role and organization, not by the URL the user visits.

The platform-admin account must be provisioned by the platform's real identity/account provisioning process. Self-registration cannot assign `PLATFORM_ADMIN`. The dashboard must authenticate through an identity integration whose access token this backend accepts and maps to `ROLE_PLATFORM_ADMIN` (legacy `ROLE_SUPER_ADMIN` tokens remain accepted); do not add a public role-assignment endpoint or let the dashboard send a role in its request body.

## Activation page

The approval email links to `{FRONTEND_BASE_URL}/auth/activate?email=...&token=...`. The frontend should read both query values, collect password and confirmation, then call:

`POST /api/access-requests/activate` (no authentication)

```json
{
  "email": "ada@example.com",
  "token": "token-from-link",
  "password": "StrongPassword1",
  "confirmPassword": "StrongPassword1"
}
```

The token expires after 24 hours and is consumed on successful activation. A success message directs the user to sign in; login returns the persisted user, role, and organization type for workspace routing.

## Production configuration

Configure these as deployment secrets/environment values. Do not commit SMTP credentials. For a typical SMTP STARTTLS provider, set `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT=587`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH=true`, and `MAIL_SMTP_STARTTLS=true`. Set `SUPER_ADMIN_EMAILS` to the comma-separated notification recipients and `FRONTEND_BASE_URL` to the deployed frontend origin. Set `CORS_ALLOWED_ORIGINS` to the comma-separated frontend/dashboard origins allowed to call the API.

Also configure production PostgreSQL and a secret JWT signing key. Use the deployed database's normal migration/backup process; the current local `ddl-auto=update` setting should not be used as the production schema migration strategy.
