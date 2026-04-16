-- Добавляем привязку купона к модератору
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS assigned_moderator_id BIGINT;
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS assigned_moderator_name VARCHAR(255);
