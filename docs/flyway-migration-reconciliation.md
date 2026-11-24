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

## V71 collision reported by the production startup log

This section records an earlier resolution and is superseded by the later V73
incident below for the currently deployed payment history.

The deployed application reported two resources with version 71:
`V71__financial_ledger_and_commission_rules.sql` and
`V71__canonical_reseller_listings.sql`. The reseller listing migration has
been assigned the next version, `V72__canonical_reseller_listings.sql`, so a
build that includes the financial migration has one resource per version.
The financial migration remains at V71 and must not be renamed or edited.

Before rollout, inspect `flyway_schema_history` on the target database. If a
successful V71 row already records `V71__canonical_reseller_listings.sql`, do
not deploy the renumbering as-is: that database needs a reviewed Flyway
history reconciliation on a backup/clone, because applied migrations are
immutable. If V71 is the financial migration (or no V71 row exists), deploy
the financial V71, Paystack V72, and reseller V73. Do not delete history rows or
disable Flyway validation to bypass this check.
- Keep schema changes in Flyway and leave Hibernate at `validate`.

## V72 collision reported by the production startup log

This also records the historical planned ordering; use the later V73 resolution
below for the production database shown in the current Render history screenshot.

The deployed application reported two resources with version 72:
`V72__paystack_checkout_provider_fields.sql` and
`V72__canonical_reseller_listings.sql`. The reseller listing migration has
been assigned version V73; the Paystack migration remains at V72 and must not
be renamed or edited.

Before rollout, inspect `flyway_schema_history` on the target database. If a
successful V72 row already records `V72__canonical_reseller_listings.sql`, do
not deploy the renumbering as-is: that database needs a reviewed Flyway
history reconciliation on a backup/clone, because applied migrations are
immutable. If V72 is the Paystack migration (or no V72 row exists), deploy
Paystack V72 followed by reseller V73. Do not delete history rows or disable
Flyway validation to bypass this check.

## V73 collision reported by Render on 2026-11-24

Render reported duplicate resources `V73__paystack_settlement_batches_and_beneficiaries.sql`
and `V73__canonical_reseller_listings.sql`. The production history screenshot
shows V73 recorded as the Paystack settlement migration, followed by successful
V74, V75, and V76 rows. Preserve the applied Paystack V73 file and checksum.
The reseller-listing migration is assigned V77. V79 contains duplicate
reseller-listing DDL and is guarded with `IF NOT EXISTS` so it does not fail
after V77 on a fresh history. The refund migration follows at V81.
Paystack dispute tracking and the durable webhook transaction-reference field
are added at V82. Keep this new migration above the existing V81 refund
migration; never renumber or edit the applied V73 payout migration.

Before deployment, check the full history through its latest installed row.
If V79 is already recorded, compare its history checksum with the currently
deployed V79 artifact before using the changed idempotent script; do not run
`flyway repair` blindly.
Do not rename the applied Paystack V73 migration or delete its history row.
