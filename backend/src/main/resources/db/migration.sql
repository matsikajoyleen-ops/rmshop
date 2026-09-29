-- Runs on every startup, so every statement must be safe to repeat.

-- Managers reset a forgotten access code through this address.
-- Stored lowercase. NULLs are allowed (cashiers do not need one).
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(254);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_key ON users (email);

-- One row per emailed confirmation code. Only the SHA-256 hash of the code
-- is stored, never the code itself.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    used_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS password_reset_tokens_user_idx ON password_reset_tokens (user_id);
