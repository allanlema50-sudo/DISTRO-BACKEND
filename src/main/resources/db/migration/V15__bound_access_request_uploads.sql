CREATE TABLE access_request_submission_limits (
    id VARCHAR(16) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    submission_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_access_request_submission_count_nonnegative CHECK (submission_count >= 0)
);

INSERT INTO access_request_submission_limits (id, window_started_at, submission_count)
VALUES ('GLOBAL', CURRENT_TIMESTAMP, 0);
