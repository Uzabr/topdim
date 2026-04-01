-- Снятие обязательности merchant_id у купонов
ALTER TABLE coupon_offers ALTER COLUMN merchant_id DROP NOT NULL;
