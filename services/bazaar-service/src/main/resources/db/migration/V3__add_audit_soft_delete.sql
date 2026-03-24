-- Audit fields + soft delete for bazaar-service tables

ALTER TABLE bazaars ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE bazaars ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE shops ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Partial indexes
CREATE INDEX IF NOT EXISTS idx_bazaars_not_deleted ON bazaars(city, active) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_shops_not_deleted ON shops(bazaar_id, active) WHERE deleted = FALSE;
