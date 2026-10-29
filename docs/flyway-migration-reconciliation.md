# Flyway migration-history reconciliation

This project uses Flyway as the source of truth for database schema changes.
Applied migration files are immutable: a migration must not be renamed or
edited after it has been applied to a database. Flyway validates the version,
description, script name, and checksum stored in `flyway_schema_history` before
the application starts.

## Canonical production history

The Supabase database used by the deployed service is the production reference
history. Its relevant rows are:

```text
V16__version_organization_profiles.sql
V28__seed_access_request_submission_limit.sql
V29__restore_order_version_column.sql
```

The repository therefore intentionally keeps these migration identities:

```text
V29__restore_order_version_column.sql
V30__product_authorizations.sql
V31__product_publication_metadata_and_images.sql
V32__stock_item_image_availability.sql
V34__version_organization_profiles.sql
```

`V34` is the organization optimistic-lock migration that was not present in
the Supabase history. It is intentionally a new version; it must not be
renamed to V29.

Before deploying to an existing database, inspect the history:

```sql
SELECT installed_rank, version, description, script, checksum, success
FROM public.flyway_schema_history
ORDER BY installed_rank;
```

For the production database, the V29 row must have:

```text
version = 29
script  = V29__restore_order_version_column.sql
success = true
```

If that is the case, deploy with Flyway enabled and Hibernate validation
enabled. Flyway will apply only pending migrations. Do not run `repair`, set
`outOfOrder`, or set `ignoreMigrationPatterns` for this database.

## Alternate base history

Some databases created from an earlier branch have a different, valid history:

```text
version 29 -> V29__version_organization_profiles.sql
version 33 -> V33__restore_order_version_column.sql
```

That history cannot be made compatible by renaming files. Pointing such a
database at the canonical production build without reconciliation can fail
validation before V34 runs. It can also make a deployment appear healthy in
one environment while remaining unreproducible in another.

### Preferred path: rebuild a disposable database

For local, staging, or any database whose data can be recreated:

1. Export any data that must be retained.
2. Create a fresh database.
3. Start the application with Flyway enabled and Hibernate set to `validate`.
4. Load retained data through a reviewed data migration or an application-level
   import. Do not copy `flyway_schema_history` from the old database.

This produces the canonical history without changing migration metadata.

### Retained-data path: one-time reviewed reconciliation

Use this path only when the database cannot be rebuilt. It must be performed
by an operator with a backup and a tested clone of the database:

1. Stop application deployments that can write to the database.
2. Take a restorable database backup and restore it to a disposable clone.
3. Confirm the alternate history and successful rows:

   ```sql
   SELECT version, description, script, checksum, success
   FROM public.flyway_schema_history
   WHERE version IN ('29', '33')
   ORDER BY version;
   ```

4. Confirm that the schema already contains the effects of both migrations:

   ```sql
   SELECT table_name, column_name, data_type, is_nullable
   FROM information_schema.columns
   WHERE table_schema = 'public'
     AND ((table_name = 'organizations' AND column_name = 'version')
       OR (table_name = 'orders' AND column_name = 'version'))
   ORDER BY table_name;
   ```

   The expected result is `organizations.version` and `orders.version`, both
   `bigint`, with neither column nullable.

5. Against the clone only, run the Flyway `repair` command using the exact
   migration set from this repository. This is a deliberate metadata
   reconciliation, not an application startup setting. Review the command
   output and stop if it changes any migration other than the known V29/V33
   conflict.
6. Run Flyway `migrate` against the clone. Confirm that V30, V31, V32, and V34
   apply successfully and that the application starts with
   `spring.jpa.hibernate.ddl-auto=validate`.
7. Run the application's smoke tests against the clone, including login,
   access-request submission, catalog reads, and the affected order paths.
8. Repeat the same reviewed repair and migration operation on the backed-up
   production database during a maintenance window.

After reconciliation, verify the resulting history:

```sql
SELECT version, description, script, success
FROM public.flyway_schema_history
ORDER BY installed_rank;
```

The resulting database must use the canonical V29 script and must contain
successful rows for V30, V31, V32, and V34. Record the repair output and the
backup identifier in the deployment record.

Do not perform this procedure by deleting history rows manually, changing
checksums by hand, enabling `ignoreMigrationPatterns`, enabling `outOfOrder`,
or setting `baseline-on-migrate=true`. Those settings can hide a real schema
problem and make later deployments unsafe.

## Rules for future migrations

- Never edit or rename an applied migration.
- Allocate the next unused version after the highest version in the canonical
  history; the next new migration after V35 is V36.
- Check for duplicate versions before opening a pull request.
- Test startup against both a fresh database and a copy of the production
  database before deployment.
- Keep schema changes in Flyway and leave Hibernate at `validate`.
