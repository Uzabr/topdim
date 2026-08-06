CREATE UNIQUE INDEX IF NOT EXISTS uq_merchants_user_id
    ON merchants(user_id)
    WHERE user_id IS NOT NULL;
