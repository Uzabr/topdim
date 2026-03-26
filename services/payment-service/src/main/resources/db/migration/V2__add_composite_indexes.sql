-- Composite indexes for payment queries

-- Payments: lookup by order + status
CREATE INDEX IF NOT EXISTS idx_payments_order_status ON payments(order_id, status);
CREATE INDEX IF NOT EXISTS idx_payments_user_status ON payments(user_id, status);
CREATE INDEX IF NOT EXISTS idx_payments_provider_status ON payments(provider, status);
CREATE INDEX IF NOT EXISTS idx_payments_created ON payments(created_at DESC);
