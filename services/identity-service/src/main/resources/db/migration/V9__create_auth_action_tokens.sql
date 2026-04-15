-- Универсальная таблица одноразовых действий: PASSWORD_RESET, EMAIL_CONFIRM, PHONE_CONFIRM.
-- Единая модель позволяет добавлять новые типы без переделки схемы.
CREATE TABLE auth_action_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(30) NOT NULL,
    token_hash   VARCHAR(255) NOT NULL,
    target       VARCHAR(255),
    expires_at   TIMESTAMP NOT NULL,
    used_at      TIMESTAMP,
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    last_sent_at TIMESTAMP
);

CREATE INDEX idx_auth_action_tokens_user_type ON auth_action_tokens(user_id, type);
CREATE INDEX idx_auth_action_tokens_token_hash ON auth_action_tokens(token_hash);
