# Distro Backend

Spring Boot backend for the LogiFlow distribution platform.

All commands below are run from the repository root. No machine-specific
absolute paths are required.

## Prerequisites

- Docker Desktop with Docker Compose
- Java 17 or newer for host-based development
- Maven 3.9.x for host-based development

## First-time local setup with Docker

Create a local environment file from the committed template:

PowerShell:(at the root backend)

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

Open `.env` and replace the placeholder database password and JWT secret.
The `.env` file is intentionally ignored by Git.

Generate a JWT secret with PowerShell:

```powershell
$b = New-Object byte[] 64; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($b); [Convert]::ToBase64String($b)
```

Or with macOS/Linux:

```bash
openssl rand -base64 64
```

Start PostgreSQL and the API:

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`.
The health endpoint is `http://localhost:8080/actuator/health`.
OpenAPI JSON is available at `http://localhost:8080/v3/api-docs` and Swagger UI
is available at `http://localhost:8080/swagger-ui/index.html`.

OpenAPI is enabled by default for local development. Set `OPENAPI_ENABLED=false`
in a trusted deployment where interactive documentation should not be exposed.

Stop the containers while preserving local database data:

```bash
docker compose down
```

To reset the disposable local database completely:

```bash
docker compose down --volumes
docker compose up --build
```

The Docker database is named `distro_backend`. Its username, password and
host port are configured through `.env`.

## Host-based development

Start only PostgreSQL:

```bash
docker compose up db
```

Then export the database and JWT settings in the current terminal session.

PowerShell:

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:5432/distro_backend'
$env:DATABASE_USERNAME='distro'
$env:DATABASE_PASSWORD='the-value-from-.env'
$env:JWT_SECRET='the-generated-base64-secret'
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200'
mvn spring-boot:run
```

macOS/Linux:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/distro_backend'
export DATABASE_USERNAME='distro'
export DATABASE_PASSWORD='the-value-from-.env'
export JWT_SECRET='the-generated-base64-secret'
export CORS_ALLOWED_ORIGINS='http://localhost:4200'
mvn spring-boot:run
```

## Build and test

```bash
mvn clean test
```

Flyway applies versioned migrations from
`src/main/resources/db/migration`. Hibernate validates the resulting schema
and does not modify it automatically.

## Database migrations

Create a new migration for every schema change, for example:

```text
src/main/resources/db/migration/V2__add_warehouse_table.sql
```

Do not edit a migration that has already been applied to a shared database.
