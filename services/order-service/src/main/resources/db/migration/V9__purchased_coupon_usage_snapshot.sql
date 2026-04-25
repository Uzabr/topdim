-- V9: snapshot merchant usage context for purchased coupon profile UX.
-- Values are copied at checkout/generation time and do not depend on public coupon visibility later.

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS merchant_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS merchant_address TEXT,
    ADD COLUMN IF NOT EXISTS merchant_phone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS merchant_working_hours VARCHAR(255);

ALTER TABLE purchased_coupons
    ADD COLUMN IF NOT EXISTS merchant_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS merchant_address TEXT,
    ADD COLUMN IF NOT EXISTS merchant_phone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS merchant_working_hours VARCHAR(255);
