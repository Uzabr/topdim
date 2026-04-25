ALTER TABLE order_items
    ADD COLUMN merchant_id BIGINT,
    ADD COLUMN expires_at TIMESTAMP;

ALTER TABLE purchased_coupons
    ADD COLUMN merchant_id BIGINT;

CREATE INDEX idx_order_items_merchant ON order_items(merchant_id);
CREATE INDEX idx_purchased_coupons_merchant ON purchased_coupons(merchant_id);
CREATE INDEX idx_purchased_coupons_expires_at ON purchased_coupons(expires_at);
