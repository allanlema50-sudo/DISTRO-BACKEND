-- Organization optimistic-lock column, moved to its own unique Flyway version.
-- IF NOT EXISTS also supports databases that already received this column.
ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
