-- Hibernate maps Order.version as a Java long (@Version), which is BIGINT in PostgreSQL.
-- Earlier installations may have recorded V2 as applied without this column.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
