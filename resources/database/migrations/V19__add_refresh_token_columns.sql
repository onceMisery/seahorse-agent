-- Add refresh-token persistence for rotating auth sessions.

ALTER TABLE t_user
    ADD COLUMN IF NOT EXISTS refresh_token VARCHAR(255);

ALTER TABLE t_user
    ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_user_refresh_token
    ON t_user(refresh_token);
