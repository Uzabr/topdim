-- Composite indexes for high-traffic queries (5M+ users optimization)

-- Orders: frequently queried by user + status combo
CREATE INDEX IF NOT EXISTS idx_orders_user_status ON orders(user_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders(user_id, created_at DESC);

-- Cart items: always loaded with cart
CREATE INDEX IF NOT EXISTS idx_cart_items_cart ON cart_items(cart_id);

-- Purchased coupons: filtered by user + status constantly
CREATE INDEX IF NOT EXISTS idx_purchased_coupons_user_status ON purchased_coupons(user_id, status);
CREATE INDEX IF NOT EXISTS idx_purchased_coupons_order ON purchased_coupons(order_id);
CREATE INDEX IF NOT EXISTS idx_purchased_coupons_expires ON purchased_coupons(expires_at) WHERE status = 'ACTIVE';

-- Refund requests: admin panel queries
CREATE INDEX IF NOT EXISTS idx_refund_requests_status_created ON refund_requests(status, created_at DESC);
