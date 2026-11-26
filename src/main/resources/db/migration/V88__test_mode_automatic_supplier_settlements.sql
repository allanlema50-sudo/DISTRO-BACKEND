-- Snapshot the provider environment for auditable payouts and distinguish automated
-- sandbox batches from batches created and approved by platform staff.
ALTER TABLE payout_beneficiaries
    ADD COLUMN provider_mode VARCHAR(8),
    ADD CONSTRAINT ck_payout_beneficiary_provider_mode
        CHECK (provider_mode IS NULL OR provider_mode IN ('TEST', 'LIVE'));

ALTER TABLE settlement_batches
    ALTER COLUMN created_by DROP NOT NULL,
    ADD COLUMN provider_mode VARCHAR(8),
    ADD COLUMN execution_mode VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD CONSTRAINT ck_settlement_batch_provider_mode
        CHECK (provider_mode IS NULL OR provider_mode IN ('TEST', 'LIVE')),
    ADD CONSTRAINT ck_settlement_batch_execution_mode
        CHECK (execution_mode IN ('MANUAL', 'TEST_AUTOMATIC')),
    ADD CONSTRAINT ck_settlement_batch_test_automatic_audit
        CHECK (execution_mode <> 'TEST_AUTOMATIC'
            OR (provider_mode = 'TEST' AND created_by IS NULL AND approved_by IS NULL
                AND approved_at IS NOT NULL));

ALTER TABLE supplier_payout_attempts
    ADD COLUMN provider_mode VARCHAR(8),
    ADD CONSTRAINT ck_supplier_payout_attempt_provider_mode
        CHECK (provider_mode IS NULL OR provider_mode IN ('TEST', 'LIVE'));
