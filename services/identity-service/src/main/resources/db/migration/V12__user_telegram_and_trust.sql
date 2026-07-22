-- Telegram-привязка аккаунта + уровень доверия (L0/L1) для Telegram-авторизации.
-- telegram_chat_id — Telegram user id, ключ привязки/логина (UNIQUE, допускает NULL для не-Telegram аккаунтов).
-- trust_level — прогрессивное доверие: L0 (базовый) / L1 (верифицирован телефоном OTP или оплатой).
ALTER TABLE users
    ADD COLUMN telegram_chat_id   BIGINT,
    ADD COLUMN telegram_username  VARCHAR(100),
    ADD COLUMN telegram_linked_at TIMESTAMP,
    ADD COLUMN trust_level        VARCHAR(8) NOT NULL DEFAULT 'L0';

-- UNIQUE в Postgres допускает несколько NULL → существующие пользователи не конфликтуют.
ALTER TABLE users ADD CONSTRAINT uq_users_telegram_chat_id UNIQUE (telegram_chat_id);
CREATE INDEX idx_users_telegram_chat_id ON users (telegram_chat_id);
