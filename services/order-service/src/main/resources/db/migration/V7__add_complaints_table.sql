-- Complaints table: user-initiated complaints about an order
CREATE TABLE IF NOT EXISTS complaints (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    subject VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    resolution TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_complaints_user_id ON complaints(user_id);
CREATE INDEX idx_complaints_order_id ON complaints(order_id);
CREATE INDEX idx_complaints_status ON complaints(status);

