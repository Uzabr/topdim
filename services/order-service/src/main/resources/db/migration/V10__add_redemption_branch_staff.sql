-- V10: Add branch and staff identity tracking to redemptions
ALTER TABLE redemptions ADD COLUMN merchant_location_id BIGINT;
ALTER TABLE redemptions ADD COLUMN staff_id BIGINT;
ALTER TABLE redemptions ADD COLUMN redeem_method VARCHAR(10);

CREATE INDEX idx_redemptions_merchant_location ON redemptions(merchant_location_id);
CREATE INDEX idx_redemptions_staff_id ON redemptions(staff_id);
