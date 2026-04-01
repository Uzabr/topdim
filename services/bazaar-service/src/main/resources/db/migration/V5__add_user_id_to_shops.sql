-- Add user ownership to shops table
ALTER TABLE shops ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_shops_user_id ON shops(user_id);

