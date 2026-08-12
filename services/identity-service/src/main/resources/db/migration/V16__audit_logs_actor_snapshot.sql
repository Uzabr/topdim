-- Snapshot of the actor at write-time so historical audit rows stay readable
-- even if the user later changes email/role. Logs are append-only (no deletes).
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS user_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS user_role VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_name ON audit_logs (entity_name);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs (user_id);
