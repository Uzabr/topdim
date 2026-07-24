CREATE UNIQUE INDEX IF NOT EXISTS uq_complaints_pending_coupon
    ON complaints (purchased_coupon_id)
    WHERE purchased_coupon_id IS NOT NULL
      AND status = 'PENDING';
