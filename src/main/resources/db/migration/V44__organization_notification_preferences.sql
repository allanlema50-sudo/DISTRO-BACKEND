CREATE TABLE organization_notification_preferences (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    dispatch_exceptions_sms BOOLEAN NOT NULL DEFAULT FALSE,
    purchase_order_approvals_email BOOLEAN NOT NULL DEFAULT FALSE,
    weekly_capacity_forecasts BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
