ALTER TABLE ledger_journals
    ADD COLUMN provider_reference VARCHAR(120);

UPDATE ledger_journals
SET provider_reference = substring(description FROM '^Confirmed supplier payout (.+)$')
WHERE source_type = 'SUPPLIER_SETTLEMENT'
  AND event_type ~ '^PAYOUT_SETTLED_[0-9]+$'
  AND description ~ '^Confirmed supplier payout (.+)$';

UPDATE ledger_journals
SET provider_reference = substring(description FROM '^Reversal of confirmed supplier payout (.+)$')
WHERE source_type = 'SUPPLIER_SETTLEMENT'
  AND event_type ~ '^PAYOUT_REVERSED_[0-9]+$'
  AND description ~ '^Reversal of confirmed supplier payout (.+)$';

CREATE INDEX idx_ledger_journals_provider_reference
    ON ledger_journals(provider_reference)
    WHERE provider_reference IS NOT NULL;

CREATE UNIQUE INDEX uk_ledger_supplier_payout_provider_reference
    ON ledger_journals(provider_reference)
    WHERE source_type = 'SUPPLIER_SETTLEMENT'
      AND event_type ~ '^PAYOUT_SETTLED_[0-9]+$'
      AND provider_reference IS NOT NULL;
