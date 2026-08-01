ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_salt TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_iterations INTEGER;

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_complete_password_credential;
ALTER TABLE users ADD CONSTRAINT ck_users_complete_password_credential CHECK (
    (password_hash IS NULL AND password_salt IS NULL AND password_iterations IS NULL)
    OR
    (password_hash IS NOT NULL AND password_salt IS NOT NULL AND password_iterations > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_authenticated_users_email
    ON users (LOWER(email))
    WHERE password_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at ON auth_sessions (expires_at);
