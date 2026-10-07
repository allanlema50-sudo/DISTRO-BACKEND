# Distro Backend

Spring Boot backend for the LogiFlow distribution platform.

Unless stated otherwise, commands in this document are executed from the
repository root (`DISTRO-BACKEND`).

## Requirements

- Docker Desktop with Docker Compose
- Git
- Java 17 or later
- Maven 3.9.x for host-based development

The project is compiled for Java 17 and has been verified with Java 21.

## Configuration

Create a local environment file from the committed template. Run from the
repository root.

PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

Set the local database password and JWT signing secret in `.env`:

```dotenv
POSTGRES_DB=distro_backend
POSTGRES_USER=distro
POSTGRES_PASSWORD=<local-database-password>
JWT_SECRET=<base64-encoded-secret>
API_PORT=8080
OPENAPI_ENABLED=true

# Use a free host port if 5432 is already occupied on your machine.
POSTGRES_PORT=5433

MPESA_BASE_URL=https://sandbox.safaricom.co.ke
MPESA_CONSUMER_KEY=<daraja-consumer-key>
MPESA_CONSUMER_SECRET=<daraja-consumer-secret>
MPESA_SHORTCODE=<daraja-shortcode>
MPESA_PASSKEY=<daraja-passkey>
MPESA_CALLBACK_URL=https://<public-host>/api/v1/payments/mpesa/callback
MPESA_VERIFY_CALLBACK=true
```

`MPESA_VERIFY_CALLBACK` must remain `true`. The API fails closed and rejects
callbacks when provider-side verification is disabled; it never confirms an
order from callback fields alone.

Generate a JWT secret with PowerShell:

```powershell
$b=[byte[]]::new(64); [Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

Generate one with macOS/Linux:

```bash
openssl rand -base64 64
```

`.env` is ignored by Git. Do not commit local credentials. Use `.env.example`
for shared configuration documentation.

## Running with Docker

### Start the full stack

Run from the repository root:

```bash
docker compose up --build
```

To run in detached mode:

```bash
docker compose up --build -d
```

The Compose stack contains:

- `api`: Spring Boot application on container and host port `8080` by default
- `db`: PostgreSQL 16 on container port `5432`; the host port is controlled by
  `POSTGRES_PORT` and defaults to `5433`
- `postgres_data`: persistent PostgreSQL volume

Check service status:

```bash
docker compose ps
```

View API logs:

```bash
docker compose logs -f api
```

View database logs:

```bash
docker compose logs -f db
```

Stop the stack while preserving database data:

```bash
docker compose down
```

Rebuild and restart after code or dependency changes:

```bash
docker compose up --build -d
```

To remove the local database volume and recreate the database, run:

```bash
docker compose down --volumes
docker compose up --build
```

The `--volumes` operation is destructive to local database data and should
only be used when the data is disposable.

## Application endpoints

The following URLs are served by the API container. Open browser URLs in a
browser; use the API URLs with an HTTP client or API tool.

| Purpose | URL |
|---|---|
| Health | <http://localhost:8080/actuator/health> |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| WebSocket/STOMP handshake | `ws://localhost:8080/ws` |

Swagger UI is disabled by default. Enable it for local development with
`OPENAPI_ENABLED=true`, then use **Authorize** in Swagger UI to provide a
bearer JWT for secured endpoints.

## Procurement API handoff

Distributor users can create purchase orders against manufacturer-owned stock:

```text
POST /api/v1/purchase-orders
GET  /api/v1/purchase-orders
GET  /api/v1/purchase-orders/{purchaseOrderId}
GET  /api/v1/purchase-orders/{purchaseOrderId}/settlements
```

Manufacturer users can review only purchase orders addressed to their own
organization:

```text
GET   /api/v1/manufacturer/purchase-orders
GET   /api/v1/manufacturer/purchase-orders/{purchaseOrderId}
PATCH /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/decision
GET   /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/settlements
```

Only `MANUFACTURER_ADMIN` may approve or reject a purchase order. The decision
body is either `{"status":"APPROVED"}` or
`{"status":"REJECTED","note":"reason"}`. Organization ownership is
derived from the authenticated JWT; organization IDs in URLs are never trusted
for authorization.

Purchase-order settlement records are currently exposed as a read-only ledger.
They are initialized as `PENDING`; provider reconciliation, refunds, and
reversals remain part of the later platform payment-admin phase.

All controller and security failures use the common `ApiError` response shape:

```json
{
  "timestamp": "2026-10-07T10:46:03Z",
  "status": 400,
  "code": "BAD_REQUEST",
  "message": "Malformed request body or invalid field value",
  "path": "/api/auth/login"
}
```

OpenAPI and Swagger UI are disabled by default. To enable them locally, set
the following variable in `.env` and restart the API:

```dotenv
OPENAPI_ENABLED=true
```

Health check from PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Health check from macOS/Linux:

```bash
curl http://localhost:8080/actuator/health
```

## Database and migrations

Default Docker database configuration:

| Setting | Value |
|---|---|
| Database | `distro_backend` |
| Username | `distro` |
| Host port | `POSTGRES_PORT` (defaults to `5433`; choose another free port if needed) |
| Container service | `db` |

Run the following from the repository root. Connect to PostgreSQL through the
Compose service rather than the host port:

```bash
docker compose exec db psql -U distro -d distro_backend
```

Useful `psql` commands include `\dt` to list tables and `\q` to exit.

Flyway migrations are stored in:

```text
src/main/resources/db/migration/
```

The initial migration is `V1__initial_schema.sql`. Add subsequent schema
changes as new versioned migrations, for example:

```text
V8__add_warehouse_table.sql
```

Do not modify a migration that has already been applied to a shared database.
Hibernate uses `ddl-auto=validate`; it validates the schema but does not create
or alter tables automatically.

When switching between branches with different migration histories, use a
separate local database or reset the disposable development volume. Do not use
`flyway repair` to hide a checksum mismatch in a shared or production database.

For an existing database created before Flyway history was introduced, first
verify that its schema matches `V1__initial_schema.sql`, then set
`FLYWAY_BASELINE_ON_MIGRATE=true` and `FLYWAY_BASELINE_VERSION=1` in `.env`.
The baseline only records the existing schema as V1; it does not perform the
tenant ownership migration. On the first startup, V2 expands the schema and
V3 derives ownership where it is unambiguous. If V3 reports unresolved rows,
stop the API, backfill `organization_id` on the affected orders, stock items,
and trips using an approved data-migration procedure, then start the API again.
Only after those rows are reviewed should V3 add the foreign keys and NOT NULL
constraints. V3 also fails closed when a legacy trip references order and stock
records owned by different organizations. This is an explicit compatibility
decision, not a general production default; leave the setting false for new or
unverified databases.

`V7__refine_trip_batch_schema.sql` installs the positive stop-sequence check as
`NOT VALID` so legacy rows do not block startup. After reviewing and repairing
any existing non-positive values, validate it from the database service:

```sql
ALTER TABLE trip_stops VALIDATE CONSTRAINT ck_trip_stops_sequence_positive;
```

`V6__scope_stock_sku_uniqueness_to_organization.sql` fails closed when an
existing organization contains SKUs that differ only by case. Review conflicts
before retrying the migration; for example:

```sql
SELECT organization_id, UPPER(sku) AS normalized_sku, COUNT(*)
FROM stock_items
GROUP BY organization_id, UPPER(sku)
HAVING COUNT(*) > 1;
```

Do not edit an applied Flyway migration. The tenant-ownership migration V3 was
corrected before this branch's first deployment; if an environment has already
recorded a different V3 checksum, stop deployment and perform an approved
Flyway checksum repair against the exact reviewed artifact after confirming the
database schema. Never use `flyway repair` to conceal an unreviewed schema
difference.

## Host-based development

This workflow runs PostgreSQL in Docker and the Spring Boot API through Maven.

Start PostgreSQL from the repository root:

```bash
docker compose up -d db
```

Set the API environment variables in the terminal that will run Maven. The
host-based JDBC URL must use the host port configured in `.env`.

PowerShell:

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:5433/distro_backend'
$env:DATABASE_USERNAME='distro'
$env:DATABASE_PASSWORD='<value of POSTGRES_PASSWORD in .env>'
$env:JWT_SECRET='<generated JWT secret>'
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200'
$env:SERVER_PORT='8080'
# Optional local/demo data only; omit in production.
$env:SPRING_PROFILES_ACTIVE='local'
```

macOS/Linux:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5433/distro_backend'
export DATABASE_USERNAME='distro'
export DATABASE_PASSWORD='<value of POSTGRES_PASSWORD in .env>'
export JWT_SECRET='<generated JWT secret>'
export CORS_ALLOWED_ORIGINS='http://localhost:4200'
export SERVER_PORT='8080'
# Optional local/demo data only; omit in production.
export SPRING_PROFILES_ACTIVE='local'
```

Start the API from the repository root:

```bash
mvn spring-boot:run
```

### M-Pesa configuration

Payment initiation is disabled until the Daraja credentials are supplied.
Configure these variables in the local, deployment, or secret-management
environment; do not commit them:

```text
MPESA_CONSUMER_KEY
MPESA_CONSUMER_SECRET
MPESA_SHORTCODE
MPESA_PASSKEY
MPESA_CALLBACK_URL
MPESA_CALLBACK_SECRET
MPESA_AUTH_URL
MPESA_STK_PUSH_URL
```

The callback endpoint requires `X-Mpesa-Callback-Secret` to match
`MPESA_CALLBACK_SECRET`. Configure that header at the payment gateway or
integration layer before enabling live callbacks.

## Build and test

Run from the repository root.

Compile, package, and skip test execution:

```bash
mvn -B -ntp -DskipTests package
```

Run the test suite after PostgreSQL is running and the database environment
variables are set:

```bash
mvn clean test
```

The test suite uses PostgreSQL and Flyway. It requires the same
`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `JWT_SECRET`
variables as host-based development.

If the IDE reports unresolved Maven or JUnit imports after `pom.xml` changes,
reload or reimport the Maven project and restart the Java language server if
necessary.

## WebSocket conventions

The application uses STOMP over WebSocket:

- Handshake endpoint: `/ws`
- Application destination prefix: `/app`
- Broker destination prefix: `/topic`
- User destination prefix: `/user`
- Trip location subscription format: `/topic/trips/{tripId}/location`

STOMP `CONNECT` frames require a valid bearer JWT. Subscription destinations
are validated by the inbound channel interceptor.

## Project structure

```text
src/main/java/                  Application source code
src/main/resources/             Configuration and database migrations
src/main/resources/db/migration Flyway migrations
src/test/java/                  Automated tests
Dockerfile                      Multi-stage API image
docker-compose.yml              Local API and PostgreSQL services
.env.example                    Environment variable template
pom.xml                         Maven build configuration
```

## Troubleshooting

### API is not reachable

Check the service state and recent logs from the repository root:

```bash
docker compose ps
docker compose logs --tail=100 api
```

The API may take several seconds to start while PostgreSQL becomes healthy and
Flyway applies migrations. Retry the health endpoint after startup completes.

### Port conflict

If ports `8080` or `5433` are already in use, set `API_PORT` or
`POSTGRES_PORT` in `.env`. When changing `POSTGRES_PORT`, update the host-based
`DATABASE_URL` accordingly. The API-to-database connection inside Docker still
uses the Compose service name `db` and port `5432`.

For example, when PostgreSQL host port `5433` is unavailable:

```dotenv
POSTGRES_PORT=5434
API_PORT=8080
```

Then recreate the stack from the repository root:

```powershell
docker compose down
docker compose up --build
```

### Database authentication failure

The PostgreSQL password is initialized when the Docker volume is first
created. Changing `POSTGRES_PASSWORD` in `.env` does not change the password in
an existing volume. Either restore the original password or recreate the
disposable local volume with `docker compose down --volumes`.

### Docker changes are not reflected

Rebuild the API image from the repository root:

```bash
docker compose up --build -d
```
