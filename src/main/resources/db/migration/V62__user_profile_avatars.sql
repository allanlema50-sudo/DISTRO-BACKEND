ALTER TABLE users
    ADD COLUMN avatar_available BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_avatars (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(100) NOT NULL,
    content BYTEA NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);
