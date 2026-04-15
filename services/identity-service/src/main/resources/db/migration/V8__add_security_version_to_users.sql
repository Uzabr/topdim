-- Добавляет версию безопасности для мгновенной инвалидации access tokens.
-- При change-password, block, change-role securityVersion увеличивается на 1.
-- Gateway проверяет securityVersion из JWT vs текущее значение в Redis.
ALTER TABLE users ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
