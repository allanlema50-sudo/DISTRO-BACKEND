ALTER TABLE access_requests
    ADD COLUMN IF NOT EXISTS country VARCHAR(100),
    ADD COLUMN IF NOT EXISTS registration_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tax_identifier VARCHAR(50),
    ADD COLUMN IF NOT EXISTS representative_role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS authorized_representative BOOLEAN;

ALTER TABLE access_requests
    ADD CONSTRAINT ck_access_request_representative_role
        CHECK (representative_role IS NULL OR representative_role IN ('Manager', 'Supervisor')),
    ADD CONSTRAINT ck_access_request_authorized_representative
        CHECK (authorized_representative IS NULL OR authorized_representative = TRUE);

CREATE TABLE access_request_documents (
    id UUID PRIMARY KEY,
    access_request_id UUID NOT NULL REFERENCES access_requests(id) ON DELETE CASCADE,
    document_type VARCHAR(40) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_access_request_document_type UNIQUE (access_request_id, document_type),
    CONSTRAINT ck_access_request_document_type CHECK (
        document_type IN ('INCORPORATION_CERTIFICATE', 'TAX_CERTIFICATE')
    ),
    CONSTRAINT ck_access_request_document_size CHECK (file_size > 0 AND file_size <= 5242880),
    CONSTRAINT ck_access_request_document_content_size CHECK (octet_length(content) <= 5242880)
);

CREATE INDEX idx_access_request_documents_request
    ON access_request_documents (access_request_id);
