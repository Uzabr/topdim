CREATE TABLE notification_outbox (
    id                BIGSERIAL PRIMARY KEY,
    event_key         VARCHAR(255) NOT NULL UNIQUE,
    recipient_user_id BIGINT       NOT NULL,
    payload           TEXT         NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempt_count     INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at      TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notification_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT chk_notification_outbox_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox(status, next_attempt_at, id);
