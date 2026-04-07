-- Table for idempotent event processing (deduplication).
-- Prevents duplicate profile creation from duplicate UserRegistered events.
CREATE TABLE IF NOT EXISTS processed_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(36) NOT NULL UNIQUE,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_events_event_id ON processed_events(event_id);
