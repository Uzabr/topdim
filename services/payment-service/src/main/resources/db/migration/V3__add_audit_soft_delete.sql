-- Audit fields for payment-service

ALTER TABLE payments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE payments ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_payments_not_deleted ON payments(order_id, status) WHERE deleted = FALSE;
