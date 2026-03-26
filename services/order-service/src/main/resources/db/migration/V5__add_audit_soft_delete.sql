-- Audit fields + soft delete for order-service tables

-- Orders: add audit + soft delete
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE orders ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Purchased coupons: add audit
ALTER TABLE purchased_coupons ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE purchased_coupons ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE purchased_coupons ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Partial index for non-deleted records (most queries filter deleted=false)
CREATE INDEX IF NOT EXISTS idx_orders_not_deleted ON orders(user_id, status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_purchased_coupons_not_deleted ON purchased_coupons(user_id, status) WHERE deleted = FALSE;
