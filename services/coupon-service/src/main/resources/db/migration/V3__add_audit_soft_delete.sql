-- Audit fields + soft delete for coupon-service tables

ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS updated_by BIGINT;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE categories ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();

-- Partial indexes for non-deleted records
CREATE INDEX IF NOT EXISTS idx_coupon_offers_not_deleted ON coupon_offers(status, category_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_merchants_not_deleted ON merchants(id) WHERE deleted = FALSE;
