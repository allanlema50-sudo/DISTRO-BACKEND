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
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Allowed browser origins |
| `OPENAPI_ENABLED` | `false` | Enables Swagger UI and OpenAPI JSON |
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
| `POST` | `/api/auth/register` | Public | Register a customer, manufacturer admin, or distributor admin |
| `POST` | `/api/auth/verify-account` | Public | Verify the account OTP |
| `POST` | `/api/auth/resend-verification` | Public | Resend account-verification OTP |
| `POST` | `/api/auth/login` | Public | Obtain access and refresh tokens |
| `POST` | `/api/auth/refresh` | Public | Rotate/refresh an access token |
| `POST` | `/api/auth/logout` | Public with refresh token | Revoke the supplied refresh token |
| `POST` | `/api/auth/forgot-password` | Public | Request a password-reset OTP |
| `POST` | `/api/auth/reset-password` | Public | Reset a password with an OTP |
| `GET` | `/api/users/me` | Authenticated | Return the current user and organization profile |

Self-registration does not create staff or driver accounts. Those roles are
organization-managed. Organization registration requires an organization name
and email; customer accounts do not belong to an organization.

### Product catalog, offers, warehouses, and stock

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `GET` | `/api/products` | Authenticated catalog roles | Browse active catalog products; supports `category` and pagination |
| `GET` | `/api/products/categories` | Authenticated catalog roles | List categories visible to the caller |
| `POST` | `/api/products` | Manufacturer admin/staff | Create a manufacturer source product |
| `PATCH` | `/api/products/{id}` | Manufacturer admin/staff | Update a manufacturer source product |
| `POST` | `/api/v1/warehouses` | Distributor admin | Create a distributor warehouse |
| `GET` | `/api/v1/warehouses` | Distributor admin/staff | List the caller's warehouses |
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
| `PATCH` | `/api/v1/orders/{orderId}/status` | Customer or distributor admin/staff | Customer may cancel only an unpaid pending order; distributor staff use valid operational transitions |
| `POST` | `/api/v1/orders/{orderId}/delivery-otp` | Customer order owner | Request delivery OTP while the order is `IN_TRANSIT` |
| `PUT` | `/api/v1/orders/{orderId}` | Distributor admin/staff | Reserved route; currently returns `501 NOT_IMPLEMENTED` |

Order creation reserves stock immediately. An unpaid reservation expires after
`ORDER_RESERVATION_TTL`; the scheduled cleanup releases it. If an accepted
M-Pesa request is still in flight, cleanup reconciles the provider first so a
successful payment is not stranded.

### Payments

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `POST` | `/api/v1/payments/initiate` | Customer or distributor admin/staff with order access | Start an M-Pesa STK Push; send `orderId`, Kenyan `phoneNumber`, and optional idempotency key |
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

Purchase-order settlements are currently a read-only `PENDING` ledger. Payment
reconciliation, refunds, reversals, and platform-level settlement reporting are
not part of the current integration surface.

### Trips, delivery, and live tracking

| Method | Endpoint | Access | Purpose / integration note |
|---|---|---|---|
| `POST` | `/api/v1/trips` | Organization operations users | Create a delivery or restock trip with stops |
| `PATCH` | `/api/v1/trips/{tripId}/assign` | Organization operations users | Assign a driver belonging to the trip organization |
| `GET` | `/api/v1/trips/active` | Driver | List the driver's active trips |
| `GET` | `/api/v1/trips/history` | Driver | List the driver's completed/history trips |
| `POST` | `/api/v1/trips/{tripId}/location` | Assigned driver | Record a latitude/longitude ping |
| `GET` | `/api/v1/trips/{tripId}/location` | Authorized trip participant | Read persisted location history |
| `POST` | `/api/v1/trips/{tripId}/stops/{stopId}/confirm` | Assigned driver | Confirm delivery using the customer's six-digit OTP |
| `POST` | `/api/v1/trips/{tripId}/stops/{stopId}/delivery-otp` | Customer for the order | Request the delivery OTP |

### Notifications and organization workspaces

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/notifications` | Manufacturer/distributor admin/staff | Read the caller's organization notification feed |
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
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200'
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

Known limitations to account for during integration:

- `PUT /api/v1/orders/{orderId}` intentionally returns `501 NOT_IMPLEMENTED`;
- there is no platform-level `ADMIN` role or completed platform-admin API;
- admin audit, payment, stock, trip, report, and user controllers are placeholders;
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
schema and organization ownership data first, then follow the migration
recovery instructions in the migration files and project history.

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
