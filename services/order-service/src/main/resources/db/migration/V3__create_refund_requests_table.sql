-- Refund requests table: user-initiated refund requests processed by admin
CREATE TABLE IF NOT EXISTS refund_requests (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    user_id         BIGINT NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_comment   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP
);

CREATE INDEX idx_refund_requests_user ON refund_requests(user_id);
CREATE INDEX idx_refund_requests_order ON refund_requests(order_id);
CREATE INDEX idx_refund_requests_status ON refund_requests(status);
