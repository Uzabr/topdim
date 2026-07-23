ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;

-- Previously stored plaintext tokens cannot be migrated to hashes without keeping
-- the stolen session secret valid. Revoke them explicitly during the rollout.
UPDATE refresh_tokens SET revoked = TRUE WHERE revoked = FALSE;

ALTER INDEX IF EXISTS idx_refresh_tokens_token RENAME TO idx_refresh_tokens_token_hash;
