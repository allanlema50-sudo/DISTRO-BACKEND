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
```

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

- `api`: Spring Boot application on port `8080`
- `db`: PostgreSQL 16 on port `5432`
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

Swagger UI is enabled by default for local development. Use **Authorize** in
Swagger UI to provide a bearer JWT for secured endpoints.

OpenAPI and Swagger UI can be disabled by setting the following variable in
`.env` and restarting the API:

```dotenv
OPENAPI_ENABLED=false
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
| Host port | `POSTGRES_PORT` from `.env` (default `5432`) |
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
V2__add_warehouse_table.sql
```

Do not modify a migration that has already been applied to a shared database.
Hibernate uses `ddl-auto=validate`; it validates the schema but does not create
or alter tables automatically.

When switching between branches with different migration histories, use a
separate local database or reset the disposable development volume. Do not use
`flyway repair` to hide a checksum mismatch in a shared or production database.

## Host-based development

This workflow runs PostgreSQL in Docker and the Spring Boot API through Maven.

Start PostgreSQL from the repository root:

```bash
docker compose up -d db
```

Set the API environment variables in the terminal that will run Maven.

PowerShell:

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:5432/distro_backend'
$env:DATABASE_USERNAME='distro'
$env:DATABASE_PASSWORD='<value of POSTGRES_PASSWORD in .env>'
$env:JWT_SECRET='<generated JWT secret>'
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200'
$env:API_SERVER_PORT='8080'
# Optional local/demo data only; omit in production.
$env:SPRING_PROFILES_ACTIVE='local'
```

macOS/Linux:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/distro_backend'
export DATABASE_USERNAME='distro'
export DATABASE_PASSWORD='<value of POSTGRES_PASSWORD in .env>'
export JWT_SECRET='<generated JWT secret>'
export CORS_ALLOWED_ORIGINS='http://localhost:4200'
export API_SERVER_PORT='8080'
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

Run the test suite:

```bash
mvn clean test
```

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

If ports `8080` or `5432` are already in use, set `API_PORT` or
`POSTGRES_PORT` in `.env`. When changing `POSTGRES_PORT`, update the host-based
`DATABASE_URL` accordingly. The API-to-database connection inside Docker still
uses the Compose service name `db` and port `5432`.

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
