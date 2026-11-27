-- Phase 1: bank-transfer settlement for purchase orders.
-- A settlement keeps total_due and amount_paid. Each payment toward it
-- (M-Pesa receipt or bank slip) is a child row, so a credit purchase order can
-- be paid in several parts without adding a PARTIALLY_PAID status: partial is
-- amount_paid < total_due on a PENDING settlement.

CREATE TABLE settlement_proof_documents (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content BYTEA NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_settlement_proof_type
        CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_settlement_proof_size CHECK (file_size > 0 AND file_size <= 5242880)
);
CREATE INDEX idx_settlement_proof_purchase_order ON settlement_proof_documents(purchase_order_id);

ALTER TABLE purchase_order_settlements
    ADD COLUMN method VARCHAR(20) NOT NULL DEFAULT 'MPESA',
    ADD COLUMN total_due NUMERIC(12,2),
    ADD COLUMN amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0;

UPDATE purchase_order_settlements SET total_due = amount WHERE total_due IS NULL;
UPDATE purchase_order_settlements SET amount_paid = amount WHERE status = 'SETTLED';

ALTER TABLE purchase_order_settlements
    ADD CONSTRAINT ck_purchase_settlement_method CHECK (method IN ('MPESA', 'BANK_TRANSFER')),
    ADD CONSTRAINT ck_purchase_settlement_total_due CHECK (total_due IS NULL OR total_due > 0),
    ADD CONSTRAINT ck_purchase_settlement_amount_paid
        CHECK (amount_paid >= 0 AND (total_due IS NULL OR amount_paid <= total_due));

CREATE TABLE purchase_order_settlement_payments (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES purchase_order_settlements(id) ON DELETE CASCADE,
    supplier_organization_id UUID NOT NULL REFERENCES organizations(id),
    method VARCHAR(20) NOT NULL,
    payer_reference VARCHAR(100) NOT NULL,
    amount NUMERIC(12,2),
    proof_document_id UUID REFERENCES settlement_proof_documents(id),
    idempotency_key VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    submitted_by UUID REFERENCES users(id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_by UUID REFERENCES users(id),
    confirmed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_settlement_payment_method CHECK (method IN ('MPESA', 'BANK_TRANSFER')),
    CONSTRAINT ck_settlement_payment_status CHECK (status IN ('SUBMITTED', 'CONFIRMED')),
    CONSTRAINT ck_settlement_payment_amount CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT ck_settlement_payment_confirmed CHECK (
        status <> 'CONFIRMED' OR (amount IS NOT NULL AND confirmed_at IS NOT NULL)
    )
);
-- One bank slip or receipt can settle only one thing for a given supplier.
CREATE UNIQUE INDEX uk_settlement_payment_supplier_reference
    ON purchase_order_settlement_payments(supplier_organization_id, payer_reference);
CREATE UNIQUE INDEX uk_settlement_payment_idempotency
    ON purchase_order_settlement_payments(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_settlement_payment_settlement
    ON purchase_order_settlement_payments(settlement_id, status);
