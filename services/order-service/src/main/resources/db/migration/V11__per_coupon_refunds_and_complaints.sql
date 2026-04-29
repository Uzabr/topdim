-- Per-coupon refunds: extend refund_requests and complaints
ALTER TABLE refund_requests
    ADD COLUMN IF NOT EXISTS purchased_coupon_id BIGINT REFERENCES purchased_coupons(id),
    ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(12, 2),
    ADD COLUMN IF NOT EXISTS expected_refund_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_refund_requests_purchased_coupon
    ON refund_requests(purchased_coupon_id);

CREATE INDEX IF NOT EXISTS idx_refund_requests_status_created
    ON refund_requests(status, created_at DESC);

ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS purchased_coupon_id BIGINT REFERENCES purchased_coupons(id);

CREATE INDEX IF NOT EXISTS idx_complaints_purchased_coupon
    ON complaints(purchased_coupon_id);
