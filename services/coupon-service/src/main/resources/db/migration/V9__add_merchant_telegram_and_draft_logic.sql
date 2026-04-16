-- Добавляем telegram_chat_id для интеграции с Telegram-ботом
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS telegram_chat_id VARCHAR(64);

-- Добавляем поле для комментария партнёра при запросе правок
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS revision_comment TEXT;

-- Мерчант теперь обязателен для каждого купона
-- Сначала убеждаемся, что нет NULL значений (ставим заглушку, если есть)
UPDATE coupon_offers SET merchant_id = (SELECT id FROM merchants LIMIT 1)
    WHERE merchant_id IS NULL AND EXISTS (SELECT 1 FROM merchants);
DELETE FROM coupon_offers WHERE merchant_id IS NULL;

ALTER TABLE coupon_offers ALTER COLUMN merchant_id SET NOT NULL;
