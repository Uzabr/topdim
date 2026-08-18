ALTER TABLE notifications
    ADD COLUMN event_key VARCHAR(255),
    ADD COLUMN deep_link VARCHAR(500);

CREATE UNIQUE INDEX uk_notifications_event_user
    ON notifications(event_key, user_id)
    WHERE event_key IS NOT NULL;

CREATE TABLE notification_deliveries (
    id              BIGSERIAL PRIMARY KEY,
    event_key       VARCHAR(255) NOT NULL,
    user_id         BIGINT       NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    sent_at         TIMESTAMP,
    last_error      VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_delivery_event_user_channel
        UNIQUE (event_key, user_id, channel),
    CONSTRAINT chk_notification_delivery_channel
        CHECK (channel IN ('TELEGRAM', 'EMAIL')),
    CONSTRAINT chk_notification_delivery_status
        CHECK (status IN ('PENDING', 'SENT', 'RETRY', 'FAILED')),
    CONSTRAINT chk_notification_delivery_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_delivery_retry
    ON notification_deliveries(status, next_attempt_at, id);
