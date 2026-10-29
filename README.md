# Distro Backend

Spring Boot backend for the LogiFlow distribution platform. It provides
authentication, manufacturer products, distributor offers and warehouses,
orders, M-Pesa payments, procurement, stock operations, delivery trips, and
live location updates.

Commands in this document are run from the repository root:

```text
DISTRO-BACKEND/
```

## Before you start

Install the following locally:

- Git
- Docker Desktop with the Docker Compose plugin
- Java 17 or later
- Maven 3.9.x if you want to run the API directly on your machine
- An HTTP client such as Swagger UI, Postman, Insomnia, `curl`, or PowerShell

Docker Desktop must be open and its engine must be running before any
`docker compose` command. Docker is the recommended workflow because it runs
the API and PostgreSQL with the same configuration used by the project image.

The backend uses PostgreSQL 16, Flyway migrations, JWT bearer authentication,
Spring WebSocket/STOMP, and OpenAPI/Swagger.

## Clone and configure

```bash
git clone <repository-url>
cd DISTRO-BACKEND
```

Create the local environment file from the committed template.

PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

The repository also includes ignored profile files for local use:
`.env.local` is a runnable Docker-oriented development profile, while
`.env.production` is a production deployment template containing placeholders
for provider-managed database, SMTP, M-Pesa, and SMS credentials. Docker
Compose reads `.env` by default; to use the local profile explicitly, run
commands from the repository root with:

```powershell
docker compose --env-file .env.local up --build -d
```

`.env.local`, `.env.production`, and `.env` are intentionally not tracked.
Render and similar platforms do not automatically read `.env.production`; add
the same variables through the platform's secret/environment settings instead.
Maven also does not load dotenv files automatically, so use Docker for the
least-surprising local setup or export the required variables in the shell
before running Maven directly.

At minimum, set these values in `.env` before starting Docker:

```dotenv
POSTGRES_PASSWORD=<strong-local-password>
JWT_SECRET=<strong-base64-encoded-secret>
```

The template contains safe local defaults for the database name, database
user, ports, CORS, reservation settings, and OpenAPI. `.env` is ignored by
Git. Never commit it or place real credentials in `.env.example`.

Generate a JWT secret with PowerShell:

```powershell
$b=[byte[]]::new(64); [Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

Generate one with macOS/Linux:

```bash
openssl rand -base64 64
```

Important environment values:

| Variable | Local default | Purpose |
|---|---:|---|
| `POSTGRES_DB` | `distro_backend` | PostgreSQL database name |
| `POSTGRES_USER` | `distro` | PostgreSQL username |
| `POSTGRES_PASSWORD` | none | Required by Docker Compose |
| `POSTGRES_PORT` | `5433` | PostgreSQL port exposed on the host |
| `API_PORT` | `8080` | API port exposed on the host |
| `JWT_SECRET` | none | Signs access tokens; required |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200,http://127.0.0.1:4200` | Comma-separated browser origins allowed to call the API |
| `OPENAPI_ENABLED` | `false` | Enables Swagger UI and OpenAPI JSON |
| `FRONTEND_BASE_URL` | `http://localhost:4200` | Base URL used in onboarding and invitation links |
| `MAIL_ENABLED` | `false` | Enables post-commit onboarding email delivery |
| `MAIL_HOST` / `MAIL_PORT` | empty / `587` | SMTP server settings when mail is enabled |
| `GMAIL_SMTP_USERNAME` / `GMAIL_SMTP_APP_PASSWORD` | empty | Gmail SMTP account and app password; configure only in the deployment secret settings |
| `ONBOARDING_ACTIVATION_TTL` | `24h` | Lifetime of an approved administrator activation link |
| `ONBOARDING_INVITATION_TTL` | `7d` | Lifetime of an organization administrator/staff invitation link |
| `ONBOARDING_SUBMISSION_WINDOW` | `15m` | Database-coordinated public access-request admission window |
| `ONBOARDING_MAX_SUBMISSIONS_PER_WINDOW` | `100` | Maximum accepted access requests in one admission window |
| `ONBOARDING_MAX_DOCUMENT_STORAGE` | `1GB` | Total private storage reserved for onboarding evidence |
| `ONBOARDING_DOCUMENT_RETENTION` | `30d` | Retention period for evidence belonging to rejected or expired requests |
| `ONBOARDING_DOCUMENT_RETENTION_SCAN_MS` | `86400000` | Scheduled cleanup interval for retained onboarding evidence |
| `ORDER_RESERVATION_TTL` | `15m` | Unpaid stock-reservation lifetime |
| `ORDER_MAX_OPEN_PENDING_PER_CUSTOMER` | `5` | Maximum open unpaid orders per customer |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` | Only for a reviewed pre-Flyway database |

M-Pesa variables are optional for starting the API, but they must be configured
before payment initiation or callback processing can work. Copy the complete
list from `.env.example`; do not invent values for production credentials.

## Start the backend with Docker

From the repository root, with Docker Desktop running:

```bash
docker compose up --build -d
```

Check that both services are running:

```bash
docker compose ps
```

Follow API logs when troubleshooting startup or migrations:

```bash
docker compose logs -f api
```

Check the database logs separately:

```bash
docker compose logs -f db
```

Confirm that the API is ready:

PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

macOS/Linux:

```bash
curl http://localhost:8080/actuator/health
```

The expected response contains:

```json
{"status":"UP"}
```

The Compose stack exposes the API at `http://localhost:8080` and PostgreSQL at
`localhost:5433` by default. The API and database ports are bound to the local
machine for development. Change `API_PORT` or `POSTGRES_PORT` in `.env` if a
port is already in use, then recreate the stack:

```bash
docker compose down
docker compose up --build -d
```

Stop the stack while preserving database data:

```bash
docker compose down
```

To delete the disposable local database and run all migrations again:

```bash
docker compose down --volumes
docker compose up --build -d
```

`docker compose down --volumes` permanently removes the local PostgreSQL
volume. Do not use it if the local data matters.

## API documentation and tools

### OpenAPI and Swagger

OpenAPI is the machine-readable description of the HTTP API: paths, methods,
request fields, response types, authentication, and validation rules. Other
tools can import it to generate client code or collections.

Swagger UI is the browser interface for exploring and calling an OpenAPI
document. It is useful for checking a request before writing frontend or
mobile integration code.

Set this in `.env` for local development and restart the API:

```dotenv
OPENAPI_ENABLED=true
```

Then open:

| Tool | URL |
|---|---|
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| OpenAPI YAML | <http://localhost:8080/v3/api-docs.yaml> |
| Health | <http://localhost:8080/actuator/health> |

To call secured endpoints in Swagger UI:

1. Register and verify an account, or use an existing account.
2. Call `POST /api/auth/login` and copy `accessToken` from the response.
3. Select **Authorize** in Swagger UI.
4. Enter the JWT token and authorize the session.
5. Execute the endpoint you want to test.

The OpenAPI document is also suitable for importing into Postman/Insomnia or
generating a typed client. Do not enable the documentation publicly in a
production deployment unless it is intentionally protected.

### Authentication and request conventions

Most endpoints require:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Access tokens are short-lived. Use `POST /api/auth/refresh` with the refresh
token when an access token expires. IDs are UUIDs and timestamps are returned
as ISO-8601 values. Paginated endpoints use Spring's format, for example:

```text
?page=0&size=20&sort=createdAt,desc
```

The maximum page size is 100. Errors use a common structure:

```json
{
  "timestamp": "2026-10-07T10:46:03Z",
  "status": 400,
  "code": "BAD_REQUEST",
  "message": "Request validation failed",
  "path": "/api/v1/orders"
}
```

Use the HTTP status and `code` rather than matching only on the human-readable
message.

Unless a section shows a request body, `GET`, `DELETE`, and notification
read-marking endpoints do not require JSON input; provide their path variables,
query parameters, and bearer token as applicable.

### What accompanies an endpoint request?

An API request is more than its JSON payload. When integrating, identify each
of these parts:

| Request part | Where it appears | Example |
|---|---|---|
| HTTP method | Request line | `POST`, `GET`, `PATCH`, or `DELETE` |
| Path | URL, including required identifiers | `/api/v1/orders/{orderId}` |
| Query parameters | After `?` in the URL; used for filters and pagination | `?page=0&size=20&sort=createdAt,desc` |
| Headers | HTTP headers | `Authorization: Bearer <accessToken>` |
| JSON body | Request body for create/update operations | `{ "status": "ACTIVE" }` |

For example, changing a member's status requires a UUID in the path, a bearer
token in the header, and a JSON body:

```http
PATCH http://localhost:8080/api/v1/organization/members/<user-id>/status
Authorization: Bearer <access-token>
Content-Type: application/json

{ "status": "SUSPENDED" }
```

For a paginated catalog request, there is no JSON body; the inputs are the
bearer token and query parameters:

```http
GET http://localhost:8080/api/products?category=Food&page=0&size=20
Authorization: Bearer <access-token>
```

The payload examples below document JSON request bodies. Path variables and
query parameters remain part of the endpoint URL shown in the API tables.

## API inventory

The base URL for local development is:

```text
http://localhost:8080
```

The access labels below describe the backend's current authorization rules.
Organization-scoped users can access only data belonging to their own
organization unless an endpoint explicitly says otherwise.

### Authentication and profile

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a customer |
| `POST` | `/api/auth/verify-account` | Public | Verify the account OTP |
| `POST` | `/api/auth/resend-verification` | Public | Resend account-verification OTP |
| `POST` | `/api/auth/login` | Public | Obtain access and refresh tokens |
| `POST` | `/api/auth/refresh` | Public | Rotate/refresh an access token |
| `POST` | `/api/auth/logout` | Public with refresh token | Revoke the supplied refresh token |
| `POST` | `/api/auth/forgot-password` | Public | Request a password-reset OTP |
| `POST` | `/api/auth/reset-password` | Public | Reset a password with an OTP |
| `GET` | `/api/users/me` | Authenticated | Return the current user and organization profile |

Request bodies (JSON) for authentication:

```jsonc
// POST /api/auth/register (CUSTOMER only)
{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "phoneNumber": "254712345678",
  "password": "StrongPassword1!",
  "role": "CUSTOMER"
}

// POST /api/auth/verify-account
{ "identifier": "jane@example.com", "otp": "123456" }

// POST /api/auth/resend-verification or /api/auth/forgot-password
{ "identifier": "jane@example.com" }

// POST /api/auth/login
{ "identifier": "jane@example.com", "password": "StrongPassword1!" }

// POST /api/auth/refresh or /api/auth/logout
{ "refreshToken": "<refresh-token>" }

// POST /api/auth/reset-password
{
  "identifier": "jane@example.com",
  "otp": "123456",
  "newPassword": "NewStrongPassword1!"
}
```

`identifier` may be the account's supported email address or phone number.
Store access and refresh tokens securely on the client; send the access token
on protected requests as `Authorization: Bearer <access-token>`. Verification,
password, and phone values are examples only and must not be hard-coded.

Self-registration is intentionally limited to customers. A manufacturer or
distributor administrator is created after a `SUPER_ADMIN` approves an access
request. Organization staff are created from an invitation issued by an
organization administrator. Customers and `SUPER_ADMIN` accounts do not belong
to an organization; organization users carry exactly one organization claim.

### Platform onboarding and organization invitations

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `POST` | `/api/access-requests` | Public | Request manufacturer/distributor administrator access |
| `GET` | `/api/admin/access-requests` | `SUPER_ADMIN` | List access requests; optional `status` filter |
| `GET` | `/api/admin/access-requests/{requestId}` | `SUPER_ADMIN` | View one access request |
| `GET` | `/api/admin/access-requests/{requestId}/documents/{documentType}` | `SUPER_ADMIN` | Download one private review document; type is `INCORPORATION_CERTIFICATE` or `TAX_CERTIFICATE` |
| `POST` | `/api/admin/access-requests/{requestId}/approve` | `SUPER_ADMIN` | Create the organization and email a one-time activation link |
| `POST` | `/api/admin/access-requests/{requestId}/activation/resend` | `SUPER_ADMIN` | Issue a fresh activation token and resend the activation email |
| `POST` | `/api/admin/access-requests/{requestId}/reject` | `SUPER_ADMIN` | Reject a pending access request |
| `GET` | `/api/admin/users` | `SUPER_ADMIN` | List platform users; supports `organizationId`, `status`, `role`, and pagination filters |
| `PATCH` | `/api/admin/users/{userId}/status` | `SUPER_ADMIN` | Suspend, deactivate, or reactivate a non-platform account |
| `POST` | `/api/access-requests/activate` | Public | Set the approved administrator password using the emailed token |
| `GET` | `/api/v1/organization/invitations` | Organization admin / `SUPER_ADMIN` | List invitations (organization admins see their organization) |
| `POST` | `/api/v1/organization/invitations` | Organization admin / `SUPER_ADMIN` | Invite same-type organization administrators or staff; `SUPER_ADMIN` supplies `organizationId` |
| `DELETE` | `/api/v1/organization/invitations/{id}` | Organization admin / `SUPER_ADMIN` | Revoke a pending invitation |
| `GET` | `/api/invitations/{token}` | Public | Validate an invitation link and show limited metadata |
| `POST` | `/api/invitations/{token}/accept` | Public | Create the invited organization account; accepting the emailed invitation verifies the account email |

The access-request submission uses `multipart/form-data` because it includes two
private organization documents. The application accepts only PDF, JPG, or PNG
content, validates the file signature as well as the declared content type, and
limits each document to 5 MB. Files are stored privately and are not returned as
public URLs.

```bash
# POST /api/access-requests
curl -X POST "$API_URL/api/access-requests" \
  -F 'organizationType=MANUFACTURER' \
  -F 'organizationName=Example Manufacturing Ltd' \
  -F 'country=Kenya' \
  -F 'registrationNumber=CPR-123456' \
  -F 'taxIdentifier=P123456789Z' \
  -F 'fullName=Amina Owner' \
  -F 'representativeRole=Manager' \
  -F 'personalEmail=amina@example.com' \
  -F 'organizationEmail=admin@example-manufacturer.com' \
  -F 'phoneNumber=+254712345678' \
  -F 'authorizedRepresentative=true' \
  -F 'incorporationCertificate=@incorporation.pdf;type=application/pdf' \
  -F 'taxCertificate=@tax-certificate.png;type=image/png'
```

`representativeRole` must be exactly `Manager` or `Supervisor`, and
`authorizedRepresentative` must be `true`. A successful submission returns
`202 Accepted` with the request id and status, and remains pending until a
`SUPER_ADMIN` reviews it. Sensitive registration and tax values are returned
only from authenticated administrator endpoints. Public submissions are also
bounded by a database-coordinated admission window and a total private-document
storage quota, so the limit applies consistently when multiple API instances are
running. Rejected and expired request records remain available for audit, while
their private document bytes are purged after `ONBOARDING_DOCUMENT_RETENTION`.

The remaining onboarding and invitation request bodies are JSON:

```jsonc
// POST /api/access-requests/activate
{
  "email": "amina@example.com",
  "token": "<activation-token>",
  "password": "StrongPassword1!",
  "confirmPassword": "StrongPassword1!"
}

// POST /api/v1/organization/invitations
{
  "fullName": "John Staff",
  "email": "john@example.com",
  "phoneNumber": "254712345678",
  "role": "MANUFACTURER_STAFF"
}

// POST /api/invitations/{token}/accept
{ "phoneNumber": "254712345678", "password": "StrongPassword1!" }

// PATCH .../status (platform or organization member administration)
{ "status": "SUSPENDED" }
```

Submitting an access request creates an in-app notification for each configured
`SUPER_ADMIN` and sends an email notification when mail delivery is enabled.
Requests are persisted even if no `SUPER_ADMIN` account has been bootstrapped
yet, so they become reviewable as soon as the first Super Admin is configured.
Approval and rejection are safe to retry after a timeout: repeating an
approval or rejection returns the existing decision and does not create
another organization, activation token, or notification. The raw activation
token is never returned to the administrator. It is sent to the applicant's
email in the frontend-compatible link:
`/auth/activate?email=...&token=...`.

If an approval email is missed or delivery is temporarily unavailable, an
authenticated `SUPER_ADMIN` can call the activation-resend endpoint. It
invalidates the previous token, creates a new expiry window, and sends a new
email without exposing the token in the API response. The endpoint is valid
only while the request is approved and not yet activated.

For a `SUPER_ADMIN` creating an invitation, include `organizationId` in the
invitation body. Organization administrators omit it; the server derives the
organization from the authenticated account. Valid invitation roles are
organization administrator/staff roles permitted by the caller's scope.

`SUPER_ADMIN` is a platform role, not a tenant role. It is never accepted by
public registration and should be bootstrapped through a controlled database
operation or deployment process. Organization administrator roles remain
`MANUFACTURER_ADMIN` and `DISTRIBUTOR_ADMIN`; they are scoped to their own
organization and cannot access `/api/admin/**`. An organization administrator
may invite another administrator only for the same organization type, or may
invite staff. Member status management is available below; an ownership-transfer
workflow is still required before transferring the last administrator's
responsibilities to another person or organization.

Organization membership administration is available through:

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/organization/members` | Organization admin | List members in the caller's organization |
| `PATCH` | `/api/v1/organization/members/{userId}/status` | Organization admin | Suspend, deactivate, or reactivate another member in the caller's organization |

The member-status payload is the same as the platform status payload:

```json
{ "status": "ACTIVE" }
```

Organization profile endpoints:

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/organization` | Organization admin/staff | Read the caller's organization profile |
| `PATCH` | `/api/v1/organization` | Organization admin | Update name or email using the current `version` |

Organization profile updates use optimistic concurrency. The client must send
the `version` returned by the latest response; a stale version returns `409`
and the client should reload before retrying. Organization type is assigned by
the platform and cannot be changed through this endpoint.

### Product catalog, publication, and distributor authorization

Products are manufacturer-owned source products. A distributor must receive
manufacturer approval before creating a warehouse offer for that product.
Customers browse published distributor offers that have an active warehouse;
they do not browse manufacturer source inventory directly.

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/products` | Authenticated catalog role | Browse products visible to the caller; supports `category`, `page`, `size`, and `sort` |
| `GET` | `/api/v1/products/{productId}` | Authenticated catalog role | Read one visible published product |
| `GET` | `/api/v1/products/categories` | Authenticated catalog role | List categories visible to the caller |
| `GET` | `/api/v1/products/{productId}/image` | Authenticated catalog role | Stream a private product image; no public file URL is exposed |
| `POST` | `/api/v1/products` | Manufacturer admin/staff | Create a source product with a multipart image |
| `PATCH` | `/api/v1/products/{productId}` | Manufacturer admin/staff | Update source metadata and optionally replace the image using multipart form data |
| `PATCH` | `/api/v1/products/{productId}/publication` | Manufacturer admin | Set `DRAFT`, `PUBLISHED`, or `DISCONTINUED` |
| `GET` | `/api/v1/products/authorizations` | Distributor admin/staff | List that distributor's authorization requests |
| `POST` | `/api/v1/products/{productId}/authorization-requests` | Distributor admin/staff | Request permission to offer a published manufacturer product |
| `GET` | `/api/v1/products/authorizations/incoming` | Manufacturer admin/staff | List requests for that manufacturer's products |
| `PATCH` | `/api/v1/products/authorizations/{authorizationId}/decision` | Manufacturer admin | Approve or reject a pending request |
| `PATCH` | `/api/v1/products/authorizations/{authorizationId}/revocation` | Manufacturer admin | Revoke an approved authorization |

Source-product creation uses `multipart/form-data`. The required text fields
include `name`, `sku`, `unitPrice`, `initialQuantity`, `reorderThreshold`,
`publicationStatus`, and `image`; optional fields are `category`, `brand`,
`unit`, and `description`. Images must be genuine PNG, JPEG, or WebP files and
are limited to 5 MiB. A product can only be published after an image is
stored. Authorization decisions use JSON such as:

```json
{ "status": "APPROVED", "note": "Approved for the Nairobi warehouse" }
```

The valid authorization transitions are `PENDING` to `APPROVED` or
`REJECTED`, and `APPROVED` to `REVOKED`. Draft and discontinued source
products, inactive warehouses, and offers whose source product is no longer
published are excluded from customer and distributor catalogs.

Status changes revoke refresh tokens for disabled accounts. Existing access
tokens are also checked against the current database account, role, and
organization on every authenticated request. An organization must retain at
least one active administrator, and administrators cannot change their own
status.

For a new environment, the recommended first-admin bootstrap uses the
application's `CommandLineRunner`, rather than storing a password in SQL or
source code. From the repository root, set the following values in the local
environment file (never commit them):

```dotenv
BOOTSTRAP_SUPER_ADMIN_ENABLED=true
BOOTSTRAP_SUPER_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_SUPER_ADMIN_PASSWORD=REPLACE_ME_WITH_A_UNIQUE_PASSWORD
BOOTSTRAP_SUPER_ADMIN_FULL_NAME=Platform Administrator
BOOTSTRAP_SUPER_ADMIN_PHONE=+254700000000
```

Replace the password sentinel with a unique password before starting the API.
It must satisfy the normal application policy: 8–72 characters containing an
uppercase letter, a lowercase letter, and a digit. Use a longer password for
the platform administrator in production. The password is preserved exactly;
do not add leading or trailing whitespace accidentally.

Start the API once with `docker compose up --build -d`. The initializer creates
the account with a BCrypt password hash, `SUPER_ADMIN` role, no organization,
and verified email/phone flags. If the same email already belongs to an
unscoped `SUPER_ADMIN`, bootstrap reconciles its configured name, phone,
password, active status, and verification flags. This is intentional but means
the bootstrap password can rotate that account on every startup while the flag
is enabled. Disable it immediately after the controlled operation. If the email
belongs to a tenant or another non-platform account, startup fails before any
account is changed.

The account-collision failure is deliberately operator-visible rather than an
email notification: the service must not start while the configured bootstrap
identity is ambiguous. Check the API logs (`docker compose logs api`), then
either set `BOOTSTRAP_SUPER_ADMIN_ENABLED=false` or choose a dedicated email
that is not already assigned to a tenant account. The log and startup exception
include this remediation so the operator is not left with an unexplained
failure.

After confirming that the account can log in, set
`BOOTSTRAP_SUPER_ADMIN_ENABLED=false`, clear the four bootstrap values from
`.env`, and recreate the API container:

```powershell
docker compose up -d --force-recreate api
```

For an existing environment, an operator with database access can still use a
carefully reviewed SQL promotion as a recovery procedure. Replace the email
with an already active, verified account:

```powershell
docker compose exec db psql -U distro -d distro_backend -c "UPDATE users SET role = 'SUPER_ADMIN', organization_id = NULL WHERE lower(email) = lower('owner@example.com') AND is_phone_verified = true AND status = 'ACTIVE';"
```

The command should be executed only by an operator who already has database
access. Revoke existing sessions or sign in again after promotion so a fresh
JWT contains the new role. Do not expose a public endpoint for assigning
`SUPER_ADMIN`, and do not leave the bootstrap password enabled in a long-running
production deployment.

For production, set `SPRING_PROFILES_ACTIVE=prod` and configure the real OTP
provider variables. `OTP_PROVIDER=mailgun` sends OTPs through Mailgun's HTTPS
API and works with the sandbox domain for up to five individually verified
recipient addresses. Set `MAILGUN_API_KEY` and `MAILGUN_DOMAIN` in Render; use
`MAILGUN_API_BASE_URL=https://api.mailgun.net` for the US region or
`https://api.eu.mailgun.net` for the EU region. Set `MAIL_ENABLED=true` to send
post-approval activation links and invitations through the same Mailgun HTTPS
API. The local profile logs OTP codes for development and must not be used for
a live deployment.

### Platform administration API

These endpoints are intended for the platform administration console and
require a `SUPER_ADMIN` access token unless stated otherwise. Collection
endpoints support Spring pagination parameters such as `page`, `size`, and
`sort`.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/admin/dashboard` | Platform-wide operational counts |
| `GET` | `/api/organizations` | List organizations |
| `GET` | `/api/organizations/{organizationId}` | View one organization |
| `POST` | `/api/organizations` | Create an organization record |
| `GET` | `/api/admin/users` | Search platform users by organization, status, role, and page |
| `GET` | `/api/admin/users/{userId}` | View one platform user |
| `PATCH` | `/api/admin/users/{userId}/status` | Activate, suspend, or deactivate a user |
| `DELETE` | `/api/admin/users/{userId}` | Anonymize/deactivate a non-platform user |
| `GET` | `/api/admin/stock` | List platform inventory |
| `GET` | `/api/admin/stock/{inventoryId}` | View one inventory record |
| `POST` | `/api/admin/stock` | Create manufacturer source inventory |
| `PATCH` | `/api/admin/stock/{inventoryId}` | Update inventory metadata |
| `GET` | `/api/admin/stock/alerts` | List low-stock inventory |
| `GET` | `/api/admin/stock/{inventoryId}/movements` | View movement history |
| `POST` | `/api/admin/stock/{inventoryId}/movements` | Record a controlled inventory adjustment |
| `GET` | `/api/admin/orders` | List platform orders |
| `GET` | `/api/admin/orders/{orderId}` | View one platform order |
| `PATCH` | `/api/admin/orders/{orderId}/status` | Apply a permitted administrative order transition |
| `GET` | `/api/v1/payments` | List payments visible to the authenticated role |
| `GET` | `/api/v1/payments/{paymentId}` | View one payment |
| `PATCH` | `/api/v1/admin/payments/{paymentId}/status` | Mark an unsettled payment failed; successful confirmation only comes from verified callbacks |
| `GET` | `/api/v1/admin/payments` | Backward-compatible paginated platform payment listing |
| `GET` | `/api/v1/trips` | List trips visible to the authenticated role |
| `GET` | `/api/v1/trips/{tripId}` | View one trip |
| `PATCH` | `/api/v1/trips/{tripId}/driver` | Assign a driver; `/assign` remains a compatibility alias |
| `PATCH` | `/api/v1/trips/{tripId}/status` | Update a valid trip status |
| `GET` | `/api/v1/trips/{tripId}/tracking` | Read persisted tracking points |
| `GET` | `/api/v1/reports/overview` | Platform overview report |
| `GET` | `/api/v1/reports/revenue` | Revenue and payment-status report |
| `GET` | `/api/v1/reports/orders` | Order totals and status counts |
| `GET` | `/api/v1/reports/payments` | Payment totals and status counts |
| `GET` | `/api/v1/reports/deliveries` | Trip/delivery status counts |
| `GET` | `/api/v1/reports/inventory` | Inventory summary |
| `GET` | `/api/v1/analytics` | Platform analytics summary |
| `GET` | `/api/v1/analytics/organizations` | Per-organization operational analytics |
| `GET` | `/api/v1/audit-logs` | Search audit records |
| `GET` | `/api/v1/audit-logs/{auditLogId}` | View one audit record |
| `GET` | `/api/v1/audit-logs/user/{userId}` | Filter audit records by actor |
| `GET` | `/api/v1/audit-logs/organization/{organizationId}` | Filter audit records by organization |
| `GET` | `/api/v1/audit-logs/resource` | Filter audit records by resource |
| `GET` | `/api/v1/settings` | Read typed platform settings |
| `PUT` | `/api/v1/settings` | Update typed platform settings |

Representative JSON bodies:

```jsonc
// POST /api/organizations
{
  "name": "Example Manufacturing Ltd",
  "email": "operations@example.com",
  "type": "MANUFACTURER"
}

// PATCH /api/admin/users/{userId}/status
{ "status": "SUSPENDED" }

// POST /api/admin/stock
{
  "organizationId": "<manufacturer-organization-id>",
  "sku": "SKU-1001",
  "name": "25 kg maize flour",
  "category": "Food",
  "unitPrice": 1250.00,
  "initialQuantity": 100,
  "reorderThreshold": 20
}

// PATCH /api/admin/orders/{orderId}/status
{ "status": "FAILED", "note": "Administrative review" }

// PUT /api/v1/settings
{
  "maintenanceMode": false,
  "supportEmail": "support@example.com",
  "defaultDeliveryFee": 200.00
}
```

The platform user delete operation is a soft-delete/anonymization operation;
it preserves order, payment, and audit references. It cannot delete a
`SUPER_ADMIN`, delete the calling administrator, or remove the last active
administrator of an organization. Inventory and payment mutations are
validated and locked by the service layer. An accepted M-Pesa payment must be
reconciled before its reservation can be failed administratively. Clients
should treat `409 Conflict` as a concurrency or state-transition response and
refresh the affected record.

### Product catalog, offers, warehouses, and stock

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `GET` | `/api/products` | Authenticated catalog roles | Browse active catalog products; supports `category` and pagination |
| `GET` | `/api/products/categories` | Authenticated catalog roles | List categories visible to the caller |
| `POST` | `/api/products` | Manufacturer admin/staff | Create a manufacturer source product |
| `PATCH` | `/api/products/{id}` | Manufacturer admin/staff | Update a manufacturer source product |
| `GET` | `/api/v1/products` | Customer, manufacturer/distributor roles, or `SUPER_ADMIN` | Versioned product/offer catalog; platform admins see active products across organizations |
| `GET` | `/api/v1/products/{productId}` | Customer, manufacturer/distributor roles, or `SUPER_ADMIN` | Read one visible product/offer; platform admins can read any active product |
| `GET` | `/api/v1/products/categories` | Customer, manufacturer/distributor roles, or `SUPER_ADMIN` | List visible categories; platform admins see categories across active products |
| `GET` | `/api/v1/products/{productId}/inventory` | Authorized inventory scope | Read inventory details |
| `GET` | `/api/v1/products/stats` | Manufacturer, distributor, or platform scope | Read product statistics |
| `POST` | `/api/v1/products` | Manufacturer admin/staff | Create a manufacturer source product |
| `PUT` | `/api/v1/products/{productId}` | Manufacturer admin/staff | Update a manufacturer source product |
| `PATCH` | `/api/v1/products/{productId}/status` | Manufacturer admin/staff | Activate/deactivate own product |
| `PATCH` | `/api/v1/products/{productId}/approval` | `SUPER_ADMIN` | Apply platform approval state |
| `POST` | `/api/v1/warehouses` | Manufacturer/distributor admin | Create a warehouse owned by the caller's organization |
| `GET` | `/api/v1/warehouses` | Manufacturer/distributor admin/staff | List all warehouses owned by the caller's organization |
| `GET` | `/api/v1/warehouses/{warehouseId}` | Manufacturer/distributor admin/staff | Read one tenant-owned warehouse |
| `PATCH` | `/api/v1/warehouses/{warehouseId}` | Manufacturer/distributor admin | Update warehouse name/address using the response `version` |
| `PATCH` | `/api/v1/warehouses/{warehouseId}/status` | Manufacturer/distributor admin | Activate/deactivate a warehouse; active stock blocks deactivation |
| `GET` | `/api/stock` | Manufacturer/distributor admin/staff | List organization stock records |
| `GET` | `/api/stock/items` | Manufacturer/distributor admin/staff | Same organization stock listing under the inventory path |
| `GET` | `/api/stock/items/{id}` | Manufacturer/distributor admin/staff | Get one organization stock record |
| `GET` | `/api/stock/items/low-stock` | Manufacturer/distributor admin/staff | List low-stock records |
| `POST` | `/api/stock/items` | Manufacturer admin/staff | Create a manufacturer source product with opening quantity |
| `PATCH` | `/api/stock/items/{id}` | Manufacturer admin/staff | Update source-product fields |
| `POST` | `/api/stock/offers` | Distributor admin/staff | Create an offer linked to a manufacturer product and warehouse |
| `PATCH` | `/api/stock/offers/{id}` | Distributor admin/staff | Update offer price, threshold, or active state |
| `GET` | `/api/stock/{organizationId}/availability` | Distributor admin/staff | View active inventory for the caller's distributor organization |
| `POST` | `/api/stock/items/{id}/movements` | Manufacturer/distributor admin/staff | Record a stock movement |
| `GET` | `/api/stock/items/{id}/movements` | Manufacturer/distributor admin/staff | Read movement history |

Product, warehouse, offer, and stock request bodies (JSON):

```jsonc
// POST /api/products or POST /api/stock/items
{
  "sku": "SKU-1001",
  "name": "25 kg maize flour",
  "category": "Food",
  "unitPrice": 1250.00,
  "initialQuantity": 100,
  "reorderThreshold": 20
}

// PATCH /api/products/{id} or PATCH /api/stock/items/{id}
{
  "name": "25 kg premium maize flour",
  "category": "Food",
  "unitPrice": 1300.00,
  "reorderThreshold": 25,
  "active": true
}

// POST /api/v1/warehouses
{
  "code": "NAI-001",
  "name": "Nairobi Warehouse",
  "address": "Industrial Area, Nairobi"
}

// PATCH /api/v1/organization
{
  "name": "Updated Organization Name",
  "email": "operations@example.com",
  "version": 0
}

// PATCH /api/v1/warehouses/{warehouseId}
{
  "name": "Nairobi Distribution Warehouse",
  "address": "Industrial Area, Nairobi",
  "version": 0
}

// PATCH /api/v1/warehouses/{warehouseId}/status
{ "active": false, "version": 1 }

// POST /api/stock/offers
{
  "sourceStockItemId": "<manufacturer-product-id>",
  "warehouseId": "<distributor-warehouse-id>",
  "sku": "DIST-NAI-1001",
  "sellingPrice": 1400.00,
  "reorderThreshold": 10
}

// PATCH /api/stock/offers/{id}
{ "sellingPrice": 1450.00, "reorderThreshold": 12, "active": true }

// POST /api/stock/items/{id}/movements
{
  "movementType": "RESTOCK_IN",
  "quantityDelta": 50,
  "referenceType": "PURCHASE_ORDER",
  "referenceId": "<purchase-order-id>",
  "note": "Received delivery against approved purchase order"
}

// POST /api/stock/restock-request
{
  "stockItemId": "<distributor-offer-id>",
  "manufacturerOrganizationId": "<manufacturer-organization-id>",
  "requestedQuantity": 100,
  "note": "Replenishment for Nairobi warehouse"
}

// PATCH /api/stock/restock-request/{id}/status
{ "status": "APPROVED", "note": "Approved for next dispatch" }
```

The `category`, `sku`, and text fields have server-side length limits. Stock
movement deltas must agree with the movement type; for example, an inbound
restock uses a positive delta and an outbound sale uses a negative delta.
Pagination uses Spring parameters such as `?page=0&size=20&sort=name,asc`.

The catalog uses the current hybrid model. Manufacturers own source products;
distributors create warehouse-backed offers for those products; customers
order against distributor offer IDs. `POST /api/v1/orders` therefore expects
the selected offer ID in each item's `stockItemId`, not a manufacturer source
product ID.

Catalog visibility is role-aware: customers see active distributor offers,
manufacturers see their own source products, and distributors see manufacturer
source products plus their own eligible offers. Drivers do not use the catalog
endpoints.

Stock adjustments are validated against the movement type and are protected by
organization ownership and database locking. Orders reserve offer quantity
atomically; insufficient stock returns an error and creates a distributor
notification.

### Orders

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `GET` | `/api/v1/orders` | Customer or distributor admin/staff | Customer's orders or distributor organization's orders |
| `POST` | `/api/v1/orders` | Customer | Create an order from distributor offer IDs |
| `GET` | `/api/v1/orders/{orderId}` | Customer owner or distributor organization | Get order details |
| `GET` | `/api/v1/orders/{orderId}/items` | Customer owner or distributor organization | Get order items |
| `GET` | `/api/v1/orders/stats` | Customer, distributor, or `SUPER_ADMIN` | Return order totals and status counts |
| `PATCH` | `/api/v1/orders/{orderId}/status` | Customer or distributor admin/staff | Customer may cancel only an unpaid pending order; distributor staff use valid operational transitions |
| `POST` | `/api/v1/orders/{orderId}/delivery-otp` | Customer order owner | Request delivery OTP while the order is `IN_TRANSIT` |
| `PUT` | `/api/v1/orders/{orderId}` | Distributor admin/staff | Update permitted pending-order delivery fields and reserved item quantities |
| `GET` | `/api/v1/orders/{orderId}/payments` | Customer owner, distributor organization, or `SUPER_ADMIN` | Read the order payment |

Order and payment request bodies (JSON):

```jsonc
// POST /api/v1/orders
{
  "organizationId": "<distributor-organization-id>",
  "deliveryAddress": "Westlands, Nairobi",
  "deliveryLat": -1.2676,
  "deliveryLng": 36.8108,
  "items": [
    {
      "stockItemId": "<distributor-offer-id>",
      "quantity": 2
    }
  ]
}

// PATCH /api/v1/orders/{orderId}/status
{ "status": "CANCELLED", "note": "Customer requested cancellation" }

// POST /api/v1/payments/initiate
{
  "orderId": "<order-id>",
  "phoneNumber": "254712345678",
  "idempotencyKey": "checkout-<client-generated-unique-key>"
}
```

`POST /api/v1/orders` is customer-only. The customer identity is taken from
the access token, not from the request body. The order organization must be a
distributor organization and every item must be an eligible offer visible to
that distributor. Customers should submit a unique idempotency key when they
retry payment initiation.

The M-Pesa callback is sent by Daraja, not by the frontend or mobile client.
It must include the configured `X-Mpesa-Callback-Secret` header and the
provider's callback shape, for example:

```json
{
  "Body": {
    "stkCallback": {
      "MerchantRequestID": "<merchant-request-id>",
      "CheckoutRequestID": "<checkout-request-id>",
      "ResultCode": 0,
      "ResultDesc": "The service request is processed successfully.",
      "CallbackMetadata": {
        "Item": [
          { "Name": "Amount", "Value": 1400 },
          { "Name": "MpesaReceiptNumber", "Value": "<receipt-number>" },
          { "Name": "PhoneNumber", "Value": 254712345678 }
        ]
      }
    }
  }
}
```

Order creation reserves stock immediately. An unpaid reservation expires after
`ORDER_RESERVATION_TTL`; the scheduled cleanup releases it. If an accepted
M-Pesa request is still in flight, cleanup reconciles the provider first so a
successful payment is not stranded.

### Payments

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `POST` | `/api/v1/payments/initiate` | Customer or distributor admin/staff with order access | Start an M-Pesa STK Push; send `orderId`, Kenyan `phoneNumber`, and optional idempotency key |
| `GET` | `/api/v1/payments` | Authenticated payment roles | List payments visible to the caller |
| `GET` | `/api/v1/payments/{paymentId}` | Authorized payment participant | Read one payment |
| `PATCH` | `/api/v1/admin/payments/{paymentId}/status` | `SUPER_ADMIN` | Mark an unsettled payment failed with a reason; cannot forge confirmation or manually reverse a payment |
| `GET` | `/api/v1/payments/{orderId}/status` | Customer owner or distributor organization | Read persisted payment status |
| `POST` | `/api/v1/payments/mpesa/callback` | M-Pesa gateway only | Public HTTP route; requires `X-Mpesa-Callback-Secret` and provider verification |

Payment initiation requires valid Daraja configuration. The callback URL must
be reachable by Safaricom/Daraja; a localhost URL is not reachable from the
provider. Callback confirmation also requires the shared secret and provider
result verification. Never disable callback verification in an environment
that handles real payments.

Typical payment status values are `INITIATING`, `PENDING`, `CONFIRMED`,
`RECONCILED`, `FAILED`, and `REVERSED`. A successful STK response means the
request was accepted, not that the order has been paid; poll the status endpoint
and rely on the verified callback/reconciliation flow.

### Procurement and restocking

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `POST` | `/api/v1/purchase-orders` | Distributor admin/staff | Request manufacturer stock |
| `GET` | `/api/v1/purchase-orders` | Distributor admin/staff | List purchase orders for the distributor organization |
| `GET` | `/api/v1/purchase-orders/{purchaseOrderId}` | Distributor admin/staff | Get a distributor-owned purchase order |
| `GET` | `/api/v1/purchase-orders/{purchaseOrderId}/settlements` | Authorized distributor/manufacturer party | Read settlement ledger entries |
| `GET` | `/api/v1/manufacturer/purchase-orders` | Manufacturer admin/staff | List purchase orders addressed to the manufacturer organization |
| `GET` | `/api/v1/manufacturer/purchase-orders/{purchaseOrderId}` | Manufacturer admin/staff | Get a manufacturer-owned purchase order |
| `PATCH` | `/api/v1/manufacturer/purchase-orders/{purchaseOrderId}/decision` | Manufacturer admin | Approve or reject a submitted purchase order |
| `GET` | `/api/v1/manufacturer/purchase-orders/{purchaseOrderId}/settlements` | Authorized manufacturer party | Read settlement ledger entries |
| `POST` | `/api/stock/restock-request` | Distributor admin/staff | Create a restock request for a distributor offer |
| `GET` | `/api/stock/restock-request` | Manufacturer/distributor admin/staff | List organization-visible restock requests |
| `PATCH` | `/api/stock/restock-request/{id}/status` | Manufacturer/distributor admin/staff | Update a restock request status |

Purchase-order request bodies (JSON):

```jsonc
// POST /api/v1/purchase-orders
{
  "supplierOrganizationId": "<manufacturer-organization-id>",
  "items": [
    {
      "stockItemId": "<manufacturer-product-id>",
      "quantity": 100
    }
  ]
}

// PATCH /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/decision
{
  "status": "APPROVED",
  "note": "Approved for production and dispatch"
}
```

Purchase-order status values are `SUBMITTED`, `APPROVED`, `REJECTED`,
`CANCELLED`, and `FULFILLED`. The distributor creates the purchase order;
only a manufacturer administrator can approve or reject it. Settlement
endpoints currently return the read-only settlement ledger and do not initiate
money transfers.

Purchase-order settlements are currently a read-only `PENDING` ledger. Payment
reconciliation, refunds, reversals, and platform-level settlement reporting are
not part of the current integration surface.

### Trips, delivery, and live tracking

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `POST` | `/api/v1/trips` | Organization operations users | Create a delivery or restock trip with stops |
| `PATCH` | `/api/v1/trips/{tripId}/assign` | Organization operations users | Assign a driver belonging to the trip organization |
| `PATCH` | `/api/v1/trips/{tripId}/driver` | Organization operations users | Canonical driver-assignment route; `/assign` is retained as an alias |
| `GET` | `/api/v1/trips` | Authorized trip participant | List visible trips |
| `GET` | `/api/v1/trips/{tripId}` | Authorized trip participant | Read one trip |
| `PATCH` | `/api/v1/trips/{tripId}/status` | Organization operations users | Update a valid trip status |
| `GET` | `/api/v1/trips/{tripId}/tracking` | Authorized trip participant | Read tracking history |
| `GET` | `/api/v1/trips/active` | Driver | List the driver's active trips |
| `GET` | `/api/v1/trips/history` | Driver | List the driver's completed/history trips |
| `POST` | `/api/v1/trips/{tripId}/location` | Assigned driver | Record a latitude/longitude ping |
| `GET` | `/api/v1/trips/{tripId}/location` | Authorized trip participant | Read persisted location history |
| `POST` | `/api/v1/trips/{tripId}/stops/{stopId}/confirm` | Assigned driver | Confirm delivery using the customer's six-digit OTP |
| `POST` | `/api/v1/trips/{tripId}/stops/{stopId}/delivery-otp` | Customer for the order | Request the delivery OTP |

Trip and live-tracking request bodies (JSON):

```jsonc
// POST /api/v1/trips
{
  "tripType": "DELIVERY",
  "origin": {
    "name": "Nairobi Warehouse",
    "address": "Industrial Area, Nairobi",
    "latitude": -1.2921,
    "longitude": 36.8219
  },
  "scheduledStartLabel": "2026-10-14 09:00 EAT",
  "totalDistanceKm": 18,
  "stops": [
    {
      "sequence": 1,
      "location": {
        "name": "Customer delivery point",
        "address": "Westlands, Nairobi",
        "latitude": -1.2676,
        "longitude": 36.8108
      },
      "etaLabel": "2026-10-14 10:00 EAT",
      "distanceFromPreviousKm": 18,
      "orderId": "<order-id>",
      "contactName": "Jane Doe",
      "contactPhone": "254712345678"
    }
  ]
}

// PATCH /api/v1/trips/{tripId}/assign
{ "riderId": "<driver-user-id>" }

// POST /api/v1/trips/{tripId}/location
{ "lat": -1.2676, "lng": 36.8108 }

// POST /api/v1/trips/{tripId}/stops/{stopId}/confirm
{ "otp": "123456" }
```

Example `RESTOCK` stop fields:

```json
{
  "sequence": 1,
  "location": {
    "name": "Manufacturer depot",
    "address": "Nairobi, Kenya",
    "latitude": -1.2921,
    "longitude": 36.8219
  },
  "stockItemId": "<distributor-stock-item-id>",
  "restockQuantity": 100
}
```

For a `DELIVERY` trip, each stop must include `orderId` and omit
`stockItemId`. For a `RESTOCK` trip, each stop must include the distributor's
`stockItemId` and a positive `restockQuantity`, and omit `orderId`. Drivers can
record location only for trips assigned to them. Location coordinates are
validated against latitude/longitude ranges. Delivery OTPs are requested by
the customer and submitted by the assigned driver.

### Driver onboarding and fleet dispatch

Drivers are organization users with `DRIVER` role and `INVITED` status until
they accept their email invitation. An invited driver cannot log in or receive
trip assignments. After accepting, the driver must verify the account-
verification code sent through the configured OTP provider before the first
login. Drivers are currently
onboarded by distributor administrators because distributors own the delivery
fleet.

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/drivers/invitations` | Distributor admin | Create an invited driver and send the email activation link |
| `POST` | `/api/v1/drivers/{driverId}/invitation` | Distributor admin | Reissue an expired driver invitation |
| `GET` | `/api/v1/drivers` | Distributor staff/admin | List drivers in the caller's distributor organization |
| `GET` | `/api/v1/drivers/{driverId}` | Distributor staff/admin or that driver | Read one organization driver |
| `PATCH` | `/api/v1/drivers/{driverId}` | Distributor admin | Update driver profile and license details |
| `PATCH` | `/api/v1/drivers/{driverId}/status` | Distributor admin | Activate, suspend, or deactivate a driver |
| `PATCH` | `/api/v1/drivers/{driverId}/availability` | Driver or distributor admin | Set whether an active driver is available |
| `POST` | `/api/v1/drivers/me/location` | Driver | Update the driver's latest profile location |
| `GET` | `/api/v1/vehicles` | Distributor staff/admin | List organization vehicles; optional `status` filter |
| `GET` | `/api/v1/vehicles/{vehicleId}` | Distributor staff/admin | Read one organization vehicle |
| `POST` | `/api/v1/vehicles` | Distributor admin | Register a vehicle |
| `PATCH` | `/api/v1/vehicles/{vehicleId}` | Distributor admin | Update vehicle details |
| `PATCH` | `/api/v1/vehicles/{vehicleId}/status` | Distributor admin | Set `ACTIVE`, `INACTIVE`, or `MAINTENANCE` |
| `PATCH` | `/api/v1/trips/{tripId}/assign` | Organization admin/staff | Assign an active driver and optional active vehicle |
| `POST` | `/api/v1/trips/{tripId}/actions` | Assigned driver or organization staff | Record delay, reroute, pause, resume, or cancellation action |
| `GET` | `/api/v1/trips/{tripId}/actions` | Authorized trip viewer | Read the trip operational-action history |

Driver invitation payload:

```json
{
  "fullName": "Brian Driver",
  "email": "brian@example.com",
  "phoneNumber": "+254700000000",
  "licenseNumber": "DL-123456"
}
```

Vehicle payload:

```json
{
  "registrationNumber": "KDA 123A",
  "vehicleType": "VAN",
  "make": "Toyota",
  "model": "Hiace",
  "capacityKg": 1200
}
```

Trip assignment accepts both resources and validates that neither is already
assigned to another scheduled, ongoing, or delayed trip. Omitting `vehicleId`
leaves an existing vehicle assignment unchanged. To explicitly remove the
vehicle assignment, send `clearVehicle: true` and omit `vehicleId`:

```json
{
  "riderId": "<driver-user-id>",
  "vehicleId": "<vehicle-id>",
  "clearVehicle": false
}
```

The operational-action history is available to the assigned driver,
organization staff/admin for the trip's organization, and `SUPER_ADMIN`.
Customers can view permitted trip details but cannot read internal dispatch
notes or actor identifiers.

Operational actions require a non-blank note. Drivers may report delays and
reroutes for their own assigned trip; organization staff may perform the
broader operational actions within their organization.

### Notifications and organization workspaces

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/notifications` | Manufacturer/distributor admin/staff | Read the caller's organization notification feed |
| `GET` | `/api/notifications` | Authenticated user | Read the caller's personal notifications |
| `GET` | `/api/notifications/unread-count` | Authenticated user | Count unread personal notifications |
| `PATCH` | `/api/notifications/{id}/read` | Notification owner | Mark one personal notification as read |
| `PATCH` | `/api/notifications/read-all` | Authenticated user | Mark all personal notifications as read |
| `GET` | `/api/manufacturer/dashboard` | Manufacturer admin/staff | Current manufacturer workspace placeholder |
| `GET` | `/api/manufacturer/staff` | Manufacturer admin | Current manufacturer staff placeholder |
| `GET` | `/api/distributor/dashboard` | Distributor admin/staff | Current distributor workspace placeholder |
| `GET` | `/api/distributor/staff` | Distributor admin | Current distributor staff placeholder |

## WebSocket/STOMP live updates

The WebSocket handshake is:

```text
ws://localhost:8080/ws
```

The backend uses STOMP with:

- application destination prefix: `/app`
- broker destination prefix: `/topic`
- user destination prefix: `/user`

Clients must send a valid JWT in the STOMP `CONNECT` frame's `Authorization`
header:

```text
Authorization: Bearer <accessToken>
```

The HTTP WebSocket upgrade itself is permitted so browser clients can connect;
authorization is enforced when STOMP connects and subscribes.

Available subscriptions include:

```text
/topic/trips/{tripId}/location
/topic/organizations/{organizationId}/notifications
```

Trip and organization ownership is checked before subscription. Use the REST
location-history endpoint when a client needs persisted history; use the
WebSocket subscription for live updates.

## Host-based development without the API container

This is optional. It runs PostgreSQL in Docker and Spring Boot through Maven.
Do not run the Docker API service at the same time if both would use port 8080.

Start only PostgreSQL:

```bash
docker compose up -d db
```

The `.env` file is automatically read by Docker Compose, but Maven does not
automatically load `.env`. Set the database and application variables in the
terminal that starts Maven.

PowerShell example:

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:5433/distro_backend'
$env:DATABASE_USERNAME='distro'
$env:DATABASE_PASSWORD='<same value as POSTGRES_PASSWORD in .env>'
$env:JWT_SECRET='<same generated JWT secret>'
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200,http://127.0.0.1:4200'
$env:SERVER_PORT='8080'
mvn spring-boot:run
```

macOS/Linux uses the equivalent `export NAME=value` syntax. Run the command
from the repository root.

## Integration status

Completed and available for frontend/mobile integration:

- authentication, account verification, login, refresh, logout, and password reset;
- current-user profile;
- manufacturer product catalog and distributor offer catalog;
- distributor warehouses and stock/movement APIs;
- atomic order reservation and order status flow;
- M-Pesa STK initiation, status lookup, verified callback, and reconciliation;
- distributor/manufacturer purchase orders and restock requests;
- trip assignment, driver delivery confirmation, OTP delivery flow, and location tracking;
- organization notifications and WebSocket subscriptions.
- platform organization, user, dashboard, inventory, order, payment, trip,
  report, analytics, audit-log, and settings APIs listed in the platform
  administration contract above.

Known limitations to account for during integration:

- platform settings, report, analytics, and audit-log endpoints are contract
  surfaces; audit-log ingestion and settlement automation remain separate
  follow-up capabilities;
- payment refunds, reversals, reconciliation reports, and settlement processing are not exposed as APIs;
- an M-Pesa callback requires a reachable callback URL, a configured shared secret, and provider verification;
- the catalog/order model currently represents distributor offers as stock items, so customers order distributor offer IDs.

When an endpoint or request contract changes, update this inventory and the
OpenAPI annotations/configuration in the same change.

## Database and migrations

Flyway owns schema changes. Hibernate is configured with `ddl-auto=validate`,
so it validates the schema but does not create or alter tables.

Migration files are in:

```text
src/main/resources/db/migration/
```

For a new Docker database, no manual schema setup is required. Flyway runs on
API startup. For a database that already contains data, do not edit applied
migrations or use `flyway repair` to hide a mismatch. Review the existing
schema and organization ownership data first, then follow [the Flyway
migration-history reconciliation runbook](docs/flyway-migration-reconciliation.md).
The deployed Supabase database uses V29 for the order-version migration and
V34 for the organization-version migration; this history must not be changed
by renaming migration files.

Useful local database commands:

```bash
docker compose exec db psql -U distro -d distro_backend
```

Inside `psql`, use `\dt` to list tables and `\q` to exit.

## Build and test

Run from the repository root:

```bash
mvn -B -ntp -DskipTests package
mvn -B -ntp test
```

Tests that load the Spring application context require PostgreSQL and the
database/JWT environment variables described in the host-based workflow.

If the IDE shows unresolved Maven or JUnit imports after `pom.xml` changes,
reload/reimport the Maven project and restart the Java language server.

## Troubleshooting

### API does not start

```bash
docker compose ps
docker compose logs --tail=150 api
docker compose logs --tail=100 db
```

Wait for the database health check and Flyway startup to finish. If PostgreSQL
reports authentication failure, remember that the password is initialized
when the Docker volume is first created. Changing `POSTGRES_PASSWORD` later
does not change an existing volume; restore the original password or recreate
the disposable volume with `docker compose down --volumes`.

### Port already in use

Set `API_PORT` or `POSTGRES_PORT` in `.env`, then recreate the stack. When
running Maven on the host, the JDBC URL must use the host PostgreSQL port, for
example `5433`; inside Docker, the API uses `db:5432`.

### Frontend/mobile cannot reach the API

Browser development on the same machine uses `http://localhost:8080` and must
match `CORS_ALLOWED_ORIGINS`. An Android emulator commonly reaches the host at
`http://10.0.2.2:8080`; an iOS simulator normally uses
`http://localhost:8080`. A physical device cannot normally reach a loopback-only
Docker binding. Use a controlled local network/tunnel setup for device testing
and update CORS for the exact development origin; do not expose the API
publicly with production secrets.

#### Render and Angular integration

When the API is deployed to Render, the frontend must use the Render service
URL as its API base URL. It must not send production requests to its own
development server at `http://localhost:4200/api`.

For the deployed API in this project, configure the Angular environments and
service URLs with:

```text
https://distro-backend-09e7.onrender.com
```

The login request is therefore:

```http
POST https://distro-backend-09e7.onrender.com/api/auth/login
Content-Type: application/json

{
  "identifier": "admin@example.com",
  "password": "your-password"
}
```

For local `ng serve`, a proxy is also valid: keep frontend calls relative to
`/api` and point the development proxy target at the Render URL. That proxy is
only an Angular development convenience; a production frontend still needs
the deployed API base URL.

In the Render service environment, set `CORS_ALLOWED_ORIGINS` to the exact
browser origin(s) that load the frontend, separated by commas and without
trailing slashes. Examples:

```text
CORS_ALLOWED_ORIGINS=http://localhost:4200,https://admin.example.com
```

Do not put the backend URL in `CORS_ALLOWED_ORIGINS`; CORS validates the
frontend's `Origin` header, not the API's own address. After changing an
environment variable, redeploy the Render service. The backend accepts the
`Authorization` and `Content-Type` headers and includes CORS headers on
authenticated and error responses when the origin is allowed.

Render supplies the `PORT` environment variable automatically; the application
uses it in deployment and retains `SERVER_PORT`/8080 for local development.

To check the preflight from PowerShell, run:

```powershell
Invoke-WebRequest `
  -Method Options `
  -Uri https://distro-backend-09e7.onrender.com/api/v1/products `
  -Headers @{
    Origin = 'http://localhost:4200'
    'Access-Control-Request-Method' = 'GET'
    'Access-Control-Request-Headers' = 'authorization,content-type'
  }
```

The response should include `Access-Control-Allow-Origin` with the same
frontend origin and allow the requested method and headers. A `401` or `403`
after a successful preflight is an authentication/authorization problem, not
a CORS problem; sign in again to obtain a current token after a role change.

### API changes are not reflected

Rebuild the image from the repository root:

```bash
docker compose up --build -d
```

### Docker data is stale

If you intentionally want a fresh disposable database:

```bash
docker compose down --volumes
docker compose up --build -d
```

This removes local database data and reruns the Flyway migrations.

## Project structure

```text
src/main/java/com/example/distrobackend/controller/  HTTP REST controllers
src/main/java/com/example/distrobackend/service/     Business workflows
src/main/java/com/example/distrobackend/Domain/      JPA entities and enums
src/main/java/com/example/distrobackend/repository/  Database access
src/main/java/com/example/distrobackend/configuration/ Security, OpenAPI, WebSocket
src/main/resources/application.properties             Runtime configuration
src/main/resources/db/migration/                      Flyway migrations
src/test/java/                                        Automated tests
docker-compose.yml                                    Local API and PostgreSQL services
.env.example                                          Environment template
```

Java does not use a Node.js-style `routes` folder here. The controller classes
are the HTTP route definitions; Spring Boot discovers their annotations and
maps incoming requests to them.
