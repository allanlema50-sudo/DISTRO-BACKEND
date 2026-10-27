-- V15 creates this coordination table and seeds GLOBAL. This idempotent repair
-- also supports reviewed legacy databases where Hibernate created the table but
-- did not execute the seed INSERT.
INSERT INTO access_request_submission_limits (id, window_started_at, submission_count)
VALUES ('GLOBAL', CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO NOTHING;
