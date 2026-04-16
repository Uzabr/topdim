ALTER TABLE coupon_offers 
ADD COLUMN redeemed_count INT NOT NULL DEFAULT 0,
ADD COLUMN total_turnover DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

-- Update enum ENUM values if the DB supports it, or simply rely on string representation if it's VARCHAR.
-- PostgreSQL enum update (if used), otherwise standard VARCHAR mapping doesn't require schema change:
-- ALTER TYPE coupon_status ADD VALUE IF NOT EXISTS 'SOLD_OUT';
