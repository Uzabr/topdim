-- V16: Drop legacy columns from coupon_offers and merchants
-- All data has been migrated to canonical fields (V13-V15):
--   coupon_offers.offer_description replaces short_description, full_description, terms, usage_rules, how_to_use
--   merchant_locations replaces coupon_offers.address, coupon_offers.contact_phone, coupon_offers.working_hours
--   merchant_locations replaces merchants.address, merchants.phone, merchants.working_hours

-- == coupon_offers: drop legacy text columns ==
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS short_description;
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS full_description;
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS terms;
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS usage_rules;
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS how_to_use;

-- == coupon_offers: drop legacy contact columns ==
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS address;
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS contact_phone;
ALTER TABLE coupon_offers DROP COLUMN IF EXISTS working_hours;

-- == merchants: drop legacy contact columns ==
ALTER TABLE merchants DROP COLUMN IF EXISTS address;
ALTER TABLE merchants DROP COLUMN IF EXISTS phone;
ALTER TABLE merchants DROP COLUMN IF EXISTS working_hours;
