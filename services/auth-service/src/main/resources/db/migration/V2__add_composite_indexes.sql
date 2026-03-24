-- Composite indexes for auth queries

-- Users: login queries
CREATE INDEX IF NOT EXISTS idx_users_email_enabled ON users(email, enabled);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone) WHERE phone IS NOT NULL;

-- Refresh tokens: cleanup queries
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked ON refresh_tokens(user_id, revoked);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked = FALSE;
