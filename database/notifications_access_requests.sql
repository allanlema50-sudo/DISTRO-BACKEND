-- ============================================================
-- ACCESS REQUESTS
-- ============================================================

CREATE TABLE IF NOT EXISTS access_requests (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    full_name VARCHAR(150) NOT NULL,

    email VARCHAR(150) NOT NULL,

    phone_number VARCHAR(20) NOT NULL,

    organization_name VARCHAR(150) NOT NULL,

    organization_type VARCHAR(30) NOT NULL,

    requested_role VARCHAR(40) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    reviewed_at TIMESTAMPTZ,

    reviewed_by UUID,

    activated_at TIMESTAMPTZ
);


-- ============================================================
-- NOTIFICATIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS notifications (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    recipient_id UUID NOT NULL,

    title VARCHAR(200) NOT NULL,

    message TEXT NOT NULL,

    type VARCHAR(40) NOT NULL,

    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',

    reference_id UUID,

    reference_type VARCHAR(50),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    read_at TIMESTAMPTZ,

    CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES users(id)
        ON DELETE CASCADE

);


-- ============================================================
-- ACCESS REQUEST REVIEWER
-- ============================================================

ALTER TABLE access_requests
    DROP CONSTRAINT IF EXISTS fk_access_request_reviewer;

ALTER TABLE access_requests
    ADD CONSTRAINT fk_access_request_reviewer
    FOREIGN KEY (reviewed_by)
    REFERENCES users(id)
    ON DELETE SET NULL;


-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_access_requests_status
    ON access_requests(status);

CREATE INDEX IF NOT EXISTS idx_access_requests_created_at
    ON access_requests(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient
    ON notifications(recipient_id);

CREATE INDEX IF NOT EXISTS idx_notifications_status
    ON notifications(status);

CREATE INDEX IF NOT EXISTS idx_notifications_created_at
    ON notifications(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_reference
    ON notifications(reference_id);


-- ============================================================
-- CHECK CONSTRAINTS
-- ============================================================

ALTER TABLE access_requests
    DROP CONSTRAINT IF EXISTS chk_access_request_status;

ALTER TABLE access_requests
    ADD CONSTRAINT chk_access_request_status
    CHECK (
        status IN (
            'PENDING',
            'APPROVED',
            'REJECTED',
            'ACTIVATED'
        )
    );


ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS chk_notification_status;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notification_status
    CHECK (
        status IN (
            'READ',
            'UNREAD'
        )
    );


ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS chk_notification_priority;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notification_priority
    CHECK (
        priority IN (
            'LOW',
            'MEDIUM',
            'HIGH',
            'CRITICAL'
        )
    );