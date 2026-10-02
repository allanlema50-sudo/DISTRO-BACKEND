# Distro Backend

Spring Boot backend for the LogiFlow distribution platform.

This guide is written for someone checking out the repository for the first
time. Unless a section says otherwise, open a terminal and change directory to
the repository root first:

```text
DISTRO-BACKEND/
```

PowerShell on Windows:

```powershell
cd path\to\DISTRO-BACKEND
```

macOS/Linux:

```bash
cd /path/to/DISTRO-BACKEND
```

You are in the correct folder when commands such as `Get-ChildItem` or `ls`
show `pom.xml`, `Dockerfile`, and `docker-compose.yml`.

## What is required

For the recommended Docker setup, install:

- Docker Desktop with Docker Compose
- Git

For running the application directly on your computer, also install:

- Java 17 or newer
- Maven 3.9.x

The project currently compiles for Java 17 and has been verified with Java 21.

## First-time setup with Docker

Docker is the easiest way to run both the API and PostgreSQL. All commands in
this section are run from the repository root on your host computer, not inside
the database or API container.

### 1. Create the local environment file

PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

Open `.env` in an editor and replace the placeholder values. At minimum, set a
strong `POSTGRES_PASSWORD` and a strong `JWT_SECRET`.

Generate a JWT secret in PowerShell:

```powershell
$b=[byte[]]::new(64); [Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

Generate one on macOS/Linux:

```bash
openssl rand -base64 64
```

Copy the generated value into `JWT_SECRET` in `.env`. Never commit `.env` or
paste its values into source code. `.env` is ignored by Git; `.env.example` is
the safe template that is committed.

### 2. Start the database and API

Run this from the repository root:

```bash
docker compose up --build
```

The first build may take several minutes. Leave this terminal open to see the
logs. The API starts only after PostgreSQL reports that it is healthy.

To start the stack in the background instead, run from the repository root:

```bash
docker compose up --build -d
```

Check the container status from the repository root:

```bash
docker compose ps
```

Both `api` and `db` should be running. The database should show a healthy
status.

## Opening and checking the running API

These are web addresses, so open them in a browser. Do not type the URLs into
the project terminal as shell commands.

### Swagger UI

Open this address in your browser:

```text
http://localhost:8080/swagger-ui/index.html
```

Swagger UI provides an interactive list of the available HTTP endpoints. When
an endpoint requires authentication, click **Authorize**, enter a JWT using
the `Bearer` format expected by the UI, and then call the endpoint.

### Raw OpenAPI JSON

Open this address in a browser or API client:

```text
http://localhost:8080/v3/api-docs
```

This returns the machine-readable OpenAPI document. Tools such as Postman,
Insomnia, or frontend code generators can import this document.

### Health check

Open this in a browser:

```text
http://localhost:8080/actuator/health
```

Or run this from a second host terminal. The command can be run from any
folder, although using the repository root keeps the workflow consistent.

PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

macOS/Linux:

```bash
curl http://localhost:8080/actuator/health
```

An available application normally returns a status of `UP`. Immediately after
starting Docker, wait for the API startup logs to finish before checking the
endpoint. A connection-closed error usually means the container is still
starting or has restarted after a configuration error.

### WebSocket endpoint

The WebSocket handshake endpoint is:

```text
ws://localhost:8080/ws
```

The application uses STOMP messaging. Application messages use `/app`, trip
location updates are published through `/topic`, and authenticated user
messages use `/user`. A client must provide a valid bearer JWT during the STOMP
`CONNECT` frame.

## Docker commands you will use often

Run all commands below from the repository root.

View API logs:

```bash
docker compose logs -f api
```

View database logs:

```bash
docker compose logs -f db
```

Stop the API and database but preserve the database volume:

```bash
docker compose down
```

Start already-built containers again:

```bash
docker compose up -d
```

Rebuild the API after changing Java code or dependencies:

```bash
docker compose up --build -d
```

Open a PostgreSQL shell inside the running database container:

```bash
docker compose exec db psql -U distro -d distro_backend
```

Inside `psql`, use `\dt` to list tables and `\q` to exit. The database name
created by this project is `distro_backend`; the Docker username is `distro`.

## Database and Flyway migrations

The Docker database uses these values by default:

```text
Database: distro_backend
Username: distro
Port:     5432
```

The password comes from `POSTGRES_PASSWORD` in `.env`. PostgreSQL stores its
initial password in the persistent Docker volume. Changing the password in
`.env` later does not automatically change the password inside an existing
volume.

Schema files are stored in:

```text
src/main/resources/db/migration/
```

The first migration is:

```text
V1__initial_schema.sql
```

When the API starts, Flyway runs any new migrations and Hibernate validates the
resulting schema. Hibernate is configured with `ddl-auto=validate`, so it does
not silently create or change tables.

For every future schema change, create a new migration, for example:

```text
src/main/resources/db/migration/V2__add_warehouse_table.sql
```

Do not edit a migration that has already been applied to a shared database.

If the local database contains disposable data and you intentionally want a
fresh database, run from the repository root:

```bash
docker compose down --volumes
docker compose up --build
```

The `--volumes` option deletes the local PostgreSQL data volume. Do not use it
when you need to preserve existing local data.

## Host-based development with Maven

Use this workflow when you want to run the Spring Boot application directly
from your IDE or host computer. The database still runs in Docker, but the API
runs from Maven.

### 1. Start only PostgreSQL

From the repository root, run:

```bash
docker compose up -d db
```

### 2. Set environment variables in the API terminal

These variables apply only to the current terminal session.

PowerShell, from the repository root:

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:5432/distro_backend'
$env:DATABASE_USERNAME='distro'
$env:DATABASE_PASSWORD='the-value-of-POSTGRES_PASSWORD-in-.env'
$env:JWT_SECRET='the-generated-base64-secret'
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200'
```

macOS/Linux, from the repository root:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/distro_backend'
export DATABASE_USERNAME='distro'
export DATABASE_PASSWORD='the-value-of-POSTGRES_PASSWORD-in-.env'
export JWT_SECRET='the-generated-base64-secret'
export CORS_ALLOWED_ORIGINS='http://localhost:4200'
```

### 3. Start the API with Maven

Still in the repository root, run:

```bash
mvn spring-boot:run
```

The API will use port `8080`. Open the same health, Swagger UI, and OpenAPI
URLs described above. Stop the Maven process with `Ctrl+C`.

## Build and test commands

Run these commands from the repository root.

Compile and package without running tests:

```bash
mvn -B -ntp -DskipTests package
```

Compile the project and run the test suite:

```bash
mvn clean test
```

Run only the tests without cleaning previous build output:

```bash
mvn test
```

If Maven reports that dependencies are missing in the IDE, reload or reimport
the Maven project. In VS Code, use the Maven or Java extension command to
reload the project, or restart the Java language server.

## OpenAPI configuration

OpenAPI and Swagger UI are enabled by default for local development. To turn
them off, add this line to `.env` and restart the API:

```text
OPENAPI_ENABLED=false
```

The generated documentation scans controllers under:

```text
com.example.distrobackend.controller
```

## Repository layout

```text
src/main/java/                 Java application code
src/main/resources/            application configuration and migrations
src/main/resources/db/migration Flyway SQL migrations
Dockerfile                     Multi-stage API image
docker-compose.yml             API and PostgreSQL local stack
.env.example                   Safe environment-variable template
pom.xml                        Maven dependencies and build configuration
```

Business services and domain features can be added on top of this foundation.
When adding tenant-scoped business functionality, enforce tenant filtering in
the service/repository queries as well as at the controller authorization
boundary.

## Troubleshooting

### `Invoke-RestMethod` says the connection was closed

Run `docker compose ps` from the repository root and then inspect the API logs:

```bash
docker compose logs --tail=100 api
```

If the API is still starting, wait and retry the health URL. If the API keeps
restarting, check the first configuration or database error in the logs.

### Port 8080 or 5432 is already in use

Stop the process using the port, or change `API_PORT` or `POSTGRES_PORT` in
`.env`. If you change `POSTGRES_PORT`, update the host-based `DATABASE_URL` too.
The API-to-database connection inside Docker continues to use port `5432`.

### Database password authentication fails

Confirm that `DATABASE_PASSWORD` used by host Maven matches the original
`POSTGRES_PASSWORD` used when the Docker volume was created. If the data is
disposable, recreate the volume using the migration reset commands above.

### Docker changes are not appearing

From the repository root, rebuild the image:

```bash
docker compose up --build -d
```

Then check the latest logs:

```bash
docker compose logs --tail=100 api
```
