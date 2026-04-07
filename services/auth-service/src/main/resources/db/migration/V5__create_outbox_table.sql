-- Outbox table for durable event publishing (Transactional Outbox Pattern).
-- Events are created in the same transaction as the business entity,
-- then published to RabbitMQ by a scheduled publisher.
CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    event_version   INT NOT NULL DEFAULT 1,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    BIGINT NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP,
    last_error      TEXT,
    correlation_id  VARCHAR(36)
);

CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
