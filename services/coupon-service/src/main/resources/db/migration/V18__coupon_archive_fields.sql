-- V15: terminal archive metadata for coupon offers.
-- ARCHIVED stops future sales but does not mutate purchased coupons.

ALTER TABLE coupon_offers
    ADD COLUMN IF NOT EXISTS archive_reason TEXT,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
