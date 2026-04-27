-- V11: Add partner cabinet fields to staff table
-- Adds loginUserId for independent cashier login,
-- merchantId and merchantLocationId for branch binding

ALTER TABLE staff ADD COLUMN login_user_id BIGINT;
ALTER TABLE staff ADD COLUMN merchant_id BIGINT;
ALTER TABLE staff ADD COLUMN merchant_location_id BIGINT;

-- Index for quick lookup by login user id (cashier auth flow)
CREATE INDEX idx_staff_login_user_id ON staff(login_user_id) WHERE login_user_id IS NOT NULL;

-- Index for finding active staff by owner
CREATE INDEX idx_staff_user_id_active ON staff(user_id, active);
