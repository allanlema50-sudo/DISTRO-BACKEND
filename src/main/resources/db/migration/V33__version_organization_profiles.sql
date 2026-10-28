-- Add optimistic locking to organization profiles after the current schema version.
-- V16 already applies this change on older installations; keep this idempotent
-- for databases that were baselined past V16.
ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
